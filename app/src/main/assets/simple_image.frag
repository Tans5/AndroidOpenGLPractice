#version 310 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;

void main() {
    FragColor = texture(Texture, TexCoord);
}