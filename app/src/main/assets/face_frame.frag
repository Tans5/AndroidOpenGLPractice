#version 310 es

precision highp float; // Define float precision
in vec3 Color;
out vec4 FragColor;

void main() {
    FragColor = vec4(Color, 1.0);
}