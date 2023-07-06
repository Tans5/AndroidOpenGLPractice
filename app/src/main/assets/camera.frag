#version 310 es

precision highp float; // Define float precision


vec2 enlarge(vec2 currentCoordinate, vec2 circleCenter, float radius, float strength)
{
    float dis = distance(currentCoordinate, circleCenter);
    if (dis > radius) {
        return currentCoordinate;
    }
    float k0 = strength / 100.0;
    float k = 1.0 - k0 * (1.0 - pow(dis / radius, 2.0));
    float nx = (currentCoordinate.x - circleCenter.x) * k + circleCenter.x;
    float ny = (currentCoordinate.y - circleCenter.y) * k + circleCenter.y;
    return vec2(nx, ny);
}

//// 圈内缩小，intensity取值范围是0～1
//vec2 shrink(vec2 currentCoordinate, vec2 circleCenter, float radius, float intensity)
//{
//    float currentDistance = distance(currentCoordinate, circleCenter);
//    if (currentDistance > radius) {
//        return currentCoordinate;
//    }
//
//    float weight = currentDistance / radius;
//    // weight 的指数可以是 2， 3， 4 等
//    weight = 1.0 - intensity * (1.0 - weight * weight);
//    weight = clamp(weight, 0.0001, 1.0);
//    currentCoordinate = circleCenter + (currentCoordinate - circleCenter) / weight;
//    return currentCoordinate;
//}


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
 * 是否是皮肤颜色
 */
bool isSkinColor(vec4 color) {
    float r = color.x * 255.0;
    float g = color.y * 255.0;
    float b = color.z * 255.0;
    return r > 95.0 && g > 40.0 && b > 20.0 && r > g && r > b && (max(r, max(g, b)) - min(r, min(g, b))) > 15.0 && (abs(r - b) > 15.0);
}

bool checkTextureCoord(vec2 coord) {
    return coord.x >= 0.0 && coord.x <= 1.0 && coord.y >= 0.0 && coord.y <= 1.0;
}

/**
  * 正态分布函数
 */
float gauthFunc(float maxValue, float centerLine, float changeRate, float x) {
    return maxValue * exp(- pow(x - centerLine, 2.0) / (2.0 * changeRate));
}

/**
  * 大眼
  *
  * 椭圆的方程: (x - h)^2 / a^2 + (y - k)^2 / b^2 = 1
  * x = h + a * cost
  * y = k + b * sint
 */
vec2 enlargeOval(vec2 currentCoordinate, vec2 center, float a, float b, float strength) {
    float dx = currentCoordinate.x - center.x;
    float dy = currentCoordinate.y - center.y;
    float checkDistence = (dx * dx) / (a * a) + (dy * dy) / (b * b);
    if (checkDistence > 1.0) {
        return currentCoordinate;
    }
    float x = 0.0;
    float y = 0.0;
    float x1 = 0.0;
    float y1 = 0.0;
    float x2 = 0.0;
    float y2 = 0.0;
    float minStep = 0.0003;
    if (abs(center.x - currentCoordinate.x) < minStep) {
        x1 = center.x;
        y1 = center.y + b;
        x2 = center.x;
        y2 = center.y - b;
    } else if (abs(center.y - currentCoordinate.y) < minStep) {
        x1 = center.x + a;
        y1 = center.y;
        x2 = center.x - a;
        y2 = center.y;
    } else {
        float lineA = (currentCoordinate.y - center.y) / (currentCoordinate.x - center.x);
        float lineB = (currentCoordinate.y * center.x - currentCoordinate.x * center.y) / (center.x - currentCoordinate.x);
        float fucA = ((1.0 / pow(a, 2.0)) + (pow(lineA, 2.0) / pow(b, 2.0)));
        float fucB = ((2.0 * lineA * (lineB - center.y)) / pow(b, 2.0)) - (2.0 * center.x) / pow(a, 2.0);
        float fucC = pow(center.x / a, 2.0) + pow((lineB - center.y) / b, 2.0) - 1.0;
        x1 = (- fucB + sqrt(pow(fucB, 2.0) - 4.0 * fucA * fucC)) / (2.0 * fucA);
        y1 = lineA * x1 + lineB;
        x2 = (- fucB - sqrt(pow(fucB, 2.0) - 4.0 * fucA * fucC)) / (2.0 * fucA);
        y2 = lineA * x2 + lineB;
    }

    float d1 = distance(vec2(x1, y1), currentCoordinate);
    float d2 = distance(vec2(x2, y2), currentCoordinate);
    if (d1 < d2) {
        x = x1;
        y = y1;
    } else {
        x = x2;
        y = y2;
    }
    float radius = distance(center, vec2(x, y));
    return enlarge(currentCoordinate, center, radius, strength);
}

// 瘦脸
vec2 stretch(vec2 textureCoord, vec2 circleCenter, vec2 targetPosition, float radius, float strength)
{
    float k1 = distance(textureCoord, circleCenter);
    if (k1 >= radius) {
        return textureCoord;
    }

    float k0 = 100.0 / strength;

    float tx = pow((pow(radius, 2.0) - pow(textureCoord.x - circleCenter.x, 2.0)) / (pow(radius, 2.0) - pow(textureCoord.x - circleCenter.x, 2.0) + k0 * pow(targetPosition.x - circleCenter.x, 2.0)), 2.0) * (targetPosition.x - circleCenter.x);
    float ty = pow((pow(radius, 2.0) - pow(textureCoord.y - circleCenter.y, 2.0)) / (pow(radius, 2.0) - pow(textureCoord.y - circleCenter.y, 2.0) + k0 * pow(targetPosition.y - circleCenter.y, 2.0)), 2.0) * (targetPosition.y - circleCenter.y);

    float nx = textureCoord.x - tx * (1.0 - k1 / radius);
    float ny = textureCoord.y - ty * (1.0 - k1 / radius);
    return vec2(nx, ny);
}

