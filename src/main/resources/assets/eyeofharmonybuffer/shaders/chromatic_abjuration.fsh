#version 330 core
// 轨道炮色差后处理（移植自 orbital-railgun 的 chromatic_abjuration.fsh）
// GLSL 330 core 原生语法（避开 CompatShaderTransformer 的 ANTLR 解析路径）。
// 打击开始 37 秒后开始生效：围绕打击点的旋转 RGB 采样偏移，距离越近强度越大

uniform sampler2D DiffuseSampler;
uniform vec3 CameraPosition;
uniform vec3 BlockPosition;

uniform float iTime;
uniform float StrikeActive;
uniform float StrikeRadius;

in vec2 texCoord;
in float viewHeight;
in float viewWidth;

out vec4 fragColor;

vec2 rotate(vec2 p, float r) {
    return mat2(cos(r), -sin(r), sin(r), cos(r)) * p;
}

void main() {
    float frameTimeCounter = max(iTime - 37.0, 0.0);

    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    // 充能期间（非打击）直接透传，不做色差（对齐 Forge 移植版 StrikeActive 门控）
    if (StrikeActive < 0.5) {
        fragColor = vec4(original, 1.0);
        return;
    }
    vec2 one_pixel = vec2(1.0 / viewWidth, 1.0 / viewHeight);
    vec2 rotated_pixel = rotate(one_pixel, -frameTimeCounter);

    // 与 Forge 移植版对齐：分母使用实际打击半径（默认 24）
    float radius = max(StrikeRadius, 0.0001);
    float scale = max((-pow((frameTimeCounter - 0.84) * 8.0, 2.0) + 50.0) * 25.0 / (distance(CameraPosition, BlockPosition) - radius + 25.0), 0.0);
    float ca_red = texture(DiffuseSampler, texCoord + rotated_pixel * scale).r;
    rotated_pixel = rotate(rotated_pixel, 2.09439510239);
    float ca_green = texture(DiffuseSampler, texCoord + (rotated_pixel - one_pixel) * scale).g;
    rotated_pixel = rotate(rotated_pixel, 2.09439510239);
    float ca_blue = texture(DiffuseSampler, texCoord + (rotated_pixel - one_pixel) * scale).b;

    fragColor = vec4(mix(original, vec3(ca_red, ca_green, ca_blue), 2.0 * length(texCoord - vec2(0.5))), 1.0);
}
