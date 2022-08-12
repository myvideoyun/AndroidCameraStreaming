package com.myvideoyun.livestream.tool.gpu;

import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.GL_TRUE;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetProgramInfoLog;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUseProgram;
import static com.myvideoyun.livestream.tool.gpu.MVYGPUImageConstants.TAG;

import android.util.Log;

public class MVYGLProgram {

    private int program;
    private int vertShader;
    private int fragShader;

    public MVYGLProgram(String vShaderString, String fShaderString) {
        vertShader = compileShader(GL_VERTEX_SHADER, vShaderString);

        fragShader = compileShader(GL_FRAGMENT_SHADER, fShaderString);

        program = glCreateProgram();

        glAttachShader(program, vertShader);
        glAttachShader(program, fragShader);
    }

    private int compileShader(int type, String shaderString) {
        int shader = glCreateShader(type);

        if (shader != 0) {
            glShaderSource(shader, shaderString);
            glCompileShader(shader);

            int[] status = new int[1];
            glGetShaderiv(shader, GL_COMPILE_STATUS, status,0);

            if (status[0] == 0) {
                Log.d(TAG, "shader complie error : " + glGetShaderInfoLog(shader));
                glDeleteShader(shader);
                shader = 0;
            }
        }

        return shader;
    }

    public int attributeIndex(String attributeName) {
        return glGetAttribLocation(program, attributeName);
    }

    public int uniformIndex(String uniformName) {
        return glGetUniformLocation(program, uniformName);
    }

    public boolean link() {
        glLinkProgram(program);

        int[] status = new int[1];

        glGetProgramiv(program, GL_LINK_STATUS, status, 0);
        if (status[0] != GL_TRUE) {
            Log.d(TAG, "link program error : " +glGetProgramInfoLog(program));
            if (vertShader > 0) {
                glDeleteShader(vertShader);
                vertShader = 0;
            }
            if (fragShader > 0) {
                glDeleteShader(fragShader);
                fragShader = 0;
            }

            return false;
        }

        return true;
    }

    public void use() {
        glUseProgram(program);
    }

    public void destroy() {
        if (vertShader > 0) {
            glDeleteShader(vertShader);
            vertShader = 0;
        }
        if (fragShader > 0) {
            glDeleteShader(fragShader);
            fragShader = 0;
        }
        if (program > 0) {
            glDeleteProgram(program);
            program = 0;
        }
    }
}

