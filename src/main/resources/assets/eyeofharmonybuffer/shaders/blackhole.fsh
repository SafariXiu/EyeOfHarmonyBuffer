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

varying vec2 vNdc;

// ============ 工具 ============
float sat01(float x){ return clamp(x, 0.0, 1.0); }
float smoothc(float x){ return x * x * (3.0 - 2.0 * x); }
float remapSat(float x, float e0, float e1){ return sat01((x - e0) / (e1 - e0)); }
float pcurve(float x){ float x2 = x * x; return 12.207 * x2 * x2 * (1.0 - x); }

// ============ 黑体色温近似（Planckian locus 简化分段）============
vec3 blackbody(float temp){
    float t = clamp(temp, 1000.0, 12000.0);
    vec3 c;
    if (t < 2000.0){
        float k = (t - 1000.0) / 1000.0;
        c = vec3(0.35 + 0.60 * k, 0.04 + 0.26 * k, 0.01 + 0.09 * k);
    } else if (t < 3500.0){
        float k = (t - 2000.0) / 1500.0;
        c = vec3(0.95 + 0.05 * k, 0.30 + 0.30 * k, 0.10 + 0.15 * k);
    } else if (t < 6000.0){
        float k = (t - 3500.0) / 2500.0;
        c = vec3(1.0, 0.60 + 0.35 * k, 0.25 + 0.60 * k);
    } else {
        float k = (t - 6000.0) / 6000.0;
        c = vec3(1.0 - 0.06 * k, 0.95 - 0.04 * k, 0.85 + 0.15 * k);
    }
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
    const float maxLum = 0.04;
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

    // 虚拟相机：黑洞背向光一侧 8 单位
    vec3 eye = -lightDir * 8.0;
    vec3 rayPos = eye + rayDir * 3.0;

    // 盘倾斜（绕 X 5.7°、绕 Z -20°）
    mat3 rot = rotMatrix(0.1, 0.0, -0.35);

    const float steps = 50.0;
    const float rSteps = 1.0 / steps;
    const float stepLength = 0.2;
    const float discRadius = 2.25;
    const float discWidth = 3.5;
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
            float p = atan(dp.z, dp.x);
            float h = dp.y;

            float radialGradient = 1.0 - sat01((r - discInner) / discWidth * 0.5);
            float dist = abs(h);
            float discThickness = 0.1 * radialGradient;

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
                vec3 discCoord = vec3(r, p * (1.0 - radialGradient * 0.5), h * 0.1) * 3.5;
                float f = fbmCloud(discCoord, uTime * vec3(0.1, 0.07, 0.0));
                f = f * f;
                f = f * f;
                density *= f * dr;

                float gr = 1.0 - radialGradient;
                gr = gr * gr;
                float glowStrength = 1.0 / (gr * gr * 400.0 + 0.002);
                vec3 glow = blackbody(2700.0 + glowStrength * 50.0) * glowStrength;
                glow *= sin(p - 1.07) * 0.75 + 1.0;

                float stepTrans = exp2(-density * 7.0);
                float integral = 1.0 - stepTrans;
                transmittance *= stepTrans;

                result += integral * transmittance * glow;
            }

            // 内缘白热环（torus bloom）
            vec2 t = vec2(1.0, 0.01);
            float torusDist = length(length(dp + vec3(0.0, -0.05, 0.0)) - t);
            float bloomDisc = 1.0 / (pow(torusDist, 3.5) + 0.001);
            vec3 col = blackbody(12000.0);
            bloomDisc *= step(0.5, r);
            result += col * bloomDisc * 0.1 * transmittance;
        }
    }
    result *= rSteps;

    // 恒星画在弯曲后的视线方向上 → 视界周围形成爱因斯坦环
    vec3 color = calcStars(rayDir);
    color *= transmittance;
    color += result;

    gl_FragColor = vec4(color, 1.0);
}
