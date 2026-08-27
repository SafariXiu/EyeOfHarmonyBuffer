#version 330 core
// 维度转场白化球（逐行翻译自 Nostalgia 的 radial_whiteout.fsh，GUN2RAS，CC0-1.0，见 LICENSE-nostalgia.txt）。
// 源库语义：半径完全由 CPU 驱动（ExtraData.z = outerRadius = whiteRadius 白壳半径、
// ExtraData.w = innerRadius = alphaRadius 内圈半径）；内圈深处是"透明洞"（露出原场景），
// 内圈边缘白辉光；环带内上方（y > 信标 y）全白、下方渐晕；天空/极近深度直接透出原场景。
// 适配：std140 UBO -> 单独 uniform；InverseViewProj -> inverse(投影) + transpose(纯旋转ModelView) + 相机位置；
// 管线无混合 -> mix 合成（与源库 blend 语义等价）。

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec4 BeaconAndTimer;   // xyz = 信标中心（+0.5），w = 转场总秒数
uniform vec4 ExtraData;        // x = GlobalFade(whiteoutAlpha)，y = inNewDimension，z = OuterRadius(whiteRadius)，w = InnerRadius(alphaRadius)
uniform vec4 CamPosData;       // xyz = 相机位置，w = 云高（源库未使用）
uniform vec4 SkyColor;         // 源库声明但未使用（保留对齐）
uniform float uCoverWhite;     // 落地揭幕白幕（1.7.10 适配）：直接混入全屏白色

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;

    // 源库 early-out：天空 / 极近深度 → 透明（显示原场景）；
    // 落地揭幕白幕必须先应用，否则天空像素提前 return 永远漏白
    if (depth >= 0.9999 || depth <= 0.0001) {
        fragColor = vec4(mix(scene, vec3(1.0), clamp(uCoverWhite, 0.0, 1.0)), 1.0);
        return;
    }

    // 世界重建：inverse(投影) * NDC -> 视图空间 -> transpose(纯旋转 ModelView) -> + 相机位置
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, depth, 1.0);
    vec3 camRelative = (transpose(ModelViewMat) * vec4(homPos.xyz / homPos.w, 1.0)).xyz;
    vec3 absoluteWorldPos = camRelative + CamPosData.xyz;

    vec3 blockPos = floor(absoluteWorldPos) + vec3(0.5);
    float noise = fract(sin(dot(blockPos, vec3(12.9898, 78.233, 45.164))) * 43758.5453);
    vec3 diff = blockPos - BeaconAndTimer.xyz;
    float dist = length(diff) + (noise - 0.5) * 4.0;

    float outerRadius = ExtraData.z;
    float innerRadius = ExtraData.w;
    float globalFade = ExtraData.x;

    // 源库：whiteFactor = clamp(dist / outerRadius, 0, 1)（outerRadius=0 时 dist/0=inf -> 白）；
    // 这里加 max 保护避免除零产生 NaN（视觉一致）。
    float whiteFactor = clamp(dist / max(outerRadius, 0.0001), 0.0, 1.0);
    vec3 echoColor = vec3(0.55, 0.65, 0.75);
    vec3 renderColor = mix(echoColor, vec3(1.0), whiteFactor);

    if (dist < innerRadius) {
        float distToEdge = innerRadius - dist;
        if (distToEdge < 4.0) {
            float glowAlpha = pow(1.0 - (distToEdge / 4.0), 2.0);
            fragColor = vec4(mix(scene, renderColor, glowAlpha * globalFade), 1.0);
        } else {
            // 内圈深处：透明洞（露出原场景）
            fragColor = vec4(scene, 1.0);
        }
    } else if (dist < outerRadius) {
        if (absoluteWorldPos.y > BeaconAndTimer.y) {
            // 环带内上方：全白幕
            fragColor = vec4(mix(scene, renderColor, globalFade), 1.0);
        } else {
            float sphereAlpha = 1.0 - smoothstep(outerRadius - 10.0, outerRadius, dist);
            float innerAlpha = smoothstep(innerRadius, innerRadius + 5.0, dist);
            float waveAlpha = min(sphereAlpha, innerAlpha);
            if (waveAlpha > 0.01) {
                fragColor = vec4(mix(scene, renderColor, waveAlpha * globalFade), 1.0);
            } else {
                fragColor = vec4(scene, 1.0);
            }
        }
    } else {
        fragColor = vec4(scene, 1.0);
    }

    // 落地揭幕（1.7.10 适配）：传送前/换维后全屏白幕混入（HUD 由 Pre(ALL) setCanceled 隐藏）
    fragColor = vec4(mix(fragColor.rgb, vec3(1.0), clamp(uCoverWhite, 0.0, 1.0)), 1.0);
}
