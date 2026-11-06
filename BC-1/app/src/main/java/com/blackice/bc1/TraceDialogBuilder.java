package com.blackice.bc1;
import android.app.AlertDialog;
import android.content.Context;
/** Preserve native dialog behavior while tracing its separate input window. */
public final class TraceDialogBuilder extends AlertDialog.Builder {
 public TraceDialogBuilder(Context context){super(context);}
 public TraceDialogBuilder(Context context,int theme){super(context,theme);}
 @Override public AlertDialog create(){AlertDialog dialog=super.create();ActionTrace.install(dialog.getWindow());return dialog;}
}
