#version 310 es

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aColor;
uniform mat4 transform;
uniform mat4 model;
uniform mat4 view;
out vec3 Color;

void main() {
    gl_Position = view * model * transform * vec4(aPos, 1.0);
    gl_PointSize = 4.0;
    Color = aColor;
}