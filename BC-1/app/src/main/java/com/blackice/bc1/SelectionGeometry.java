package com.blackice.bc1;
import android.graphics.RectF;
import com.blackice.bc1.editor.*;
import java.util.*;
/** Metadata loads off the UI thread; a completion refreshes the box without reselecting the layer. */
final class SelectionGeometry {
    final EditorActivity a;final Map<String,int[]> sizes=new HashMap<>();final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    SelectionGeometry(EditorActivity activity){a=activity;}
    int[] size(String uri,boolean image,int width,int height){if(width>0&&height>0)return new int[]{width,height};int[] found=sizes.get(uri);if(found!=null)return found;
        if(uri!=null&&!pending.contains(uri)&&!failed.contains(uri)&&!a.io.isShutdown()){pending.add(uri);a.io.execute(()->{int[] value=MediaImporter.measure(a,uri,image);a.main.post(()->{pending.remove(uri);if(a.isDestroyed())return;if(value!=null)sizes.put(uri,value);else failed.add(uri);if(a.previewControls!=null)a.previewControls.invalidate();});});}return null;}
    RectF bounds(EditProject.Clip c,EditProject.Overlay o){double aspect=a.frameAspect();if(c!=null){int[] s=size(c.uri,c.image,c.width,c.height);return LayerGeometry.fittedBounds(aspect,s==null?0:s[0],s==null?0:s[1]);}
        if(o==null)return LayerGeometry.fittedBounds(aspect,0,0);
        int[] canvas=a.project.canvasSize(a.project.previewHeight);
        if(o.shape>0)return LayerGeometry.shapeBounds(o,canvas[0],canvas[1]);
        if(o.imageUri!=null){int[] s=size(o.imageUri,true,0,0);if(s==null)return new RectF(0,0,0,0);RectF b=LayerGeometry.imageRect(canvas[0],s[0],s[1]);b.set(b.left/canvas[1],b.top/canvas[1],b.right/canvas[1],b.bottom/canvas[1]);return b;}
        // Text retains its established typographic frame; shapes and media use measured geometry.
        double unit=Math.min(aspect,1)*.065;String[] lines=o.text.split("\n",-1);int longest=1;for(String l:lines)longest=Math.max(longest,l.length());float w=(float)Math.min(aspect*.45,unit*.3*longest),h=(float)(unit*.65*lines.length);return new RectF(-w,-h,w,h);
    }
}
