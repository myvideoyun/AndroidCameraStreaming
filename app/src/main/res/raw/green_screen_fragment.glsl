precision mediump float;

uniform sampler2D uSampler;
uniform sampler2D uObject;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

void main() {
   // lookup the color of the texel corresponding to the fragment being
   // generated while rendering a triangle
   lowp vec4 tempColor = texture2D(uSampler, vTextureCoord);

   // Calculate the average intensity of the texel's red and blue components
   lowp float rbAverage = tempColor.r * 0.5 + tempColor.b * 0.5;

   // Calculate the difference between the green element intensity and the
   // average of red and blue intensities
   lowp float gDelta = tempColor.g - rbAverage;

   // If the green intensity is greater than the average of red and blue
   // intensities, calculate a transparency value in the range 0.0 to 1.0
   // based on how much more intense the green element is
   tempColor.a = 1.0 - smoothstep(0.0, 0.25, gDelta);

   // Use the cube of the of the transparency value. That way, a fragment that
   // is partially translucent becomes even more translucent. This sharpens
   // the final result by avoiding almost but not quite opaque fragments that
   // tend to form halos at color boundaries.
   tempColor.a = tempColor.a * tempColor.a * tempColor.a;

   vec4 objectPixel = texture2D(uObject, vTextureObjectCoord);

   gl_FragColor = mix(objectPixel, tempColor, tempColor.a);
}