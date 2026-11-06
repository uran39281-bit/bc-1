package com.blackice.bc1;
import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;
import java.util.UUID;
/** PopupWindow has no Window.Callback; record its root without consuming or changing input. */
final class TraceInputLayout extends LinearLayout {
 private String gesture=UUID.randomUUID().toString();
 TraceInputLayout(Context context){super(context);setOrientation(VERTICAL);}
 private String identity(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER||e.getActionMasked()==MotionEvent.ACTION_SCROLL)gesture=UUID.randomUUID().toString();return gesture;}
 @Override public boolean dispatchTouchEvent(MotionEvent e){return ActionTrace.inputMotion(this,e,identity(e),()->super.dispatchTouchEvent(e));}
 @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return ActionTrace.inputMotion(this,e,identity(e),()->super.dispatchGenericMotionEvent(e));}
 @Override public boolean dispatchKeyEvent(KeyEvent e){return ActionTrace.inputKey(e,()->super.dispatchKeyEvent(e));}
}
