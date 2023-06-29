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

/**
 * 磨皮
 */
vec4 skinSmooth(sampler2D inputTexture, vec2 texCoord, float widthPixelStep, float heightPixelStep, float radius) {
    vec4 centerColor = texture(inputTexture, texCoord);
    float colorSize = 1.0;
    if (isSkinColor(centerColor)) {
        vec2 upVec = vec2(0.0, - heightPixelStep);
        vec2 upRightVec = vec2(widthPixelStep, - heightPixelStep);
        vec2 rightVec = vec2(widthPixelStep, 0.0);
        vec2 rightDownVec = vec2(widthPixelStep, heightPixelStep);
        vec2 downVec = vec2(0.0, heightPixelStep);
        vec2 downLeftVec = vec2(-widthPixelStep, heightPixelStep);
        vec2 leftVec = vec2(-widthPixelStep, 0.0);
        vec2 leftUpVec = vec2(-widthPixelStep, -heightPixelStep);
        for (float i = 1.0; i <= radius; i = i + 1.0) {
            vec2 u = texCoord + upVec * i;
            if (checkTextureCoord(u)) {
                vec4 c = texture(inputTexture, u);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 ur = texCoord + upRightVec * i;
            if (checkTextureCoord(ur)) {
                vec4 c = texture(inputTexture, ur);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 r = texCoord + rightVec * i;
            if (checkTextureCoord(r)) {
                vec4 c = texture(inputTexture, r);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 rd = texCoord + rightDownVec * i;
            if (checkTextureCoord(rd)) {
                vec4 c = texture(inputTexture, rd);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 d = texCoord + downVec * i;
            if (checkTextureCoord(d)) {
                vec4 c = texture(inputTexture, d);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 dl = texCoord + downLeftVec * i;
            if (checkTextureCoord(dl)) {
                vec4 c = texture(inputTexture, dl);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 l = texCoord + leftVec * i;
            if (checkTextureCoord(l)) {
                vec4 c = texture(inputTexture, l);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }

            vec2 lu = texCoord + leftUpVec * i;
            if (checkTextureCoord(lu)) {
                vec4 c = texture(inputTexture, lu);
                if (isSkinColor(c)) {
                    centerColor = centerColor + c;
                    colorSize = colorSize + 1.0;
                }
            }
        }
        return centerColor / colorSize;
    } else {
        return centerColor;
    }
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

// 磨皮
uniform int skinSmoothSwitch;
uniform float textureWidthPixelStep;
uniform float textureHeightPixelStep;

void main() {
    // 大眼
    vec2 fixedCoord = enlargeOval(TexCoord, leftEyeCenter, leftEyeA, leftEyeB, 20.0);
    fixedCoord = enlargeOval(fixedCoord, rightEyeCenter, rightEyeA, rightEyeB, 20.0);

    // 瘦脸
    fixedCoord = stretch(fixedCoord, leftFaceThinCenter, stretchCenter, thinRadius, 40.0);
    fixedCoord = stretch(fixedCoord, rightFaceThinCenter, stretchCenter, thinRadius, 40.0);

    vec4 outputColor = texture(Texture, fixedCoord);

    // 磨皮
    if (skinSmoothSwitch == 1) {
        vec4 smoothColor = skinSmooth(Texture, fixedCoord, textureWidthPixelStep, textureHeightPixelStep, 8.0);
        outputColor = mix(outputColor, smoothColor, 0.5);
    }

    // 美白
    if (whiteningSwitch == 1) {
        outputColor = vec4(whitening(vec3(outputColor.x, outputColor.y, outputColor.z), 2.5), 1.0);
    }
    FragColor = outputColor;
}