attribute vec4 aPosition;
attribute vec4 aTextureCoord;
attribute vec4 aTextureObjectCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uObjectMatrix;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = aTextureCoord.xy;
  vTextureObjectCoord = (uObjectMatrix * aTextureObjectCoord).xy;
}