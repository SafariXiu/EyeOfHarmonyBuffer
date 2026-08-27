#version 330 core
// 维度转场天空撕裂 v2（逐行翻译自 Nostalgia 的 portal_sky_rip_v2.fsh，GUN2RAS，CC0-1.0，见 LICENSE-nostalgia.txt）
// 天空平面上的裂缝/碎片/冲击波：24 层裂缝墙 + 10 层飞散碎片 + 鼓包 + 崩溃闪光。
// 适配（已规避 EOHB 转场的全部 GLSL 坑）：
// - GLSL 330 core 原生语法（无 texture2D/varying/gl_FragColor，防 Angelica CompatShaderTransformer ANTLR 解析）；
// - 无 inverse()：InverseViewProj -> inverse(投影) + transpose(纯旋转 ModelView) + 相机位置（两段式）；
// - 构造器带齐分量（vec4(uv, depth, 1.0) 等），避免 C1067 "too little data in type constructor"；
// - 管线无混合：early-out 透明处直接输出 scene 透传，最终 fragColor 与 scene mix 合成（blend 语义等价）。

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D RiftSampler;     // rift_data.png：r=裂缝边界距离，gb=碎片偏移，a=完整标记
uniform sampler2D PortalSampler;   // 末地传送门贴图（textures/entity/end_portal.png）
uniform sampler2D SkySampler;      // 末地天空贴图（textures/environment/end_sky.png）
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;
uniform vec4 BeaconAndTimer;       // xyz = 撕裂中心（世界），w = 撕裂时间（秒）
uniform float CrackPlaneY;         // 裂缝平面世界高度
uniform float uSkyRipActive;       // 1.0 渲染撕裂，0.0 透传

in vec2 texCoord;
out vec4 fragColor;

const float shatterTime       = 2.5;
const float BULGE_DURATION    = 1.8;
const float BULGE_MAX         = 4.0;
const float BULGE_RADIUS      = 180.0;
const float textureScale      = 900.0;
const float waveExpansionTime = 0.8;
const float maxShatterRadius  = 450.0;
const float fadeStartRadius   = 150.0;
const float SHATTER_MAX_RADIUS = 75.0;
const float CRACK_SPREAD_RADIUS = 450.0;

float bulgeProfile(float r) {
    float rn = clamp(r / BULGE_RADIUS, 0.0, 1.0);
    float c = cos(rn * 1.5707963);
    return c * c;
}

float bulgeAmp(float t) {
    float bs = shatterTime - BULGE_DURATION;
    if (t < bs) return 0.0;
    float tt = clamp((t - bs) / BULGE_DURATION, 0.0, 1.0);
    return tt * tt * (3.0 - 2.0 * tt);
}

/**
 * 传送门材质（超域侵蚀方块配色 + 末地传送门结构）：
 * - 结构复刻 1.7.10 原版 RenderEndPortal：层 0 = end_sky 微弱底（SRC_ALPHA），
 *   层 1~15 = end_portal 加法累加发光（GL_ONE/GL_ONE），每层独立旋转
 *   （angle = (i*i*4321 + i*9) * 2 度）+ 时间垂直滚动 + 缩放 f6（层1=0.5，其他=0.0625）；
 * - 颜色用超域侵蚀 RenderOverdomainEndStyle 的 layerR/G/B 算法：
 *   baseR=随机0.1~0.6、baseG=0~0.3、baseB=0.3~0.8；i==0→(1,0.8,0.8)；
 *   70% 层 ×0.3（暗）30% ×1.4（亮）；depthLerp 红+0.08、蓝-0.06；
 *   最终 R×1.4、G×0.4、B×0.4（紫红调）；brightnessFactor=0.25+layer*0.03（层0=0.15）。
 */
