#version 310 es

precision highp float; // Define float precision

// 圈内放大，intensity取值范围是0～1
vec2 enlarge(vec2 currentCoordinate, vec2 circleCenter, float radius, float intensity)
{
    float dis = distance(currentCoordinate, circleCenter);
    if (dis > radius) {
        return currentCoordinate;
    }
    float k0 = intensity / 100.0;
    float k = 1.0 - k0 * (1.0 - pow(dis / radius, 2.0));
    float nx = (currentCoordinate.x - circleCenter.x) * k + circleCenter.x;
    float ny = (currentCoordinate.y - circleCenter.y) * k + circleCenter.y;
    return vec2(nx, ny);
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

// 瘦脸
vec2 stretch(vec2 textureCoord, vec2 circleCenter, vec2 targetPosition, float radius, float strength)
{
    float k1 = distance(textureCoord, circleCenter);
    if (k1 > radius) {
        return textureCoord;
    }

    float k0 = 100.0 / strength;

    float tx = pow((pow(radius, 2.0) - pow(textureCoord.x - circleCenter.x, 2.0)) / (pow(radius, 2.0) - pow(textureCoord.x - circleCenter.x, 2.0) + k0 * pow(targetPosition.x - circleCenter.x, 2.0)), 2.0) * (targetPosition.x - circleCenter.x);
    float ty = pow((pow(radius, 2.0) - pow(textureCoord.y - circleCenter.y, 2.0)) / (pow(radius, 2.0) - pow(textureCoord.y - circleCenter.y, 2.0) + k0 * pow(targetPosition.y - circleCenter.y, 2.0)), 2.0) * (targetPosition.y - circleCenter.y);

    float nx = textureCoord.x - tx * (1.0 - k1 / radius);
    float ny = textureCoord.y - ty * (1.0 - k1 / radius);
    return vec2(nx, ny);
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

//    float r = y + 1.280 * (v - 128.0);
//    float g = y - 0.215 * (u - 128.0) - 0.381 * (v - 128.0);
//    float b = y + 2.128 * (u - 128.0);
    float r = 1.164 * (y - 16.0) + 1.793 * (v - 128.0);
    float g = 1.164 * (y - 16.0) - 0.213 * (u - 128.0) - 0.533 * (v - 128.0);
    float b = 1.164 * (y - 16.0) + 2.112 * (u - 128.0);

    return vec3(r, g, b);
}


/**
  * 大眼
  *
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
    // float radius = distance(vec2(ovalX, ovalY), center);
    return enlarge(currentCoordinate, center, max(a, b), intensity);
}

/**
 * 美白
 */
vec3 whitening(vec3 rgb, float intensity) {
    vec3 yuv255 = rgbToYuv(rgb * 255.0);
    yuv255.x = log(yuv255.x / 255.0 * (intensity - 1.0) + 1.0) / log(intensity) * 255.0;
    vec3 rgb255 = yuvToRgb(yuv255);
    return rgb255 / 255.0;
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

// 美白
uniform int whiteningSwitch;

// 瘦脸
uniform float thinRadius;
uniform vec2 stretchCenter;
uniform vec2 leftFaceThinCenter;
uniform vec2 rightFaceThinCenter;

void main() {
    // 大眼
    vec2 fixedCoord = enlargeOval(TexCoord, leftEyeCenter, leftEyeA, leftEyeB, 20.0);
    fixedCoord = enlargeOval(fixedCoord, rightEyeCenter, rightEyeA, rightEyeB, 20.0);

    // 瘦脸
    fixedCoord = stretch(fixedCoord, leftFaceThinCenter, stretchCenter, thinRadius, 40.0);
    fixedCoord = stretch(fixedCoord, rightFaceThinCenter, stretchCenter, thinRadius, 40.0);

    vec4 outputColor = texture(Texture, fixedCoord);
    // 美白
    if (whiteningSwitch == 1) {
        outputColor = vec4(whitening(vec3(outputColor.x, outputColor.y, outputColor.z), 2.5), 1.0);
    }
    FragColor = outputColor;
}