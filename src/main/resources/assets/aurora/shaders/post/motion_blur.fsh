#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float Strength;
};

layout(location = 0) out vec4 fragColor;

// Blends the new frame with the previous result. The previous target starts out cleared to
// transparent, so the first frame after switching on is shown as it is instead of fading in from black.
void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 previous = texture(PrevSampler, texCoord);
    float weight = Strength * previous.a;
    fragColor = vec4(mix(current.rgb, previous.rgb, weight), 1.0);
}
