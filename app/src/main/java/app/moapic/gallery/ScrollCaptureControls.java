package app.moapic.gallery;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.*;
import android.widget.*;

final class ScrollCaptureControls{
    private final Context context;private final WindowManager windows;private View root;
    ScrollCaptureControls(Context c){context=c;windows=c.getSystemService(WindowManager.class);}
    void show(int count,Runnable add,Runnable finish,Runnable cancel){
        hide();LinearLayout panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(6),dp(8),dp(4));panel.setBackgroundColor(0xf26554c0);
        TextView hint=new TextView(context);hint.setText("② 반 화면씩 아래로 스크롤 → 화면 추가  ·  "+count+"장");hint.setTextColor(0xffffffff);hint.setTextSize(12);panel.addView(hint);
        LinearLayout row=new LinearLayout(context);String[] names={"화면 추가","완료·저장","취소"};Runnable[] actions={add,finish,cancel};
        for(int i=0;i<3;i++){Button b=new Button(context);b.setText(names[i]);b.setTextSize(12);int index=i;b.setOnClickListener(v->actions[index].run());row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}panel.addView(row);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(dp(360),context.getResources().getDisplayMetrics().widthPixels),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=dp(8);root=panel;
        try{windows.addView(root,p);}catch(Exception e){root=null;cancel.run();}
    }
    void hide(){if(root!=null){try{windows.removeViewImmediate(root);}catch(Exception ignored){}root=null;}}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
}
