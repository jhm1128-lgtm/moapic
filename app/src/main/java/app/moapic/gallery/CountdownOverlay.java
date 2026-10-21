package app.moapic.gallery;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;

/** Temporary non-interactive pill, only during user-initiated screen capture. */
public final class CountdownOverlay {
    private final Context context;
    private final WindowManager windows;
    private TextView view;
    public CountdownOverlay(Context context){this.context=context;windows=context.getSystemService(WindowManager.class);}
    public void show(int seconds){
        if(!Settings.canDrawOverlays(context))return;
        try{
            if(view==null){
                view=new TextView(context);view.setTextColor(Color.WHITE);view.setTextSize(20);
                view.setTypeface(null,Typeface.BOLD);view.setGravity(Gravity.CENTER);view.setPadding(dp(22),dp(14),dp(22),dp(14));
                GradientDrawable background=new GradientDrawable();background.setColor(0xF26554C0);background.setCornerRadius(dp(30));view.setBackground(background);
                WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
                p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=dp(72);p.alpha=0.8f;windows.addView(view,p);
            }
            view.setText("화면 캡처  ·  "+seconds);view.setContentDescription(seconds+"초 뒤 화면 캡처");
        }catch(Exception ignored){hide();}
    }
    public void hide(){if(view!=null){try{windows.removeViewImmediate(view);}catch(Exception ignored){}view=null;}}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
}
