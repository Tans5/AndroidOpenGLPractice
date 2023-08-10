#version 310 es

precision highp float; // Define float precision
precision highp sampler2DArray;

uniform sampler2DArray Texture;
in vec3 TexCoord;
out vec4 FragColor;

void main() {
    FragColor = texture(Texture, TexCoord);
}