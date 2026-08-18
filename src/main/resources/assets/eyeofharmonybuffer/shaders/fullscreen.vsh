#version 330 core
// 轨道炮后处理全屏顶点着色器（GLSL 330 core 原生语法）。
// 注意：必须避免 GLSL 120 兼容内建（texture2D/varying/gl_FragColor 等），
// 否则 Angelica 的 CompatShaderTransformer 会用 ANTLR 解析源（遇到 GlShader
// 附加的 '\0' 结尾会报语法错误并卡死编译）。
// FullScreenQuadRenderer 的顶点 Position/UV 均为 [0,1] 范围

in vec4 Position;

uniform vec2 OutSize;

out vec2 texCoord;
out float viewWidth;
out float viewHeight;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.2, 1.0);
    texCoord = Position.xy;
    viewWidth = OutSize.x;
    viewHeight = OutSize.y;
}
