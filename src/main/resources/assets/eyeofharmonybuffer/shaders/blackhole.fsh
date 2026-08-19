#version 120

// 黑洞天空逐像素 ray-march。
// 黑洞渲染算法参考 IterationT（Kary / Tahnass）的末地天空实现（EndSky.glsl），
// 按算法规格独立重写：物理模型与参数一致，代码为 1.7.10 GLSL 120 独立实现。

uniform float uTime;
uniform float uTanHalfFov;
uniform float uAspect;
uniform mat3  uWorldToBhLocal;   // 相机空间视线方向 → 黑洞局部系
uniform vec3  uLightDir;         // 盘面朝向参考方向（潮汐锁定，固定）
uniform sampler2D uNoise;        // 64x64 程序噪声（纹理单元 1）

// ============ 画面亮度（无 bloom 环境下的 HDR 补偿，可按需调节）============
// ITT 靠 HDR 数值 + bloom 扩散 + gamma 链让盘面整体明亮；
// 1.7.10 无后处理，这里用数值放大 + 电影 tone map 等效补偿。
/** 盘面辉光增益：中段/外缘的 glowStrength 原始值只有 0.01 级，放大到可见范围。 */
const float DISC_BRIGHTNESS = 40.0;
/** 外缘弥散光强度（原 0.16 太弱）。 */
const float OUTER_GLOW = 0.7;
/** 输出曝光（filmic tone map 系数）。 */
const float EXPOSURE = 2.2;
/** 恒星亮度（原 maxLum 0.04 在无 bloom 环境下几乎不可见）。 */
const float STAR_LUM = 0.32;

varying vec2 vNdc;

// ============ 工具 ============
float sat01(float x){ return clamp(x, 0.0, 1.0); }
float smoothc(float x){ return x * x * (3.0 - 2.0 * x); }
float remapSat(float x, float e0, float e1){ return sat01((x - e0) / (e1 - e0)); }
float pcurve(float x){ float x2 = x * x; return 12.207 * x2 * x2 * (1.0 - x); }

// ============ 黑体色温（Planckian locus spline，IterationT 同算法独立实现）============
vec3 blackbody(float temperature){
    float rt = 1.0 / temperature;
    float rt2 = rt * rt;
    vec4 coeffX = vec4(rt2 * rt, rt2, rt, 1.0);
    float x;
    if (temperature < 4000.0){
        x = dot(coeffX, vec4(-0.2661293e9, -0.2343589e6, 0.8776956e3, 0.179910));
    } else {
        x = dot(coeffX, vec4(-3.0258469e9, 2.1070479e6, 0.2226347e3, 0.240390));
    }
    float x2 = x * x;
    vec4 coeffY = vec4(x2 * x, x2, x, 1.0);
    float z;
    if (temperature < 2222.0){
        z = 1.0 / dot(coeffY, vec4(-1.1063814, -1.34811020, 2.18555832, -0.20219683));
    } else if (temperature < 4000.0){
        z = 1.0 / dot(coeffY, vec4(-0.9549476, -1.37418593, 2.09137015, -0.16748867));
    } else {
        z = 1.0 / dot(coeffY, vec4(3.0817580, -5.87338670, 3.75112997, -0.37001483));
    }
    vec3 xyz = vec3(x * z, 1.0, z);
    xyz.z -= xyz.x + 1.0;
    vec3 c = vec3(3.24097 * xyz.x - 1.53738 * xyz.y - 0.49861 * xyz.z,
                  -0.96924 * xyz.x + 1.87597 * xyz.y + 0.04156 * xyz.z,
                   0.05563 * xyz.x - 0.20398 * xyz.y + 1.05697 * xyz.z);
    return max(c, vec3(0.0));
}

// ============ 程序噪声 FBM（3D 折叠到 2D，64 周期无缝）============
float cloudNoise(vec3 pos){
    vec2 uv = 17.0 * pos.z + pos.xy;
    return texture2D(uNoise, (uv + 0.5) / 64.0).r;
}

