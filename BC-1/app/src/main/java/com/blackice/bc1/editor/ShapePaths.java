package com.blackice.bc1.editor;
import android.graphics.Path;
/** Vector geometry shared by thumbnails, live preview and both export backends. Existing IDs stay stable. */
public final class ShapePaths {
 private ShapePaths(){}
 public static Path create(int id,float h,float line){Path p=new Path();
  switch(id){
   case 1:p.addRect(-h,-h,h,h,Path.Direction.CW);break;
   case 2:p.addCircle(0,0,h,Path.Direction.CW);break;
   case 3:polygon(p,h,3,1);break;
   case 4:p.addRect(-h,-line,h,line,Path.Direction.CW);break;
   case 5:points(p,h,new float[]{-.3f,-1,.3f,-1,.3f,-.3f,1,-.3f,1,.3f,.3f,.3f,.3f,1,-.3f,1,-.3f,.3f,-1,.3f,-1,-.3f,-.3f,-.3f});break;
   case 6:p.addArc(-h,-h,h,h,30,300);Path q=new Path();android.graphics.Paint pen=new android.graphics.Paint();pen.setStyle(android.graphics.Paint.Style.STROKE);pen.setStrokeWidth(h*.22f);pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);pen.getFillPath(p,q);return q;
   case 7:p.addRoundRect(-h,-h,h,h*.6f,h*.22f,h*.22f,Path.Direction.CW);Path tail=new Path();points(tail,h,new float[]{-.5f,.5f,-.5f,1,.2f,.5f});p.op(tail,Path.Op.UNION);break;
   case 8:p.moveTo(0,0);p.arcTo(-h,-h,h,h,-45,285,false);p.close();break;
   case 9:polygon(p,h,6,1);break;
   case 10:
       for(int i=0;i<5;i++){double t=i*Math.PI*2/5;float x=(float)Math.cos(t)*h*.5f,y=(float)Math.sin(t)*h*.5f;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();
       for(int i=0;i<5;i++){double t=i*Math.PI*2/5;Path petal=new Path();petal.addCircle((float)Math.cos(t)*h*.5f,(float)Math.sin(t)*h*.5f,h*.48f,Path.Direction.CW);p.op(petal,Path.Op.UNION);}break;
   case 11:points(p,h,new float[]{-1,.6f,.2f,-.6f,-.2f,-1,1,-1,1,.2f,.6f,-.2f,-.6f,1});break;
   case 12:points(p,h,new float[]{-.65f,-1,.65f,-1,1,1,-1,1});break;
   case 13:p.addCircle(0,0,h,Path.Direction.CW);Path cut=new Path();cut.addCircle(h*.5f,-h*.35f,h,Path.Direction.CW);p.op(cut,Path.Op.DIFFERENCE);break;
   case 14:p.addRect(-h,-h*.6f,h,h*.6f,Path.Direction.CW);break;
   case 15:polygon(p,h,10,.43f);break;
   case 16:points(p,h,new float[]{-.95f,.65f,.65f,-.95f,.95f,-.65f,-.65f,.95f});break;
   case 17:points(p,h,new float[]{-1,-1,.15f,-1,.15f,0,1,1,-1,1});break;
   case 18:p.addRect(-h,-h,h,h,Path.Direction.CW);for(int i=0;i<5;i++){float x=-h+(i+.5f)*h*.4f;Path c=new Path();c.addCircle(x,-h,h*.10f,Path.Direction.CW);c.addCircle(x,h,h*.10f,Path.Direction.CW);c.addCircle(-h,x,h*.10f,Path.Direction.CW);c.addCircle(h,x,h*.10f,Path.Direction.CW);p.op(c,Path.Op.DIFFERENCE);}break;
   case 19:p.addOval(-h,-h*.62f,h,h*.62f,Path.Direction.CW);break;
   case 20:points(p,h,new float[]{0,-1,.72f,0,0,1,-.72f,0});break;
   case 21:polygon(p,h,8,1);break;
   case 22:points(p,h,new float[]{-.45f,-.6708204f,1,-.6708204f,.45f,.6708204f,-1,.6708204f});break;
   case 23:p.moveTo(0,h*.9f);p.cubicTo(-h*.2f,h*.65f,-h,h*.1f,-h,-h*.38f);p.cubicTo(-h,-h*.95f,-h*.3f,-h*1.08f,0,-h*.5f);p.cubicTo(h*.3f,-h*1.08f,h,-h*.95f,h,-h*.38f);p.cubicTo(h,h*.1f,h*.2f,h*.65f,0,h*.9f);p.close();break;
   case 24:p.moveTo(-h,h*.5f);p.arcTo(-h,-h*.5f,h,h*1.5f,180,180,false);p.close();break;
   case 25:points(p,h,new float[]{-1,-.28f,.2f,-.28f,.2f,-.75f,1,0,.2f,.75f,.2f,.28f,-1,.28f});break;
   case 26:p.setFillType(Path.FillType.WINDING);p.addCircle(0,0,h*.25f,Path.Direction.CW);p.addCircle(0,-h*.48f,h*.46f,Path.Direction.CW);p.addCircle(-h*.47f,h*.05f,h*.46f,Path.Direction.CW);p.addCircle(h*.47f,h*.05f,h*.46f,Path.Direction.CW);points(p,h,new float[]{-.11f,.2f,.11f,.2f,.3f,1,.05f,1});break;
   case 27: // Original front-facing 2D monkey icon; no Blender mesh or asset is embedded.
       p.addOval(-h,-h*.25f,-h*.45f,h*.42f,Path.Direction.CW);p.addOval(h*.45f,-h*.25f,h,h*.42f,Path.Direction.CW);
       p.addOval(-h*.7f,-h*.9f,h*.7f,h*.65f,Path.Direction.CW);p.addOval(-h*.5f,h*.1f,h*.5f,h*.95f,Path.Direction.CW);
       Path holes=new Path();holes.addOval(-h*.47f,-h*.22f,-h*.13f,h*.12f,Path.Direction.CW);holes.addOval(h*.13f,-h*.22f,h*.47f,h*.12f,Path.Direction.CW);
       holes.addOval(-h*.18f,h*.32f,h*.18f,h*.44f,Path.Direction.CW);holes.addRoundRect(-h*.25f,h*.65f,h*.25f,h*.72f,h*.035f,h*.035f,Path.Direction.CW);p.op(holes,Path.Op.DIFFERENCE);break;
   default:throw new IllegalArgumentException("Unsupported shape");
  }return p;
 }
 static void points(Path p,float h,float[] xy){p.moveTo(xy[0]*h,xy[1]*h);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i]*h,xy[i+1]*h);p.close();}
 static void polygon(Path p,float h,int n,float inner){for(int i=0;i<n;i++){double t=-Math.PI/2+i*2*Math.PI/n;float r=h*(i%2==0?1:inner);float x=(float)Math.cos(t)*r,y=(float)Math.sin(t)*r;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();}
}
