#version 330 core
// 维度转场天空撕裂 v1（移植自 Nostalgia 的 portal_sky_rip.fsh，GUN2RAS，CC0-1.0，见 LICENSE-nostalgia.txt）
// 只在天空（深度 >= 0.9999）绘制：以 PortalPos 为中心的扩张圆盘——
// 内圈黑幕 + 边缘白辉光 + 外圈噪声锯齿波纹微光。输出与输入画面合成。

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;
uniform vec3 PortalPos;       // 撕裂平面中心（y = center.y + 配置高度）
uniform float TransitionTime; // 撕裂时间（秒）
uniform float MaxRadius;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;

    if (depth < 0.9999) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // 相机相对方向（不含相机平移；1.7.10 相机矩阵为纯旋转）。
    // 注意：NDC 重建必须带 depth 分量（vec4(uv, depth, 1.0)），否则构造器缺参报 C1067。
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, depth, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    // ModelViewMat 为纯旋转正交矩阵：逆 = 转置（老 NV 编译器 inverse() 会报 C1067）
    vec3 camRelative = (transpose(ModelViewMat) * vec4(viewPos, 1.0)).xyz;

    if (camRelative.y <= 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // 视线与撕裂平面（y = PortalPos.y）求交
    float t = (PortalPos.y - CameraPosition.y) / camRelative.y;
    vec3 hitPos = CameraPosition + camRelative * t;
    vec3 blockPos = floor(hitPos) + vec3(0.5);
    vec2 planePos = blockPos.xz;
    vec2 centerPos = PortalPos.xz;

    float noise = fract(sin(dot(blockPos, vec3(12.9898, 78.233, 45.164))) * 43758.5453);
    float maxRadius = max(MaxRadius, 1.0);
    float currentRadius = min(TransitionTime * 40.0, maxRadius);
    float innerRadius = currentRadius - 5.0;
    float dist = distance(planePos, centerPos) + (noise - 0.5) * 8.0;

    vec3 tearColor = vec3(0.0);
    float tearAlpha = 0.0;

    if (dist < innerRadius) {
        float distToEdge = innerRadius - dist;
        if (distToEdge < 3.0) {
            float glowAlpha = pow(1.0 - (distToEdge / 3.0), 2.0);
            tearColor = vec3(1.0);
            tearAlpha = glowAlpha;
        } else {
            tearColor = vec3(0.0);
            tearAlpha = 1.0;
        }
    } else if (dist < currentRadius) {
        float sphereAlpha = 1.0 - smoothstep(currentRadius - 3.0, currentRadius, dist);
        float innerAlpha = smoothstep(innerRadius, innerRadius + 2.0, dist);
        float waveAlpha = min(sphereAlpha, innerAlpha);

        float pulse1 = sin(planePos.x * 0.4 + TransitionTime * 3.0);
        float pulse2 = cos(planePos.y * 0.4 - TransitionTime * 2.5);
        float smoothShimmer = (pulse1 * pulse2 + 1.0) * 0.5;

        if (waveAlpha > 0.01) {
            tearColor = vec3(1.0);
            tearAlpha = waveAlpha * (0.8 + 0.2 * smoothShimmer);
        }
    }

    fragColor = vec4(mix(scene, tearColor, tearAlpha), 1.0);
}
