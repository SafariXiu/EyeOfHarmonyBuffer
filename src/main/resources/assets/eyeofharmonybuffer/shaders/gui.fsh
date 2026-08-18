#version 330 core
// 轨道炮瞄准 GUI 后处理（移植自 orbital-railgun 的 gui.fsh）
// SDF 目标选框（深度感知）+ 旋转 AOE 圈 + 屏幕内框（扫描线/glitch）+ 旋转准星。
// 世界重建：inverse(ModelViewMat)*视图 + CameraPosition（1.7.10 相机矩阵为纯旋转）。
// 移植自 orbital-railgun（Mishkis，MIT License，见 LICENSE-orbital-railgun.txt）。
#define STEPS 500
#define MIN_DIST 0.001
#define MAX_DIST 250.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform float IsBlockHit;
uniform float StrikeActive;
uniform vec3 BlockPosition;
uniform float SelectionActive;

const vec3 red = 2. * vec3(0.878, 0.427, 0.427);
const vec3 green = 2. * vec3(0.13, 0.65, 0.23);
const vec3 blue = vec3(0.62, 0.93, 0.93);

vec2 scale = vec2(0.);

uniform float iTime;
uniform float StrikeRadius;

uniform vec3  u_MarkerInnerColor;
uniform float u_MarkerInnerAlpha;

uniform vec3  u_MarkerOuterColor;
uniform float u_MarkerOuterAlpha;

in vec2 texCoord;
in float viewHeight;
in float viewWidth;

out vec4 fragColor;

// https://www.reedbeta.com/blog/hash-functions-for-gpu-rendering/
float pcg_hash(uint seed) {
    uint state = seed * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return float((word >> 22u) ^ word) / 2147483647.;
}

// https://iquilezles.org/articles/distfunctions/
float sDist(vec3 p) {
    p = abs(p) - vec3(0.51);
    const float e = 0.02;
    vec3 q = abs(p + e) - e;
    return min(min(
            length(max(vec3(p.x, q.y, q.z), 0.0)) + min(max(p.x, max(q.y, q.z)), 0.0),
            length(max(vec3(q.x, p.y, q.z), 0.0)) + min(max(q.x, max(p.y, q.z)), 0.0)),
        length(max(vec3(q.x, q.y, p.z), 0.0)) + min(max(q.x, max(q.y, p.z)), 0.0));
}

float sdBox(vec2 p, vec2 s) {
    p = abs(p) - s * scale;
    return length(max(p, 0.)) + min(max(p.x, p.y), 0.);
}

vec4 computeUiLayer(float dist) {
    vec3 col = green;
    vec3 overlay = mix(col, col / 2., smoothstep(0.01, 0., dist));
    float threshold = 0.25 * step(0., -dist) + step(0., dist) * 0.004 / dist;
    threshold = clamp(threshold, 0., 1.);

    float scan_lines = 1. + max(sin(20. * (texCoord.y) + 0.5 * iTime) - sin(80. * (texCoord.y) + 3. * iTime), 0.) / 2.;
    overlay *= scan_lines;

    float glitch = 1. + 0.2 * pcg_hash(uint(round((texCoord.x + texCoord.y * viewHeight) * viewWidth) + round(iTime * viewWidth * viewHeight)));
    overlay *= glitch;

    float alpha = clamp(threshold * SelectionActive, 0., 1.);
    return vec4(overlay, alpha);
}

float raycast(vec3 point, vec3 dir) {
    float traveled = 0.;
    for (int i = 0; i < STEPS; i++) {
        float safe = sDist(point);
        if (safe <= MIN_DIST || traveled >= MAX_DIST) {
            break;
        }

        traveled += safe;
        point += dir * safe;
    }
    return traveled;
}

