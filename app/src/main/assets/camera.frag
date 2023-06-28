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
    float checkDistence = (dx * dx) / (a * a) + (dy * dy) / (b * b);
    if (checkDistence > 1.0) {
        return currentCoordinate;
    }
    float distanceToCenter = distance(currentCoordinate, center);
    float ovalX = center.x + a * dx / distanceToCenter;
    float ovalY = center.y + b * dy / distanceToCenter;
    float radius = distance(vec2(ovalX, ovalY), center);
    return enlarge(currentCoordinate, center, max(a, b), intensity);
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


vec3 rgbToYuv(vec3 rgb) {
    float r = rgb.x;
    float g = rgb.y;
    float b = rgb.z;

    float y = 0.183 * r + 0.614 * g + 0.062 * b + 16.0;
    float u = -0.101 * r - 0.339 * g + 0.439 * b + 128.0;
    float v = 0.439 * r - 0.399 * g - 0.040 * b + 128.0;

    return vec3(y, u, v);
}

vec3 yuvToRgb(vec3 yuv) {
    float y = yuv.x;
    float u = yuv.y;
    float v = yuv.z;

    float r = y + 1.280 * (v - 128.0);
    float g = y - 0.215 * (u - 128.0) - 0.381 * (v - 128.0);
    float b = y + 2.128 * (u - 128.0);

    return vec3(r, g, b);
}

uniform sampler2D Texture;
in vec2 TexCoord;
out vec4 FragColor;

// 左眼
uniform vec2 leftEyeCenter;
uniform float leftEyeA;
uniform float leftEyeB;

// 右眼
uniform vec2 rightEyeCenter;
uniform float rightEyeA;
uniform float rightEyeB;

void main() {
    // 大眼
    vec2 fixedCoord = enlargeOval(TexCoord, leftEyeCenter, leftEyeA, leftEyeB, 0.4);
    fixedCoord = enlargeOval(fixedCoord, rightEyeCenter, rightEyeA, rightEyeB, 0.4);

    vec4 outputColor = texture(Texture, fixedCoord);
    vec3 yuv = rgbToYuv(vec3(outputColor.x * 255.0, outputColor.y * 255.0, outputColor.z * 255.0));
    vec3 rgb = yuvToRgb(yuv);
    outputColor = vec4(rgb.x / 255.0, rgb.y / 255.0, rgb.z / 255.0, 1.0);
    FragColor = outputColor;
}