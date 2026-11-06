package com.blackice.bc1.editor;

import android.content.Context;
import android.opengl.GLES30;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.*;
import androidx.media3.effect.*;

/** Media3 ping-pongs its pooled output textures between these ordered passes. */
@androidx.annotation.OptIn(markerClass=UnstableApi.class)
final class StackEffect implements GlEffect {
    final EditProject.Placement placement;final EffectInstance effect;
    StackEffect(EditProject.Placement p,EffectInstance e){placement=p;effect=e;}
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean hdr)throws VideoFrameProcessingException{return new Program(context,hdr);}
    private static String read(java.io.InputStream in)throws java.io.IOException{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    final class Program extends BaseGlShaderProgram {
        final GlProgram program;
        Program(Context c,boolean hdr)throws VideoFrameProcessingException{
            super(hdr,1);
            try{
                String vertex,fragment;
                try(java.io.InputStream in=c.getAssets().open("shaders/video.vert")){vertex=read(in);}
                try(java.io.InputStream in=c.getAssets().open("shaders/video.frag")){fragment=read(in);}
                program=new GlProgram(vertex,com.blackice.bc1.core.LayerShader.effectPass(fragment,effect.type));program.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4);
            }catch(java.io.IOException|GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
        @Override public Size configure(int w,int h){if(program.getUniformLocation("uTexel")>=0)program.setFloatsUniform("uTexel",new float[]{1f/w,1f/h});if(program.getUniformLocation("uAspect")>=0)program.setFloatUniform("uAspect",(float)w/h);return new Size(w,h);}
        @Override public void drawFrame(int texture,long timeUs)throws VideoFrameProcessingException{
            long source=com.blackice.bc1.core.RenderPlan.sourceTime(placement,timeUs/1000);
            try{program.use();program.setSamplerTexIdUniform("uInput",texture,0);if(program.getUniformLocation("uEffect")>=0)program.setIntUniform("uEffect",effect.type);if(program.getUniformLocation("uIntensity")>=0)program.setFloatUniform("uIntensity",effect.strength(source));if(program.getUniformLocation("uParams")>=0)program.setFloatsUniform("uParams",effect.resolved());if(program.getUniformLocation("uTime")>=0)program.setFloatUniform("uTime",source/1000f);program.bindAttributesAndUniforms();GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError();}catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e,timeUs);}
        }
        @Override public void release()throws VideoFrameProcessingException{super.release();try{program.delete();}catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}}
    }
}
