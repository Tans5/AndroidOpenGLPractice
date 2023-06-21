#version 310 es

precision highp float; // Define float precision
uniform sampler2D Texture;
in vec2 TexCoord;
out vec4 FragColor;

uniform vec2 leftEyeIris;
uniform float leftEyeIrisRadius;

// 圈内放大，intensity取值范围是0～1
vec2 enlarge(vec2 currentCoordinate, vec2 circleCenter, float radius, float intensity)
{
    float currentDistance = distance(currentCoordinate, circleCenter);
    if (currentDistance > radius) {
        return currentCoordinate;
    }
    float weight = currentDistance / radius;
    // weight 的指数可以是 2， 3， 4 等
    weight = 1.0 - intensity * (1.0 - weight * weight);
    weight = clamp(weight, 0.0, 1.0);
    currentCoordinate = circleCenter + (currentCoordinate - circleCenter) * weight;
    return currentCoordinate;
}

void main() {
    vec2 fixedCoord = enlarge(TexCoord, leftEyeIris, leftEyeIrisRadius, 0.8);
    FragColor = texture(Texture, fixedCoord);
}