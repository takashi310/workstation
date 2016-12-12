// vertex shader to support dynamic selection of presented colors.
#version 120
uniform int hasMaskingTexture;
// 0th
attribute vec4 vertexAttribute;
// 1st
attribute vec4 texCoordAttribute;

void main(void)
{
    gl_FrontColor = gl_Color;
    gl_TexCoord[0] = texCoordAttribute;
    if (hasMaskingTexture > 0)
    {
        gl_TexCoord[1] = texCoordAttribute;
    }
    gl_Position = gl_ModelViewProjectionMatrix * vertexAttribute;
}