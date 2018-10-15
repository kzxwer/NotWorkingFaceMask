package com.android.facemask.camera;

public class GLSepiaFilter extends GLDrawer2D{
    public static final String SEPIA_FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "\n" +
            "varying vec2 textureCoordinate;\n" +
            "\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "\n" +
            "const highp vec3 W = vec3(0.299, 0.587, 0.114);\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "  lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "  float luminance = dot(textureColor.rgb, W);\n" +
            "\n" +
            "  gl_FragColor = vec4(luminance*vec3(1.0,0.8,0.4), textureColor.a);\n" +
            "}";

    public GLSepiaFilter() {

        super(NO_FILTER_VERTEX_SHADER, SEPIA_FRAGMENT_SHADER);
    }
}