/**
 * 美白
 */
vec3 whitening(vec3 rgb, float strength) {
    vec3 yuv255 = rgbToYuv(rgb * 255.0);
    yuv255.x = log(yuv255.x / 255.0 * (strength - 1.0) + 1.0) / log(strength) * 255.0;
    vec3 rgb255 = yuvToRgb(yuv255);
    return rgb255 / 255.0;
}


/**
 * 磨皮
 */
vec4 skinSmooth(sampler2D inputTexture, vec2 texCoord, float widthPixelStep, float heightPixelStep, float radius, float strength) {
    vec4 centerColor = texture(inputTexture, texCoord);

    if (isSkinColor(centerColor)) {
        float gauthMaxValue = 1.0;
        float gauthCenterLine = 0.0;

        vec4 colorSum = centerColor;
        float colorRateSum = 1.0;

        vec2 upVec = vec2(0.0, - heightPixelStep);
        vec2 upRightVec = vec2(widthPixelStep, - heightPixelStep);
        vec2 rightVec = vec2(widthPixelStep, 0.0);
        vec2 rightDownVec = vec2(widthPixelStep, heightPixelStep);
        vec2 downVec = vec2(0.0, heightPixelStep);
        vec2 downLeftVec = vec2(-widthPixelStep, heightPixelStep);
        vec2 leftVec = vec2(-widthPixelStep, 0.0);
        vec2 leftUpVec = vec2(-widthPixelStep, -heightPixelStep);
        for (float i = 1.0; i <= radius; i = i + 1.0) {
            float colorRate = gauthFunc(gauthMaxValue, gauthCenterLine, strength, i);
            vec2 u = texCoord + upVec * i;
            if (checkTextureCoord(u)) {
                vec4 c = texture(inputTexture, u);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 ur = texCoord + upRightVec * i;
            if (checkTextureCoord(ur)) {
                vec4 c = texture(inputTexture, ur);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 r = texCoord + rightVec * i;
            if (checkTextureCoord(r)) {
                vec4 c = texture(inputTexture, r);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 rd = texCoord + rightDownVec * i;
            if (checkTextureCoord(rd)) {
                vec4 c = texture(inputTexture, rd);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 d = texCoord + downVec * i;
            if (checkTextureCoord(d)) {
                vec4 c = texture(inputTexture, d);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 dl = texCoord + downLeftVec * i;
            if (checkTextureCoord(dl)) {
                vec4 c = texture(inputTexture, dl);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 l = texCoord + leftVec * i;
            if (checkTextureCoord(l)) {
                vec4 c = texture(inputTexture, l);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }

            vec2 lu = texCoord + leftUpVec * i;
            if (checkTextureCoord(lu)) {
                vec4 c = texture(inputTexture, lu);
                if (isSkinColor(c)) {
                    colorRateSum += colorRate;
                    colorSum += colorRate * c;
                }
            }
        }
        return colorSum / colorRateSum;
    } else {
        return centerColor;
    }
}

uniform sampler2D Texture;
in vec2 TexCoord;
out vec4 FragColor;

// 大眼
uniform vec2 leftEyeCenter;
uniform float leftEyeA;
uniform float leftEyeB;
uniform vec2 rightEyeCenter;
uniform float rightEyeA;
uniform float rightEyeB;
uniform float enlargeEyesStrength;

// 瘦脸
uniform float thinRadius;
uniform vec2 stretchCenter;
uniform vec2 leftFaceThinCenter;
uniform vec2 rightFaceThinCenter;
uniform float thinStrength;

// 美白
uniform int whiteningSwitch;

// 磨皮
uniform int skinSmoothSwitch;
uniform float textureWidthPixelStep;
uniform float textureHeightPixelStep;

void main() {
    // 大眼
    vec2 fixedCoord = enlargeOval(TexCoord, leftEyeCenter, leftEyeA, leftEyeB, enlargeEyesStrength);
    fixedCoord = enlargeOval(fixedCoord, rightEyeCenter, rightEyeA, rightEyeB, enlargeEyesStrength);

    // 瘦脸
    fixedCoord = stretch(fixedCoord, leftFaceThinCenter, stretchCenter, thinRadius, thinStrength);
    fixedCoord = stretch(fixedCoord, rightFaceThinCenter, stretchCenter, thinRadius, thinStrength);

    vec4 outputColor = texture(Texture, fixedCoord);

    // 磨皮
    if (skinSmoothSwitch == 1) {
        vec4 smoothColor = skinSmooth(Texture, fixedCoord, textureWidthPixelStep, textureHeightPixelStep, 4.0, 6.0);
        outputColor = mix(outputColor, smoothColor, 0.6);
    }

    // 美白
    if (whiteningSwitch == 1) {
        outputColor = vec4(whitening(vec3(outputColor.x, outputColor.y, outputColor.z), 2.5), 1.0);
    }
    FragColor = outputColor;
}