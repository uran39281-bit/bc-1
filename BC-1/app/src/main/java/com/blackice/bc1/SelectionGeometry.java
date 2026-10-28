package com.blackice.bc1;
import android.graphics.RectF;
import com.blackice.bc1.editor.*;
import java.util.*;
/** Metadata loads off the UI thread; a completion refreshes the box without reselecting the layer. */
final class SelectionGeometry {
    final EditorActivity a;final Map<String,int[]> sizes=new HashMap<>();final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    SelectionGeometry(EditorActivity activity){a=activity;}
    int[] size(String uri,boolean image,int width,int height){if(width>0&&height>0)return new int[]{width,height};int[] found=sizes.get(uri);if(found!=null)return found;
        if(uri!=null&&!pending.contains(uri)&&!failed.contains(uri)&&!a.io.isShutdown()){pending.add(uri);a.io.execute(()->{int[] value=MediaImporter.measure(a,uri,image);a.main.post(()->{pending.remove(uri);if(a.isDestroyed())return;if(value!=null)sizes.put(uri,value);else failed.add(uri);if(a.previewControls!=null)a.previewControls.invalidate();if(a.clip()!=null||a.overlay()!=null)a.renderInspector();});});}return null;}
    RectF bounds(EditProject.Clip c,EditProject.Overlay o){double aspect=a.previewAspect();if(c!=null){int[] s=size(c.uri,c.image,c.width,c.height);return s==null?new RectF():LayerGeometry.fittedBounds(aspect,s[0],s[1]);}
        if(o==null)return LayerGeometry.fittedBounds(aspect,0,0);
        int[] canvas=a.project.canvasSize(1080);
        if(o.shape>0)return LayerGeometry.shapeBounds(o,canvas[0],canvas[1]);
        if(o.imageUri!=null){int[] s=size(o.imageUri,true,0,0);if(s==null)return new RectF(0,0,0,0);RectF b=LayerGeometry.imageRect(canvas[0],s[0],s[1]);b.set(b.left/canvas[1],b.top/canvas[1],b.right/canvas[1],b.bottom/canvas[1]);return b;}
        return LayerGeometry.textBounds(o,canvas[0],canvas[1],TransformMath.of(o,a.panels.motionTime()).scale);
    }
}