// 1.7.10 适配：InverseTransformMatrix = inverse(投影)，ModelViewMat 为纯旋转矩阵
// （1.7.10 相机矩阵不含平移，世界坐标 = 旋转逆 * 视图坐标 + 相机位置，
//  与 Forge 移植版 gui.fsh 完全一致）
vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.)).xyz + CameraPosition;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec3 start_point = worldPos(vec3(texCoord, 0)) - BlockPosition;
    vec3 end_point = worldPos(vec3(texCoord, depth)) - BlockPosition;
    vec3 dir = normalize(end_point - start_point);

    scale = vec2(min(pow(iTime * 1.5, 2.), 1.), pow(clamp(1.5 * iTime - 1., 0., 1.), 2.));

    vec2 uv = texCoord - vec2(0.5);

    // 目标选框只出现在中央 45% 区域、命中方块且非打击时（提前判断，跳过无谓的光线步进）。
    // 开火后选框/红色辉光消失（* (1.0 - StrikeActive)）；范围圈仍由 IsBlockHit 控制
    float coveredByScreen = min(step(abs(uv.x), 0.45 * scale.x), step(abs(uv.y), 0.45 * scale.y)) * IsBlockHit * (1.0 - StrikeActive);

    float threshold = 0.;
    if (coveredByScreen > 0.5) {
        float traveled = raycast(start_point, dir);
        vec3 hit_point = start_point + dir * traveled;
        threshold = step(sDist(hit_point), MIN_DIST * 2.);

        // cover by blocks：选框被地形遮挡时不可见
        threshold *= step(distance(start_point, hit_point), distance(start_point, end_point));
    }

    float rotation = 0.2 * iTime;
    mat2 rotationMatrix = mat2(cos(rotation), -sin(rotation), sin(rotation), cos(rotation));

    vec2 AOE = end_point.xz * rotationMatrix;
    float radius = max(StrikeRadius, 0.0001);
    float withinAOE = length(AOE) - radius;
    float indicator = 0.05 / min(abs(withinAOE), min(max(min(sdBox(AOE, vec2(5.5, 0.)), sdBox(AOE, vec2(0., 5.5))), -length(AOE) + 1.5), abs(length(AOE) - 2.5)));

    vec3 baseScene = texture(DiffuseSampler, texCoord).rgb;
    vec3 redContribution = red * 0.03 / sDist(end_point) * coveredByScreen;

    // 攻击范围圈（AOE）：全屏绘制。原版只画在屏幕中央 45% 区域（coveredByScreen），
    // 这里放开为 IsBlockHit 门控，让范围指示始终可见（即阶段一地面圈的 shader 版）。
    // 仅充能瞄准时显示（* (1.0 - StrikeActive)）：开火后爆炸范围由 strike.fsh 呈现，
    // 避免打击期间残留贴地光圈直到打击结束才啪地消失
    vec3 outerBaseRgb = blue * indicator + blue / 5. * step(withinAOE, 0.);
    vec4 outer = vec4(outerBaseRgb, IsBlockHit * (1.0 - StrikeActive));
    outer.rgb *= u_MarkerOuterColor;
    outer.a *= u_MarkerOuterAlpha;

    vec3 original = baseScene + redContribution + outer.rgb * outer.a;

    vec3 world = mix(original, red, threshold);

    vec4 innerBase = computeUiLayer(sdBox(uv, vec2(0.45)));
    vec4 inner = vec4(innerBase.rgb * u_MarkerInnerColor, innerBase.a * u_MarkerInnerAlpha);
    float innerAlpha = clamp(inner.a, 0., 1.);
    vec3 overlay = mix(world, inner.rgb, innerAlpha);

    uv.x *= max(viewWidth, viewHeight) / min(viewWidth, viewHeight);
    uv *= rotationMatrix;
    float crosshair = max(min(sdBox(uv, vec2(0.001, 0.03)), sdBox(uv, vec2(0.03, 0.001))), -sdBox(uv, vec2(0.012)));
    vec4 crossBase = computeUiLayer(crosshair);
    vec4 cross = vec4(crossBase.rgb * u_MarkerInnerColor, crossBase.a * u_MarkerInnerAlpha);
    float crossAlpha = clamp(cross.a, 0., 1.);
    vec3 overlayWithCrosshair = mix(overlay, cross.rgb, crossAlpha);
    overlay = max(overlay, overlayWithCrosshair);

    fragColor = vec4(overlay, 1.);
}
