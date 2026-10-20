package com.blackice.bc1;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small monoline controls matching the approved V1 editor reference. */
final class V1Icon extends Drawable {
    private final String name;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    V1Icon(String name,int color){this.name=name;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.6f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
    private void line(float x,float y,float xx,float yy){path.moveTo(x,y);path.lineTo(xx,yy);}
    @Override public void draw(Canvas canvas){
        canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);path.reset();boolean fill=false;
        switch(name){
            case "Cube":path.moveTo(12,2);path.lineTo(22,7);path.lineTo(22,17);path.lineTo(12,23);path.lineTo(2,17);path.lineTo(2,7);path.close();line(2,7,12,13);line(22,7,12,13);line(12,13,12,23);line(12,2,12,13);break;
            case "Media":path.addRect(3,5,21,20,Path.Direction.CW);line(3,5,9,5);line(9,5,11,2);line(11,2,17,2);line(17,2,19,5);line(5,16,10,11);line(10,11,14,15);line(14,15,17,12);line(17,12,20,16);break;
            case "Edit":case "Split":path.addCircle(6,17,3,Path.Direction.CW);path.addCircle(18,17,3,Path.Direction.CW);line(8,15,20,3);line(16,15,4,3);break;
            case "Effects":path.moveTo(12,2);path.lineTo(15,9);path.lineTo(22,12);path.lineTo(15,15);path.lineTo(12,22);path.lineTo(9,15);path.lineTo(2,12);path.lineTo(9,9);path.close();break;
            case "Layers":path.moveTo(12,3);path.lineTo(22,8);path.lineTo(12,13);path.lineTo(2,8);path.close();path.moveTo(2,13);path.lineTo(12,18);path.lineTo(22,13);path.moveTo(2,18);path.lineTo(12,23);path.lineTo(22,18);break;
            case "Setup":line(3,6,21,6);line(3,12,21,12);line(3,18,21,18);path.addCircle(8,6,2,Path.Direction.CW);path.addCircle(16,12,2,Path.Direction.CW);path.addCircle(10,18,2,Path.Direction.CW);break;
            case "Time":path.addCircle(12,12,9,Path.Direction.CW);line(12,5,12,12);line(12,12,17,15);break;
            case "TrimStart":line(8,3,8,21);line(12,6,12,18);line(12,6,21,6);line(12,18,21,18);line(2,7,4,7);line(2,12,4,12);line(2,17,4,17);break;
            case "TrimEnd":line(16,3,16,21);line(12,6,12,18);line(3,6,12,6);line(3,18,12,18);line(20,7,22,7);line(20,12,22,12);line(20,17,22,17);break;
            case "Trim":line(5,3,5,21);line(19,3,19,21);line(9,6,9,18);line(9,6,15,6);line(9,18,15,18);break;
            case "Duplicate":path.addRect(8,8,21,21,Path.Direction.CW);path.moveTo(16,4);path.lineTo(3,4);path.lineTo(3,17);break;
            case "Opacity":path.addCircle(12,12,9,Path.Direction.CW);line(12,3,12,21);for(int y=6;y<=18;y+=3)line(12,y,18,y);break;
            case "Chroma Key":path.addRoundRect(3,3,21,21,3,3,Path.Direction.CW);path.addCircle(12,9,3,Path.Direction.CW);path.moveTo(7,20);path.lineTo(8,15);path.lineTo(16,15);path.lineTo(17,20);break;
            case "Extract Audio":path.moveTo(7,18);path.lineTo(7,5);path.lineTo(16,3);path.lineTo(16,12);path.addOval(2,16,7,21,Path.Direction.CW);line(13,18,22,18);line(19,15,22,18);line(22,18,19,21);break;
            case "Volume":path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,4);path.lineTo(12,20);path.lineTo(7,15);path.lineTo(3,15);path.close();path.addArc(10,6,20,18,-60,120);path.addArc(8,2,25,22,-55,110);break;
            case "Delete":line(3,6,21,6);line(9,3,15,3);path.moveTo(6,6);path.lineTo(7,21);path.lineTo(17,21);path.lineTo(18,6);line(10,10,10,17);line(14,10,14,17);break;
            case "Export":line(12,3,12,15);line(12,3,7,8);line(12,3,17,8);path.moveTo(4,12);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,12);break;
            case "Captions":path.addRoundRect(2,5,22,19,2,2,Path.Direction.CW);line(5,10,19,10);line(5,14,11,14);line(14,14,19,14);break;
            case "Save":path.addRect(4,3,21,21,Path.Direction.CW);path.addRect(8,3,17,9,Path.Direction.CW);path.addRect(8,14,17,21,Path.Direction.CW);break;
            case "Undo":line(4,5,4,12);line(4,12,11,12);path.moveTo(4,12);path.cubicTo(6,3,21,5,20,14);path.cubicTo(20,18,16,20,12,20);break;
            case "Redo":line(20,5,20,12);line(20,12,13,12);path.moveTo(20,12);path.cubicTo(18,3,3,5,4,14);path.cubicTo(4,18,8,20,12,20);break;
            case "Add":case "+ Add":line(12,4,12,20);line(4,12,20,12);break;
            case "More":path.addCircle(12,5,1.2f,Path.Direction.CW);path.addCircle(12,12,1.2f,Path.Direction.CW);path.addCircle(12,19,1.2f,Path.Direction.CW);break;
            case "Close":line(5,5,19,19);line(19,5,5,19);break;
            case "Back":line(15,4,7,12);line(7,12,15,20);break;
            case "Eye":path.moveTo(2,12);path.quadTo(12,2,22,12);path.quadTo(12,22,2,12);path.addCircle(12,12,3,Path.Direction.CW);break;
            case "Hidden":path.moveTo(2,12);path.quadTo(12,2,22,12);path.quadTo(12,22,2,12);line(4,20,20,4);break;
            case "Lock":path.addRoundRect(5,11,19,21,2,2,Path.Direction.CW);path.moveTo(8,11);path.lineTo(8,7);path.cubicTo(8,2,16,2,16,7);path.lineTo(16,11);break;
            case "Unlock":path.addRoundRect(5,11,19,21,2,2,Path.Direction.CW);path.moveTo(8,11);path.lineTo(8,7);path.cubicTo(8,2,16,2,16,6);break;
            case "Solo":path.addCircle(12,12,9,Path.Direction.CW);path.moveTo(15,8);path.cubicTo(13,6,9,7,9,9.5f);path.cubicTo(9,12,15,12,15,14.5f);path.cubicTo(15,17,11,18,9,16);break;
            case "Mute":path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,4);path.lineTo(12,20);path.lineTo(7,15);path.lineTo(3,15);path.close();line(15,9,21,15);line(21,9,15,15);break;
            case "Handle":for(int y=7;y<=17;y+=5){line(6,y,18,y);}break;
            case "Keyframe":path.moveTo(12,3);path.lineTo(21,12);path.lineTo(12,21);path.lineTo(3,12);path.close();break;
            case "Text":case "Edit Text":line(4,5,20,5);line(12,5,12,20);line(9,20,15,20);break;
            case "Shapes":path.addRect(3,10,13,20,Path.Direction.CW);path.addCircle(16,8,5,Path.Direction.CW);break;
            case "Audio":path.moveTo(9,18);path.lineTo(9,5);path.lineTo(20,3);path.lineTo(20,16);path.addCircle(6.5f,18,2.5f,Path.Direction.CW);path.addCircle(17.5f,16,2.5f,Path.Direction.CW);break;
            case "Saved":path.moveTo(6,3);path.lineTo(18,3);path.lineTo(18,21);path.lineTo(12,16);path.lineTo(6,21);path.close();break;
            case "Transform":case "Move & Transform":line(12,2,12,22);line(2,12,22,12);line(12,2,9,5);line(12,2,15,5);line(22,12,19,9);line(22,12,19,15);line(12,22,9,19);line(12,22,15,19);line(2,12,5,9);line(2,12,5,15);break;
            case "Color":case "Color & Fill":path.addCircle(12,12,9,Path.Direction.CW);path.addCircle(9,9,1.5f,Path.Direction.CW);path.addCircle(15,9,1.5f,Path.Direction.CW);path.addCircle(9,15,1.5f,Path.Direction.CW);break;
            case "Border & Shadow":path.addRect(4,4,16,16,Path.Direction.CW);line(20,8,20,20);line(8,20,20,20);break;
            case "Blending & Opacity":path.addCircle(9,12,6,Path.Direction.CW);path.addCircle(15,12,6,Path.Direction.CW);break;
            case "Presets":path.addRect(4,4,10,10,Path.Direction.CW);path.addRect(14,4,20,10,Path.Direction.CW);path.addRect(4,14,10,20,Path.Direction.CW);path.addRect(14,14,20,20,Path.Direction.CW);break;
            case "Speed":path.addArc(3,5,21,23,180,180);line(12,14,17,8);break;
            case "Slip":line(3,12,21,12);line(3,12,7,8);line(3,12,7,16);line(21,12,17,8);line(21,12,17,16);break;
            case "Fade In":line(3,20,21,4);line(3,20,21,20);break;
            case "Fade Out":line(3,4,21,20);line(3,20,21,20);break;
            case "Settings":path.addCircle(12,12,3,Path.Direction.CW);path.addCircle(12,12,8,Path.Direction.CW);for(int i=0;i<8;i++){double r=Math.PI*i/4;line(12+8*(float)Math.cos(r),12+8*(float)Math.sin(r),12+10.5f*(float)Math.cos(r),12+10.5f*(float)Math.sin(r));}break;
            case "SkipStart":line(5,5,5,19);path.moveTo(19,5);path.lineTo(8,12);path.lineTo(19,19);path.close();break;
            case "SkipEnd":line(19,5,19,19);path.moveTo(5,5);path.lineTo(16,12);path.lineTo(5,19);path.close();break;
            case "StepBack":path.moveTo(12,6);path.lineTo(4,12);path.lineTo(12,18);path.close();path.moveTo(20,6);path.lineTo(12,12);path.lineTo(20,18);path.close();break;
            case "StepForward":path.moveTo(4,6);path.lineTo(12,12);path.lineTo(4,18);path.close();path.moveTo(12,6);path.lineTo(20,12);path.lineTo(12,18);path.close();break;
            case "Play":fill=true;path.moveTo(6,4);path.lineTo(20,12);path.lineTo(6,20);path.close();break;
            case "Pause":fill=true;path.addRoundRect(6,4,10,20,1,1,Path.Direction.CW);path.addRoundRect(14,4,18,20,1,1,Path.Direction.CW);break;
            case "Fullscreen":line(3,9,3,3);line(3,3,9,3);line(15,3,21,3);line(21,3,21,9);line(21,15,21,21);line(21,21,15,21);line(9,21,3,21);line(3,21,3,15);break;
            case "Magnet":path.moveTo(5,3);path.lineTo(5,12);path.cubicTo(5,21,19,21,19,12);path.lineTo(19,3);line(10,3,10,12);path.moveTo(10,12);path.cubicTo(10,15,14,15,14,12);line(14,12,14,3);line(5,7,10,7);line(14,7,19,7);break;
            case "Fit":line(3,12,9,12);line(9,12,6,9);line(9,12,6,15);line(21,12,15,12);line(15,12,18,9);line(15,12,18,15);line(12,4,12,20);break;
            case "Search":path.addCircle(10,10,6,Path.Direction.CW);line(15,15,21,21);break;
            case "Sort":line(4,6,20,6);line(6,12,18,12);line(9,18,15,18);break;
            case "Grid":path.addRect(4,4,10,10,Path.Direction.CW);path.addRect(14,4,20,10,Path.Direction.CW);path.addRect(4,14,10,20,Path.Direction.CW);path.addRect(14,14,20,20,Path.Direction.CW);break;
            case "List":line(8,6,20,6);line(8,12,20,12);line(8,18,20,18);path.addCircle(4,6,.8f,Path.Direction.CW);path.addCircle(4,12,.8f,Path.Direction.CW);path.addCircle(4,18,.8f,Path.Direction.CW);break;
            case "Animation":path.moveTo(8,5);path.lineTo(13,10);path.lineTo(8,15);path.lineTo(3,10);path.close();path.moveTo(17,9);path.lineTo(21,13);path.lineTo(17,17);path.lineTo(13,13);line(3,21,21,21);break;
            case "Loop":path.moveTo(4,12);path.cubicTo(4,5,12,5,17,7);line(17,7,14,4);line(17,7,14,10);path.moveTo(20,12);path.cubicTo(20,19,12,19,7,17);line(7,17,10,14);line(7,17,10,20);break;
            case "Flag":line(5,3,5,21);path.moveTo(5,4);path.lineTo(18,4);path.lineTo(15,8.5f);path.lineTo(18,13);path.lineTo(5,13);break;
            default:path.addCircle(12,12,8,Path.Direction.CW);break;
        }
        // Play and Pause read as solid glyphs; everything else stays monoline.
        if(fill)pen.setStyle(Paint.Style.FILL_AND_STROKE);canvas.drawPath(path,pen);pen.setStyle(Paint.Style.STROKE);canvas.restore();
    }
    @Override public void setAlpha(int alpha){pen.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);invalidateSelf();}
    @Override public void setTint(int color){pen.setColor(color);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