vec3 portalMaterial(vec2 lp, float t) {
    vec3 col = vec3(0.0);
    float scroll = fract(t * 0.1);

    // 层 0：end_sky 微弱底（brightness 0.15，缩放 0.125）
    {
        // 内容缩小 10%：采样系数 ×10（贴图重复更密，旋涡更小）
        vec2 uv = lp * 1.25 * 0.004 + vec2(0.5);
        uv.y += scroll;
        col += texture(SkySampler, uv).rgb * 0.15;
    }

    // 层 1~15：end_portal 加法累加（超域侵蚀配色）
    for (int i = 1; i < 16; i++) {
        float fi = float(i);
        float f6 = (fi < 1.5) ? 0.5 : 0.0625;

        // 旋转（末地传送门角度）+ 缩放 + 滚动；内容缩小 10%：0.02 → 0.2
        float rotRad = ((fi * fi * 4321.0 + fi * 9.0) * 2.0) * 0.0174533;
        vec2 sampled = lp * f6 * 0.2;
        vec2 c = sampled - vec2(0.5);
        sampled = vec2(c.x * cos(rotRad) - c.y * sin(rotRad),
                       c.x * sin(rotRad) + c.y * cos(rotRad)) + vec2(0.5);
        sampled.y += scroll * (0.1 + fi * 0.02) + fi * 0.003;

        vec3 layerCol = texture(PortalSampler, sampled).rgb;

        // 超域侵蚀层色：3 个独立随机（复刻三次 nextFloat）
        float h1 = fract(sin(dot(vec2(fi, fi * 31.1), vec2(12.9898, 78.233))) * 43758.5453);
        float h2 = fract(sin(dot(vec2(fi * 7.7, fi + 1.3), vec2(12.9898, 78.233))) * 43758.5453);
        float h3 = fract(sin(dot(vec2(fi * 3.1, fi + 5.7), vec2(12.9898, 78.233))) * 43758.5453);
        float baseR = h1 * 0.5 + 0.1;
        float baseG = h2 * 0.3;
        float baseB = h3 * 0.5 + 0.3;

        // 70% 暗层 / 30% 亮层
        float hd = fract(sin(dot(vec2(fi * 13.7, fi + 9.1), vec2(12.9898, 78.233))) * 43758.5453);
        if (hd < 0.7) {
            baseR *= 0.3; baseG *= 0.3; baseB *= 0.3;
        } else {
            baseR *= 1.4; baseG *= 1.4; baseB *= 1.4;
        }

        // 深度偏色：红增蓝减
        float depthLerp = fi / 15.0;
        baseR += 0.08 * depthLerp;
        baseB -= 0.06 * depthLerp;
        baseR = clamp(baseR, 0.0, 1.0);
        baseB = clamp(baseB, 0.0, 1.0);

        // 最终：R×1.4、G×0.4、B×0.4（紫红调）* brightnessFactor
        vec3 layerColor = vec3(baseR * 1.4, baseG * 0.4, baseB * 0.4);
        float brightness = min(0.25 + fi * 0.03, 1.0);
        layerColor *= brightness;

        // 加法发光累加（GL_ONE/GL_ONE）
        col += layerCol * layerColor;
    }

    return clamp(col, 0.0, 1.0);
}

