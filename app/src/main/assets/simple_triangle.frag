#version 310 es

precision highp float; // Define float precision
in vec3 pColor;
out vec4 FragColor;

void main() {
    FragColor = vec4(pColor, 1.0);
}