float fbmCloud(vec3 pos, vec3 shift){
    const int octaves = 4;
    const float alpha = 0.87;
    const float scale = 2.5;
    const float octShift = (alpha / scale) / float(octaves);
    float acc = 0.0;
    float amp = 0.5;
    for (int i = 0; i < octaves; i++){
        acc += amp * cloudNoise(pos);
        pos = (pos + shift) * scale;
        amp *= alpha;
    }
    return acc + octShift;
}

// ============ 空间弯曲（引力透镜）============
void warpSpace(inout vec3 dir, inout vec3 pos){
    float d = length(pos);
    float warp = 1.0 / (d * d + 0.000001);
    vec3 sv = normalize(-pos);
    dir = normalize(dir + sv * warp * 0.06);
}

// ============ 旋转矩阵（盘朝向）============
mat3 rotMatrix(float x, float y, float z){
    mat3 matx = mat3(1.0, 0.0, 0.0,  0.0, cos(x), sin(x),  0.0, -sin(x), cos(x));
    mat3 maty = mat3(cos(y), 0.0, -sin(y),  0.0, 1.0, 0.0,  sin(y), 0.0, cos(y));
    mat3 matz = mat3(cos(z), sin(z), 0.0,  -sin(z), cos(z), 0.0,  0.0, 0.0, 1.0);
    return maty * matx * matz;
}

// ============ 程序化恒星 ============
vec3 calcStars(vec3 worldDir){
    float angleY = uTime * 0.001;
    mat3 rotY = mat3(cos(angleY), 0.0, -sin(angleY),  0.0, 1.0, 0.0,  sin(angleY), 0.0, cos(angleY));
    worldDir = rotY * worldDir;

    const float scale = 384.0;
    const float coverage = 0.007;
    const float maxLum = STAR_LUM;
    const float minT = 4000.0;
    const float maxT = 8000.0;

    vec3 p = worldDir * scale;
    vec3 i = floor(p);
    vec3 f = p - i;
    float r = dot(f - 0.5, f - 0.5);

    vec3 i3 = fract(i * vec3(443.897, 441.423, 437.195));
    i3 += dot(i3, i3.yzx + 19.19);
    vec2 hash = fract((i3.xx + i3.yz) * i3.zy);
    hash.y = 2.0 * hash.y - 4.0 * hash.y * hash.y + 3.0 * hash.y * hash.y * hash.y;

    float c = remapSat(hash.x, 1.0 - coverage, 1.0);
    return (maxLum * remapSat(r, 0.25, 0.0) * c * c) * blackbody(mix(minT, maxT, hash.y));
}

