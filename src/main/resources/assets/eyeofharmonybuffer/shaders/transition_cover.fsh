#version 330 core
// 落地揭幕白幕覆盖（GLProgram 全屏 quad）：采样主 FBO 当前内容（世界+手臂），
// 按 uCoverWhite 混入纯白。在 RenderGameOverlayEvent.Pre（手臂渲染完成后、HUD 前）调用，
// 让白幕覆盖世界、手臂、HUD 全部画面，避免手臂露在白幕之外。

uniform sampler2D DiffuseSampler;
uniform float uCoverWhite;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    fragColor = vec4(mix(scene, vec3(1.0), clamp(uCoverWhite, 0.0, 1.0)), 1.0);
}
