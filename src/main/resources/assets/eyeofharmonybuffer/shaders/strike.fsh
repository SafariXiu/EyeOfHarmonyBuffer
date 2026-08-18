#version 330 core
// 轨道炮打击后处理（移植自 orbital-railgun 的 strike.fsh，forge 移植版原文）
// GLSL 330 core 原生语法（避开 CompatShaderTransformer 的 ANTLR 解析路径）。
// 时间轴（iTime = 打击秒数，0 起）：
//   localTime = iTime - 4*step(4,iTime) - 32*step(36,iTime)
//   0~4s   落点瞄准环（旋转、渐暗）
//   4~36s  扩张：主球壳 + 六球环绕 + 高低光束 + 冲击波纹（800 步 SDF 光线步进）
//   36s+   爆炸：扩散圆柱 + 多环冲击波 + 淡出
// 世界重建与 gui.fsh 同约定：InverseTransformMatrix = inverse(投影)，
// ModelViewMat = 纯旋转，世界 = inverse(ModelViewMat)*视图 + CameraPosition。
#define STEPS 800
#define MIN_DIST 0.001
#define MAX_DIST 2500.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;
uniform vec3 BlockPosition;

uniform float iTime;
uniform float StrikeActive;
uniform float StrikeRadius;

uniform vec3  u_BeamColor;
uniform float u_BeamAlpha;

uniform vec3  u_MarkerInnerColor;
uniform float u_MarkerInnerAlpha;

uniform vec3  u_MarkerOuterColor;
uniform float u_MarkerOuterAlpha;

const vec3 blue = vec3(0.62, 0.93, 0.93);

const float startTime = 4.;
const float expansionTime = 32.;
const float endTime = startTime + expansionTime;
float localTime = 0.;

in vec2 texCoord;

out vec4 fragColor;

float smooth_min(float a, float b, float k) {
    float diff = a - b;
    return 0.5 * (a + b - sqrt(diff * diff + k * k * k));
}

vec2 rotate(vec2 p, float r) {
    return mat2(cos(r), -sin(r), sin(r), cos(r)) * p;
}

float sDist(vec3 p) {
    if (iTime < endTime) {
        float scale = 1.2 * (expansionTime * 25. / (localTime + 0.7) / 32. - 32. * (1. - pow(clamp(2. * localTime / expansionTime - 1., 0., 10.), 2.))) + 2.4;
        float main_sphere = abs(length(p) + scale);

        // https://iquilezles.org/articles/sdfrepetition/
        float rotation = min(25. / (localTime - 25.) + 1.5, 0);
        p.xz = rotate(p.xz, rotation);

        const float num = 6.28 / 6.;
        float offset = -pow((localTime - 13.) / 1.4, 2.) + 60.;
        float theta = atan(p.z, p.x);
        theta = floor(theta / num);

        float c1 = num * (theta + 0.0);
        vec3 p1 = mat3(cos(c1), 0., -sin(c1), 0., 1., 0., sin(c1), 0., cos(c1)) * p;
        float c2 = num * (theta + 1.0);
        vec3 p2 = mat3(cos(c2), 0., -sin(c2), 0., 1., 0., sin(c2), 0., cos(c2)) * p;

        p1.x -= offset;
        p2.x -= offset;

        float outer_spheres = min(length(p1) + max(scale, -3), length(p2) + max(scale, -3));

        float base = clamp(pow(localTime - 16., 3.) * 50., 0., 5000.);
        float height = min(pow(localTime - 11., 3.) * 50., 5000.);
        float outer_beams = min(length(vec3(p1.x, clamp(p1.y, base, height) - p1.y, p1.z)) - 0.2, length(vec3(p2.x, clamp(p2.y, base, height) - p2.y, p2.z)) - 0.2);

        return smooth_min(main_sphere, smooth_min(outer_spheres, outer_beams, 1.), 5.);
    }

    float radius = max(StrikeRadius, 0.0001);
    float explosion_cylindar = length(p.xz) + 8. / (localTime) - radius;
    return explosion_cylindar;
}

