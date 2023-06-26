#version 310 es

precision highp float; // Define float precision

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

/**
  * 椭圆的方程: (x - h)^2 / a^2 + (y - k)^2 / b^2 = 1
  * x = h + a * cost
  * y = k + b * sint
 */
vec2 enlargeOval(vec2 currentCoordinate, vec2 center, float a, float b, float intensity) {
    float dx = currentCoordinate.x - center.x;
    float dy = currentCoordinate.y - center.y;
    float checkDistence = dx * dx / a * a + dy * dy / b * b;
    if (checkDistence > 1.0) {
        return currentCoordinate;
    }
    float distanceToCenter = distance(currentCoordinate, center);
    float ovalX = center.x + a * dx / distanceToCenter;
    float ovalY = center.y + b * dy / distanceToCenter;
    float radius = distance(vec2(ovalX, ovalY), center);
    return enlarge(currentCoordinate, center, radius, intensity);
}

// 圈内缩小，intensity取值范围是0～1
vec2 shrink(vec2 currentCoordinate, vec2 circleCenter, float radius, float intensity)
{
    float currentDistance = distance(currentCoordinate, circleCenter);
    if (currentDistance > radius) {
        return currentCoordinate;
    }

    float weight = currentDistance / radius;
    // weight 的指数可以是 2， 3， 4 等
    weight = 1.0 - intensity * (1.0 - weight * weight);
    weight = clamp(weight, 0.0001, 1.0);
    currentCoordinate = circleCenter + (currentCoordinate - circleCenter) / weight;
    return currentCoordinate;
}

// 拉伸，curve就是指数，1，2，3，4这些
vec2 stretch(vec2 textureCoord, vec2 circleCenter, vec2 targetPosition, float radius, float curve)
{
    float currentDistance = distance(textureCoord, circleCenter);
    if (currentDistance > radius) {
        return textureCoord;
    }

    vec2 direction = targetPosition - circleCenter;
    float weight = currentDistance / radius;
    weight = 1.0 - pow(weight, curve);
    weight = clamp(weight, 0.0, 1.0);
    textureCoord = textureCoord - direction * weight;

    return textureCoord;
}

uniform sampler2D Texture;
in vec2 TexCoord;
out vec4 FragColor;

uniform vec2 leftEyeCenter;
uniform float leftEyeA;
uniform float leftEyeB;

void main() {
    vec2 fixedCoord = enlargeOval(TexCoord, leftEyeCenter, leftEyeA, leftEyeB, 0.8);
    FragColor = texture(Texture, fixedCoord);
}