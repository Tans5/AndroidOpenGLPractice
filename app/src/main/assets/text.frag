#version 310 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;

void main() {
    FragColor = vec4(1.0, 0.0, 0.0, texture(Texture, TexCoord).a);
}