vec2 raycast(vec3 point, vec3 dir) {
    float traveled = 0.;
    int close_steps = 0;
    for (int i = 0; i < STEPS; i++) {
        float safe = sDist(point);
        if (safe <= MIN_DIST || traveled >= MAX_DIST) {
            break;
        }

        traveled += safe;
        point += dir * safe;
        if (safe <= 0.01) {
            close_steps += 1;
        }
    }
    return vec2(traveled, close_steps);
}

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;

    return (inverse(ModelViewMat) * vec4(viewPos, 1.)).xyz + CameraPosition;
}

float shockwave(vec3 point) {
    float dist = sDist(point);

    float default_light = 10. / pow(dist, 2.);

    if (iTime < endTime) {
        float speed_factor = 1. - pow(localTime / expansionTime, 2.);
        speed_factor = clamp(5., -5., speed_factor);
        float shock = 0.05 / abs(fract(2. * dist / expansionTime - localTime * speed_factor) - 0.5) * 2.;

        return default_light + shock * smoothstep(dist + 4., dist + 14., localTime * speed_factor * expansionTime / 2.);
    }

    // 爆炸光柱淡出加速：localTime>=12（iTime>=48）后完全消失（原版要到 56s）
    float fade_factor = clamp(3. / localTime - 0.25, 0., 1.);
    return fade_factor * (default_light
            + 20. / abs(dist - 50. * (localTime - 0.5)) - 0.3
            + 5. / abs(dist - 25. * (localTime - 0.5)) - 0.2
        ) + smoothstep(dist - 10., dist, 80. * (localTime - 2.5));
}

float sdBox(vec2 p, vec2 s) {
    p = abs(p) - s;
    return length(max(p, 0.)) + min(max(p.x, p.y), 0.);
}

void main() {
    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    if (StrikeActive < 0.5) {
        fragColor = vec4(original, 1.0);
        return;
    }
    localTime = iTime - startTime * step(startTime, iTime) - expansionTime * step(endTime, iTime);

    float depth = texture(DepthSampler, texCoord).r;
    vec3 start_point = worldPos(vec3(texCoord, 0)) - BlockPosition;
    vec3 end_point = worldPos(vec3(texCoord, depth)) - BlockPosition;
    vec3 dir = normalize(end_point - start_point);

    if (iTime < startTime) {
        end_point.xz = rotate(end_point.xz, pow(localTime / 2., 4.));
        float radius = max(StrikeRadius, 0.0001);
        float halfRadius = radius * 0.5;
        float dist = max(
            max(length(end_point.xz) - radius, -(length(end_point.xz) - halfRadius * (localTime - 1.7))),
            -min(sdBox(end_point.xz, vec2(0., radius)), sdBox(end_point.xz, vec2(radius, 0.)))
        );
        vec3 col = original + 0.2 / pow(dist, 2.) * blue * step(length(end_point), localTime * 20.);
        col = mix(col, vec3(0.), pow(max(localTime - 3., 0.), 2.));

        vec4 color = vec4(col, 1.);
        color.rgb *= u_BeamColor;
        color.a *= u_BeamAlpha;
        fragColor = color;
        return;
    }

    vec2 hit_result = raycast(start_point, dir);
    vec3 hit_point = start_point + dir * hit_result.x;

    vec3 col = mix(blue, vec3(0.), abs(sin(3.14 * localTime / expansionTime))) + vec3(smoothstep(5., 10., hit_result.y)) * blue;

    float threshold = step(sDist(hit_point), MIN_DIST * 2.);

    // cover by blocks
    threshold *= step(distance(start_point, hit_point), distance(start_point, end_point));

    // 柱体渐隐：爆炸（endTime=36）后 12 秒内淡出（原版要拖到 72s，残留过久）
    threshold *= 1. - pow(clamp((iTime - endTime) / 12., 0., 1.), 2.);
    vec3 shockwave_color = mix(blue, vec3(1.), clamp((iTime - endTime) / 12., 0., 1.));

    vec3 beamColor = mix(original * shockwave(end_point) * shockwave_color, vec3(col), threshold);
    vec4 color = vec4(beamColor, 1.);
    color.rgb *= u_BeamColor;
    color.a *= u_BeamAlpha;

    // 打击结束前 5 秒平滑淡出回原画面（避免 80s 时效果瞬间消失的生硬切换）
    float endFade = clamp((75. - iTime) / 5., 0., 1.);
    color.rgb = mix(original, color.rgb, endFade);
    fragColor = color;
}