/** 世界重建：inverse(投影)*NDC -> 视图空间 -> transpose(纯旋转 ModelView) -> + 相机位置（与 whiteout 一致）。 */
vec3 worldPosAtDepth(float depth) {
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, depth, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (transpose(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    if (uSkyRipActive <= 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    vec3 PortalPos = BeaconAndTimer.xyz;
    float TransitionTime = BeaconAndTimer.w;
    float crackPlaneY = CrackPlaneY;

    // 射线重建（源库用 InverseViewProj 一步，一前一后两点求方向；等价于两段式）
    vec3 rayPointA = worldPosAtDepth(0.4);
    vec3 rayPointB = worldPosAtDepth(0.6);
    vec3 rayDir = normalize(rayPointB - rayPointA);

    if (abs(rayDir.y) <= 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    float bAmp = bulgeAmp(TransitionTime);
    float t_plane = (crackPlaneY - rayPointA.y) / rayDir.y;
    if (t_plane <= 0.01) {
        fragColor = vec4(scene, 1.0);
        return;
    }
    vec3 hitPlanePos = rayPointA + rayDir * t_plane;
    for (int bi = 0; bi < 3; bi++) {
        vec2 lp = hitPlanePos.xz - PortalPos.xz;
        float by = BULGE_MAX * bAmp * bulgeProfile(length(lp));
        t_plane = (crackPlaneY + by - rayPointA.y) / rayDir.y;
        if (t_plane <= 0.01) {
            fragColor = vec4(scene, 1.0);
            return;
        }
        hitPlanePos = rayPointA + rayDir * t_plane;
    }
    vec2 localPos = hitPlanePos.xz - PortalPos.xz;

    vec2 texUV = localPos / textureScale + vec2(0.5);
    if (texUV.x < 0.0 || texUV.x > 1.0 || texUV.y < 0.0 || texUV.y > 1.0) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    vec4 riftData = texture(RiftSampler, texUV);

    vec2 shardOffset = riftData.gb * 200.0 - vec2(100.0);
    vec2 shardCenterLocal = localPos + shardOffset;
    float physicalCellDist = length(shardCenterLocal);

    vec2 p2 = vec2(dot(shardCenterLocal, vec2(127.1, 311.7)), dot(shardCenterLocal, vec2(269.5, 183.3)));
    float h = fract(sin(p2.x) * 43758.5453);

    float shatterDelay = (physicalCellDist / 75.0) * 0.8 + h * 0.35;
    float rawProgress = (TransitionTime - shatterTime - shatterDelay) / 0.65;
    float shardProgress = clamp(rawProgress, 0.0, 1.0);

    if (physicalCellDist > SHATTER_MAX_RADIUS) {
        shardProgress = 0.0;
        rawProgress = -1.0;
    }

    if (riftData.a > 0.5) {
        shardProgress = 0.0;
    }

    // 洞：破碎区块（riftData.a <= 0.5）且碎片已开始飞（rawProgress >= 0）→ 碎片飞走后
    // 留下的空区域。这里填充传送门材质（超域侵蚀风格），洞外保持原样。
    if (shardProgress >= 1.0 || (shardProgress > 0.0 && rawProgress >= 0.0)) {
        fragColor = vec4(mix(scene, portalMaterial(localPos, TransitionTime), 0.85), 1.0);
        return;
    }

    // 深度遮挡：地形挡住裂缝时不渲染（blockDist < crackDist-0.25 → 透明透传 scene）
    float depth = texture(DepthSampler, texCoord).r;
    vec3 pixelWorldPos = worldPosAtDepth(depth);
    float blockDist = length(pixelWorldPos - rayPointA);
    float crackDist = length(hitPlanePos - rayPointA);
    if (blockDist < crackDist - 0.25) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    float currentWaveTime = max(0.0, TransitionTime - shatterTime);
    float waveRadius = (currentWaveTime / waveExpansionTime) * maxShatterRadius;
    if (currentWaveTime <= 0.001) waveRadius = -1000.0;

    int layers = 24;
    float wallHeight = 12.0;

    float totalCrackAlpha = 0.0;
    vec3 totalCrackColor = vec3(0.0);

    for (int i = 0; i < layers; i++) {
        int layerIndex = (rayDir.y > 0.0) ? i : (layers - 1 - i);
        float hOffset = float(layerIndex) * (wallHeight / float(layers - 1));

        float layerBulgeY = BULGE_MAX * bAmp * bulgeProfile(length(localPos));
        float t_layer = (crackPlaneY + hOffset + layerBulgeY - rayPointA.y) / rayDir.y;
        if (t_layer <= 0.01) continue;
        vec2 layerLocalPos = (rayPointA + rayDir * t_layer).xz - PortalPos.xz;
        float layerRawDist = length(layerLocalPos);

        vec2 layerTexUV = layerLocalPos / textureScale + vec2(0.5);
        if (layerTexUV.x < 0.0 || layerTexUV.x > 1.0 || layerTexUV.y < 0.0 || layerTexUV.y > 1.0) continue;

        vec4 layerRiftData = texture(RiftSampler, layerTexUV);
        vec2 rotUV = vec2(layerTexUV.y, 1.0 - layerTexUV.x);
        vec4 layerRiftData2 = texture(RiftSampler, rotUV);

        float heightRatio = float(layerIndex) / float(layers);
        float alpha1 = pow(layerRiftData.r, 3.0);
        float alpha2 = pow(layerRiftData2.r, 3.0);
        float localAlpha = max(alpha1, alpha2);

        float radialNorm = clamp(layerRawDist / CRACK_SPREAD_RADIUS, 0.0, 1.0);
        float crackActivation = smoothstep(radialNorm, radialNorm + 0.08, bAmp);
        localAlpha *= crackActivation;

        if (localAlpha > 0.01) {
            float distToWaveFront = layerRawDist - waveRadius;
            float passedFactor = 1.0 - smoothstep(-25.0, 5.0, distToWaveFront);
            float pulse = exp(-pow(distToWaveFront / 25.0, 2.0));

            float globalFade = 1.0 - smoothstep(fadeStartRadius, maxShatterRadius, layerRawDist);

            vec3 baseColor = vec3(1.0);
            vec3 pulseColor = vec3(0.7, 0.95, 1.0);
            vec3 finalColor = mix(baseColor, pulseColor, min(pulse * 1.5, 1.0));

            float intensity = pow(1.0 - heightRatio, 1.5);
            if (layerIndex == 0) intensity = 1.0;

            float currentThicknessAlpha = mix(0.15, 1.15, passedFactor);
            float layerCrackAlpha = min(localAlpha * (currentThicknessAlpha + pulse * 2.8), 1.0) * globalFade * intensity;

            if (layerIndex > 0) layerCrackAlpha *= 0.25;

            totalCrackColor = totalCrackColor + finalColor * layerCrackAlpha * (1.0 - totalCrackAlpha);
            totalCrackAlpha = totalCrackAlpha + layerCrackAlpha * (1.0 - totalCrackAlpha);
        }

        if (totalCrackAlpha > 0.99) break;
    }

    for (int flyI = 0; flyI < 10; flyI++) {
        float flyHeight = float(flyI + 1) * 8.0;
        float flyPlaneY = crackPlaneY + flyHeight;
        float t_fly = (flyPlaneY - rayPointA.y) / rayDir.y;
        if (t_fly <= 0.01) continue;

        vec3 flyHitPos = rayPointA + rayDir * t_fly;
        vec2 flyLocalPos = flyHitPos.xz - PortalPos.xz;
        float spreadFactor = 1.0 + flyHeight * 0.015;
        vec2 origLocalPos = flyLocalPos / spreadFactor;

        vec2 flyTexUV = origLocalPos / textureScale + vec2(0.5);
        if (flyTexUV.x < 0.0 || flyTexUV.x > 1.0 || flyTexUV.y < 0.0 || flyTexUV.y > 1.0) continue;

        vec4 flyData = texture(RiftSampler, flyTexUV);
        if (flyData.a > 0.5) continue;

        vec2 flyShardOffset = flyData.gb * 200.0 - vec2(100.0);
        vec2 flyShardCenter = origLocalPos + flyShardOffset;
        float flyPhysDist = length(flyShardCenter);

        vec2 flyP2 = vec2(dot(flyShardCenter, vec2(127.1, 311.7)), dot(flyShardCenter, vec2(269.5, 183.3)));
        float flyH = fract(sin(flyP2.x) * 43758.5453);
        float flyDelay = (flyPhysDist / 75.0) * 0.8 + flyH * 0.35;
        float flyRawProgress = (TransitionTime - shatterTime - flyDelay) / 0.65;
        float flyProgress = clamp(flyRawProgress, 0.0, 1.0);

        if (flyProgress <= 0.01 || flyProgress >= 1.0) continue;

        float flyT = flyProgress * 3.0;
        float explosionForce = pow(max(1.0 - flyPhysDist / 80.0, 0.0), 2.0) * 150.0;
        float velY = explosionForce * (0.5 + flyH * 0.8);
        float expectedHeight = velY * flyT - 50.0 * flyT * flyT * 0.5;
        if (abs(expectedHeight - flyHeight) > 12.0) continue;

        float flyEdgeDist = (1.0 - flyData.r) * 0.15;
        float fadeOut = pow(1.0 - flyProgress, 0.7);
        float flash = exp(-flyProgress * 5.0) * 2.0;
        float edgeGlow = exp(-flyEdgeDist * 30.0);

        vec3 shardColor = vec3(0.2, 0.6, 0.9) * fadeOut + vec3(0.9, 0.97, 1.0) * edgeGlow * fadeOut + vec3(1.0) * flash;
        float shardAlpha = min(fadeOut * 0.9 + flash * 0.5 + edgeGlow * 0.2, 1.0) * 0.8;
        shardAlpha *= max(1.0 - flyHeight / 100.0, 0.1);

        totalCrackColor = totalCrackColor + shardColor * shardAlpha * (1.0 - totalCrackAlpha);
        totalCrackAlpha = totalCrackAlpha + shardAlpha * (1.0 - totalCrackAlpha);

        if (totalCrackAlpha > 0.95) break;
    }

    // 崩溃闪光（shatter 后 0.25s 内）
    float crashT = TransitionTime - shatterTime;
    if (crashT >= 0.0 && crashT < 0.25) {
        float cp = clamp(1.0 - length(localPos) / BULGE_RADIUS, 0.0, 1.0);
        float fb = (1.0 - crashT / 0.25) * (0.3 + cp * 0.7) * 0.9;
        totalCrackColor = totalCrackColor + vec3(1.0) * fb * (1.0 - totalCrackAlpha);
        totalCrackAlpha = totalCrackAlpha + fb * (1.0 - totalCrackAlpha);
    }

    // 鼓包辉光（破碎前）
    if (bAmp > 0.001 && TransitionTime < shatterTime) {
        float cp = clamp(1.0 - length(localPos) / BULGE_RADIUS, 0.0, 1.0);
        float sg = bAmp * pow(cp, 1.5) * 0.25;
        totalCrackColor = totalCrackColor + vec3(0.95, 0.98, 1.0) * sg * (1.0 - totalCrackAlpha);
        totalCrackAlpha = totalCrackAlpha + sg * (1.0 - totalCrackAlpha);
    }

    if (totalCrackAlpha > 0.001) {
        vec3 crackColor = totalCrackColor / max(totalCrackAlpha, 0.0001);
        float crackAlpha = totalCrackAlpha;
        // 源库输出带 alpha 的裂缝色（blend 叠加）；我们管线无混合 → mix 合成到 scene
        fragColor = vec4(mix(scene, crackColor, clamp(crackAlpha, 0.0, 1.0)), 1.0);
    } else {
        fragColor = vec4(scene, 1.0);
    }
}