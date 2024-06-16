#version 310 es

layout (location = 0) in vec4 aPos;
layout (location = 1) in vec2 offset;
out vec2 TexCoord;

void main() {
    gl_Position = vec4(aPos.x + offset.x, aPos.y + offset.y, 0.0, 1.0);
    TexCoord = aPos.zw;
}