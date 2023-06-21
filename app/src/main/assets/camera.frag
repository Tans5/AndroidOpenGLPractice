#version 310 es

precision highp float; // Define float precision
uniform sampler2D Texture;
in vec2 TexCoord;
out vec4 FragColor;

void main() {
    FragColor = texture(Texture, TexCoord);
}