// ============ 主 ============
void main(){
    // 从 NDC 重建相机空间视线方向
    vec3 dirView = normalize(vec3(vNdc.x * uTanHalfFov * uAspect, vNdc.y * uTanHalfFov, -1.0));
    vec3 rayDir = normalize(uWorldToBhLocal * dirView);
    vec3 lightDir = normalize(uLightDir);

    // 黑洞系统放大：盘参数 ×1.5，但虚拟相机距离拉近（6.0，原 8.0）——
    // 张角 = atan(盘半径/相机距离)，只放大盘不拉远相机才能真正变大
    vec3 eye = -lightDir * 6.0;
    vec3 rayPos = eye + rayDir * 3.0;

    // 盘倾斜（绕 X 5.7°、绕 Z -20°）
    mat3 rot = rotMatrix(0.1, 0.0, -0.35);

    const float steps = 50.0;
    const float rSteps = 1.0 / steps;
    const float stepLength = 0.2;
    const float discRadius = 3.375;
    const float discWidth = 5.25;
    const float discInner = discRadius - discWidth * 0.5;
    const float discOuter = discRadius + discWidth * 0.5;

    // 时间抖动（InterleavedGradientNoise）
    float noise = fract(52.9829189 * fract(0.06711056 * gl_FragCoord.x + 0.00583715 * gl_FragCoord.y));

    vec3 result = vec3(0.0);
    float transmittance = 1.0;

    rayPos += rayDir * stepLength * noise;

    for (int i = 0; i < 50; i++){
        if (transmittance < 0.0001) break;
        warpSpace(rayDir, rayPos);
        rayPos += rayDir * stepLength;
        {
            vec3 dp = rot * rayPos;
            float r = length(dp);
            // ITT: atan2(-discPos.zx) = atan2(y=-z, x=-x)
            float p = atan(-dp.z, -dp.x);
            float h = dp.y;

            float radialGradient = 1.0 - sat01((r - discInner) / discWidth * 0.5);
            float dist = abs(h);
            float discThickness = 0.15 * radialGradient;

            float fr = abs(r - discInner) + 0.4;
            fr = fr * fr;
            float fade = fr * fr * 0.04;
            float bloomFactor = 1.0 / (h * h * 40.0 + fade + 0.00002);
            bloomFactor *= sat01(2.0 - abs(dist) / discThickness);
            bloomFactor = bloomFactor * bloomFactor;

            float dr = pcurve(radialGradient);
            float density = dr;
            density *= sat01(1.0 - abs(dist) / discThickness);
            density = sat01(density * 0.7);
            density = sat01(density + bloomFactor * 0.1);

            if (density > 0.0001){
                vec3 discCoord = vec3(r, p * (1.0 - radialGradient * 0.5), h * 0.1) * 5.25;
                float f = fbmCloud(discCoord, uTime * vec3(0.1, 0.07, 0.0));
                f = f * f;
                density *= f * dr;

                float gr = 1.0 - radialGradient;
                gr = gr * gr;
                float glowStrength = 1.0 / (gr * gr * 400.0 + 0.002);
                vec3 glow = blackbody(2700.0 + glowStrength * 50.0) * glowStrength * DISC_BRIGHTNESS;
                glow *= sin(p - 1.07) * 0.75 + 1.0;

                float stepTrans = exp2(-density * 7.0);
                float integral = 1.0 - stepTrans;
                transmittance *= stepTrans;

                result += integral * transmittance * glow;
            }

            // 内缘白热环（torus bloom，随系统放大 1.5 倍）
            vec2 t = vec2(1.5, 0.015);
            float torusDist = length(length(dp + vec3(0.0, -0.075, 0.0)) - t);
            float bloomDisc = 1.0 / (pow(torusDist, 3.5) + 0.001);
            vec3 col = blackbody(12000.0);
            bloomDisc *= step(0.75, r);
            result += col * bloomDisc * 0.1 * transmittance;

            // 外缘弥散光（卡冈图雅风格补充：r>2 起的外围盘面保持明亮橙黄，
            // 原本靠 HDR/bloom 实现的辉光在无后处理环境下的替代）。
            // 必须乘以 radialGradient 限制在盘面范围内（r>7.5 即 radial=0 后归零），
            // 否则 march 在盘面方向上会一路累加 → 天空中出现巨大环形光带。
            float outerFade = sat01((r - 3.0) / 8.25);
            outerFade *= sat01(radialGradient * 2.0);
            outerFade *= sat01(1.0 - abs(h) / (0.05 + 0.05 * radialGradient));
            result += blackbody(2800.0) * outerFade * OUTER_GLOW * transmittance;
        }
    }
    result *= rSteps;

    // 恒星画在弯曲后的视线方向上 → 视界周围形成爱因斯坦环
    vec3 color = calcStars(rayDir);
    color *= transmittance;
    color += result;

    // 电影 tone map（暗部提亮、高光柔化），模拟 ITT 的 HDR 曝光 + gamma 链
    color = 1.0 - exp(-color * EXPOSURE);

    gl_FragColor = vec4(color, 1.0);
}
