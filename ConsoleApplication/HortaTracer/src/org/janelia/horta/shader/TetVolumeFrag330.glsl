#version 430 core

/**
 * Tetrahdedral volume rendering fragment shader.
 */

/* 
 * Licensed under the Janelia Farm Research Campus Software Copyright 1.1
 * 
 * Copyright (c) 2014, Howard Hughes Medical Institute, All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *     1. Redistributions of source code must retain the above copyright notice, 
 *        this list of conditions and the following disclaimer.
 *     2. Redistributions in binary form must reproduce the above copyright 
 *        notice, this list of conditions and the following disclaimer in the 
 *        documentation and/or other materials provided with the distribution.
 *     3. Neither the name of the Howard Hughes Medical Institute nor the names 
 *        of its contributors may be used to endorse or promote products derived 
 *        from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, ANY 
 * IMPLIED WARRANTIES OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * REASONABLE ROYALTIES; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Optimize for a certain maximum number of color channels
// Input image channels
#define CHANNEL_VEC vec2
// Total output channels, including synthetic channels constructed here, in the fragment shader
#define OUTPUT_CHANNEL_VEC vec3

// three-dimensional raster volume of intensities through which we will cast view rays
layout(binding = 0) uniform sampler3D volumeTexture;

// palette used to compose "hot" color transfer functions from hue/saturation bases
layout(binding = 1) uniform sampler2D colorMapTexture;

// per-channel intensity transfer function
layout(location = 3) uniform OUTPUT_CHANNEL_VEC opacityFunctionMin = OUTPUT_CHANNEL_VEC(0);
layout(location = 4) uniform OUTPUT_CHANNEL_VEC opacityFunctionMax = OUTPUT_CHANNEL_VEC(1);
layout(location = 5) uniform OUTPUT_CHANNEL_VEC opacityFunctionGamma = OUTPUT_CHANNEL_VEC(1);

layout(location = 6) uniform OUTPUT_CHANNEL_VEC channelVisibilityMask = OUTPUT_CHANNEL_VEC(1);

// use a linear combination of input color channels to create one channel used for neuron tracing
// used for computing "core" depth and intensity
// Parameters for channel unmixing
layout(location = 7) uniform vec4 unmixMinScale = vec4(0.0, 0.0, 0.5, 0.5);

// Parameters for reconstruction of original 16-bit channel intensities
layout(location = 8) uniform CHANNEL_VEC channelIntensityGamma = CHANNEL_VEC(1);
layout(location = 9) uniform CHANNEL_VEC channelIntensityScale = CHANNEL_VEC(1);
layout(location = 10) uniform CHANNEL_VEC channelIntensityOffset = CHANNEL_VEC(0);

// Channel colors
layout(location = 11) uniform OUTPUT_CHANNEL_VEC channelColorHue = OUTPUT_CHANNEL_VEC(120, 300, 210);
layout(location = 12) uniform OUTPUT_CHANNEL_VEC channelColorSaturation = OUTPUT_CHANNEL_VEC(1);

in vec3 fragTexCoord; // texture coordinate at back face of tetrahedron
flat in vec3 cameraPosInTexCoord; // texture coordinate at view eye location
flat in mat4 tetPlanesInTexCoord; // clip plane equations at all 4 faces of tetrhedron
flat in vec4 zNearPlaneInTexCoord; // clip plane equation at near view slab plane
flat in vec4 zFarPlaneInTexCoord; // plane equation for far z-clip plane

layout(location = 0) out vec4 fragColor; // store final output color in the usual way
layout(location = 1) out vec2 coreDepth; // also store intensity and relative depth of the most prominent point along the ray, in a secondary render target

// We will be building up an intensity integrated along the view ray.
struct IntegratedIntensity
{
    CHANNEL_VEC intensity;
    float tracing_intensity; // integration of tracing intensity
    float opacity;
};

float max_element(in vec2 v) {
    return max(v.r, v.g);
}

vec2 rampstep(vec2 edge0, vec2 edge1, vec2 x) {
    return clamp((x - edge0)/(edge1 - edge0), 0.0, 1.0);
}

vec3 rampstep(vec3 edge0, vec3 edge1, vec3 x) {
    return clamp((x - edge0)/(edge1 - edge0), 0.0, 1.0);
}

// Unmixes one voxel from two channels to create a third, synthetic channel voxel
float tracing_channel_from_raw(CHANNEL_VEC raw_channels) {
    vec2 raw = raw_channels.xy;
    // Avoid extreme differences at low input intensity
    if (raw.x < 0.99 * unmixMinScale.x) return 0; // below threshold -> no data
    if (raw.y < 0.99 * unmixMinScale.y) return 0;
    // scale the two channels and combine
    float result = dot(raw_channels.xy, unmixMinScale.zw);
    // adjust the minimum to roughly match one of the input channels
    float offset = -dot(unmixMinScale.xy, unmixMinScale.zw); // move average black level to zero
    // restore black level to match one of the inputs
    if (unmixMinScale.z >= unmixMinScale.w) offset += unmixMinScale.x; // use black level from channel 1
    else offset += unmixMinScale.y; // use black level from channel 2
    result += offset; // allow room to explore negative differences
    result = clamp(result, 0, 1);
    return result;
}

// For one-channel, blending is trivial
float blend_channel_opacities(float opacity) {
    return opacity;
}

// Opacity values must be between zero and one
float blend_channel_opacities(vec2 opacities) {
    float a = opacities.x;
    float b = opacities.y;
    // return a + b - a*b; // Good compromise between MAX and SUM
    return max(a, b);
}

// Opacity values must be between zero and one
float blend_channel_opacities(vec3 opacities) {
    float a = opacities.x;
    float b = opacities.y;
    float c = opacities.z;
    // return a + b + c - a*b - a*c - b*c + a*b*c; // Good compromise between MAX and SUM
    return max(max(a, b), c);
}

float opacity_for_intensities(in OUTPUT_CHANNEL_VEC intensity) 
{
    // Use brightness model to modulate opacity
    OUTPUT_CHANNEL_VEC rescaled = rampstep(opacityFunctionMin, opacityFunctionMax, intensity);
    rescaled = pow(rescaled, opacityFunctionGamma);
    rescaled *= channelVisibilityMask;
    // TODO: Is max across channels OK here? Or should we use something intermediate between MAX and SUM?
    return blend_channel_opacities(rescaled);
}

vec3 hot_color_for_hue_intensity(in float hue, in float saturation, in float intensity) {
    // hue
    float h = fract(2.0 + hue / 360.0); // normalize 360 degrees to range 0.0-1.0
    float s_sat = (0.7500 * h + 0.1875); // restrict to rainbow region of color map
    const float s_gray = 0.0625; // location of grayscale stripe
    // intensity
    float i = pow(intensity, 2.2); // crude gamma correction of sRGB texture
    float r = (0.93750 * i + 0.03125); // dark to light, terminating at pixel centers
    vec3 color_sat = texture(colorMapTexture, vec2(r, s_sat)).rgb;
    vec3 color_gray = texture(colorMapTexture, vec2(r, s_gray)).rgb;
    return mix(color_gray, color_sat, saturation);
}

vec4 rgba_for_scaled_intensities(in vec2 c, in float opacity) {
    // return vec4(c.grg, opacity); // green/magenta

    // hot color map
    vec3 ch1 = hot_color_for_hue_intensity(channelColorHue.r, channelColorSaturation.r, c.r); // green
    vec3 ch2 = hot_color_for_hue_intensity(channelColorHue.g, channelColorSaturation.g, c.g); // magenta
    const vec3 ones = vec3(1);
    vec3 combined = ones - (ones - ch1)*(ones - ch2); // compromise between sum and max
    return vec4(combined, opacity);
}

vec4 rgba_for_scaled_intensities(in vec3 c, in float opacity) {
    // return vec4(c.grg, opacity); // green/magenta

    // hot color map
    vec3 ch1 = hot_color_for_hue_intensity(channelColorHue.r, channelColorSaturation.r, c.r); // green
    vec3 ch2 = hot_color_for_hue_intensity(channelColorHue.g, channelColorSaturation.g, c.g); // magenta
    vec3 ch3 = hot_color_for_hue_intensity(channelColorHue.b, channelColorSaturation.b, c.b); // aqua blue
    const vec3 ones = vec3(1);
    vec3 combined = ones - (ones - ch1)*(ones - ch2)*(ones - ch3); // compromise between sum and max
    return vec4(combined, opacity);
}

vec4 rgba_for_intensities(IntegratedIntensity i) {
    // Use brightness model to modulate opacity
    OUTPUT_CHANNEL_VEC v = OUTPUT_CHANNEL_VEC(i.intensity, i.tracing_intensity);
    OUTPUT_CHANNEL_VEC rescaled = rampstep(opacityFunctionMin, opacityFunctionMax, v);
    rescaled = pow(rescaled, opacityFunctionGamma);
    rescaled *= channelVisibilityMask;
    return rgba_for_scaled_intensities(rescaled, i.opacity);
}

float intersectRayAndPlane(
        in vec3 rayStart, in vec3 rayDirection, 
        in vec4 plane)
{
    float intersection = -(dot(rayStart, plane.xyz) + plane.w) / dot(plane.xyz, rayDirection);
    return intersection;
}

// Return ray parameter where ray intersects plane
void clipRayToPlane(
        in vec3 rayStart, in vec3 rayDirection, 
        in vec4 plane,
        inout float begin, // current ray start parameter
        inout float end) // current ray end parameter
{
    float direction = dot(plane.xyz, rayDirection);
    if (direction == 0)
        return; // ray is parallel to plane
    float intersection = intersectRayAndPlane(rayStart, rayDirection, plane);
    if (direction > 0) // plane normal is along ray direction
        begin = max(begin, intersection);
    else // plane normal is opposite to ray direction
        end = min(end, intersection);
}

float advance_to_voxel_edge(
        in float previousEdge,
        in vec3 rayOriginInTexels,
        in vec3 rayDirectionInTexels,
        in vec3 rayBoxCorner,
        in vec3 forwardMask,
        in float texelsPerRay)
{
    // Units of ray parameter, t, are roughly texels
    const float minStep = 0.020 / texelsPerRay;

    // Advance ray by at least minStep, to avoid getting stuck in tiny corners
    float t = previousEdge + minStep;
    vec3 x0 = rayOriginInTexels;
    vec3 x1 = rayDirectionInTexels; 
    vec3 currentTexelPos = x0 + t*x1; // apply ray equation to find new voxel

    // Advance ray to next voxel edge.
    // For NEAREST filter, advance to midplanes between voxel centers.
    // For TRILINEAR and TRICUBIC filters, advance to planes connecing voxel centers.
    vec3 currentTexel = floor(currentTexelPos + rayBoxCorner) 
            - rayBoxCorner;

    // Three out of six total voxel edges represent forward progress
    vec3 candidateEdges = currentTexel + forwardMask;
    // Ray trace to three planar voxel edges at once.
    vec3 candidateSteps = -(x0 - candidateEdges)/x1;
    // Choose the closest voxel edge.
    float nextEdge = min(candidateSteps.x, min(candidateSteps.y, candidateSteps.z));
    // Advance ray by at least minStep, to avoid getting stuck in tiny corners
    // Next line should be unneccessary, but prevents (sporadic?) driver crash
    nextEdge = max(nextEdge, previousEdge + minStep);
    return nextEdge;
}

// Nearest-neighbor filtering
IntegratedIntensity sample_nearest_neighbor(in vec3 texCoord, in int levelOfDetail)
{
    CHANNEL_VEC intensity = CHANNEL_VEC(textureLod(volumeTexture, texCoord, levelOfDetail));

    // Reconstruct original 16-bit intensity
    CHANNEL_VEC intensity2 = pow(intensity, channelIntensityGamma);
    intensity2 *= channelIntensityScale;
    intensity2 += channelIntensityOffset;
    // CHANNEL_VEC intensity2 = pow(intensity, CHANNEL_VEC(2.0));
    // intensity2 *= CHANNEL_VEC(0.05);
    // intensity2 += CHANNEL_VEC(0.2); // UNLESS original was zero
    if (intensity.x <= 0) intensity2.x = 0; // TODO: there has to be a neater way...
    if (intensity.y <= 0) intensity2.y = 0;
    if (intensity.x >= 1) intensity2.x = mix(intensity2.x, 1.0, 0.5); // TODO: there has to be a neater way...
    if (intensity.y >= 1) intensity2.y = mix(intensity2.x, 1.0, 0.5);

    float tracing = tracing_channel_from_raw(intensity2);
    float opacity = opacity_for_intensities(OUTPUT_CHANNEL_VEC(intensity2, tracing));
    return IntegratedIntensity(intensity2, tracing, opacity);
}

// Maximum intensity projection
IntegratedIntensity integrate_max_intensity(
        in IntegratedIntensity front, 
        in IntegratedIntensity back)
{
    CHANNEL_VEC intensity = max(front.intensity, back.intensity);
    float tracing = max(front.tracing_intensity, back.tracing_intensity);
    float opacity = max(front.opacity, back.opacity);
    return IntegratedIntensity(intensity, tracing, opacity);
}

// Occluding projection
IntegratedIntensity integrate_occluding(in IntegratedIntensity front, in IntegratedIntensity back) 
{
    float opacity = 1.0 - (1.0 - front.opacity) * (1.0 - back.opacity);
    float kf = front.opacity;
    float kb = 1.0 - front.opacity;
    CHANNEL_VEC bi = back.intensity * kb;
    CHANNEL_VEC fi = front.intensity * kf;
    // This is the visible VIEWER tracing channel, so apply occluding here too.
    float tracing = clamp(front.tracing_intensity * kf + back.tracing_intensity * kb, 0, 1);
    return IntegratedIntensity(clamp(bi + fi, 0, 1), tracing, opacity);
}


void main() 
{
    // Ray parameters
    vec3 x0 = cameraPosInTexCoord; // origin
    vec3 x1 = fragTexCoord - x0; // direction

    // Clip near and far ray bounds
    float minRay = 0; // eye position
    float maxRay = 1; // back face fragment location

    // Clip ray bounds to tetrahedral faces
    clipRayToPlane(x0, x1, tetPlanesInTexCoord[0], minRay, maxRay);
    clipRayToPlane(x0, x1, tetPlanesInTexCoord[1], minRay, maxRay);
    clipRayToPlane(x0, x1, tetPlanesInTexCoord[2], minRay, maxRay);
    clipRayToPlane(x0, x1, tetPlanesInTexCoord[3], minRay, maxRay);
    
    clipRayToPlane(x0, x1, zNearPlaneInTexCoord, minRay, maxRay);
    clipRayToPlane(x0, x1, zFarPlaneInTexCoord, minRay, maxRay);

    if (minRay > maxRay) discard; // draw nothing if ray is completely clipped away

    vec3 frontTexCoord = x0 + minRay * x1;
    vec3 rearTexCoord = x0 + maxRay * x1;

    // Set up for texel-by-texel ray marching
    const int levelOfDetail = 0; // TODO: adjust dynamically
    ivec3 texelsPerVolume = textureSize(volumeTexture, levelOfDetail);

    vec3 rayOriginInTexels = x0 * texelsPerVolume;
    vec3 rayDirectionInTexels = x1 * texelsPerVolume;
    float texelsPerRay = length(rayDirectionInTexels);
    const vec3 rayBoxCorner = vec3(0, 0, 0); // nearest neighbor
    vec3 forwardMask = ceil(normalize(rayDirectionInTexels) * 0.99); // each component is now 0 or 1

    vec3 frontTexel = frontTexCoord * texelsPerVolume;
    vec3 rearTexel = rearTexCoord * texelsPerVolume;

    // Cast ray through volume
    IntegratedIntensity intensity = IntegratedIntensity(CHANNEL_VEC(0), 0, 0);
    bool rayIsFinished = false;
    float t0 = minRay;
    float coreParam = mix(minRay, maxRay, 0.5);
    float coreIntensity = -1.0;
    for (int s = 0; s < 1000; ++s) 
    {
        float t1 = advance_to_voxel_edge(t0, 
                rayOriginInTexels, rayDirectionInTexels,
                rayBoxCorner, forwardMask, 
                texelsPerRay);
        if (t1 >= maxRay) {
            t1 = maxRay;
            rayIsFinished = true;
        }
        float t = mix(t0, t1, 0.5);
        vec3 texel = rayOriginInTexels + t * rayDirectionInTexels;
        vec3 texCoord = texel / texelsPerVolume;

        IntegratedIntensity rearIntensity = sample_nearest_neighbor(texCoord, levelOfDetail); // intentionally downsampled
        intensity = 
                integrate_max_intensity(intensity, rearIntensity);
                // integrate_occluding(intensity, rearIntensity);

        float tracingIntensity = 
                tracing_channel_from_raw(rearIntensity.intensity);
                // dot(rearIntensity.intensity, tracingChannelMask);
        if (tracingIntensity >= coreIntensity) { // MIP criterion
            coreIntensity = tracingIntensity;
            coreParam = t;
        }

        // Terminate early if we hit an opaque surface
        if (intensity.opacity > 0.99) {
            rayIsFinished = true;
        }

        if (rayIsFinished)
            break;
        t0 = t1;
    }

    if (intensity.opacity < 0.01) { 
        discard; // terminate early if there is nothing to show
    }

    // Secondary render target stores 16-bit core intensity, plus relative depth
    float slabMin = intersectRayAndPlane(x0, x1, zNearPlaneInTexCoord);
    float slabMax = intersectRayAndPlane(x0, x1, zFarPlaneInTexCoord);
    float relativeDepth = (coreParam - slabMin) / (slabMax - slabMin);
    // When rendering multiple blocks, we need to store a relative-depth value 
    // that could win a GL_MAX blend contest.
    //   1) pad the most significant bits with the opacity, so the most
    //      opaque ray segment wins. Not perfect, but should work pretty
    //      well in most sparse rendering contexts.
    //   2) reverse the sense of the relative depth, so in case of an
    //      opacity tie, the NEARER ray segment wins.
    // Use a floating point render target, because integer targets won't blend.
    // Pack the opacity into the first 7 bits of a 32-bit float mantissa
    uint opacityInt = clamp(uint(0x7f * intensity.opacity), 0, 0x7f); // 7 bits of opacity, range 0-127
    relativeDepth = 1.0 - relativeDepth; // In case of equal opacity, we want NEAR depths to beat FAR depths in a GL_MAX comparison
    // Keep depth strictly fractional, for unambiguous packing with integer opacity
    relativeDepth = clamp(relativeDepth, 0.0, 0.999);
    float opacityDepth = opacityInt + relativeDepth;
    coreIntensity = clamp(coreIntensity, 0, 1);
    coreDepth = vec2(coreIntensity, opacityDepth); // populates both channels of secondary render target

    // Primary render target stores final blended RGBA color
    fragColor = rgba_for_intensities(intensity);
}