#version 120

varying vec2 vNdc;

void main(){
    vNdc = gl_Vertex.xy;
    gl_Position = vec4(gl_Vertex.xy, 1.0, 1.0);
}
