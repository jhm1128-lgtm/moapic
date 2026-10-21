package app.moapic.gallery;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** A selection on the captured, original-resolution frame; no screen-control permission. */
final class CaptureSelectionOverlay {
    private final Context context;
    private final WindowManager windows;
    private View root;
    CaptureSelectionOverlay(Context context){this.context=context;windows=context.getSystemService(WindowManager.class);}
    void show(Bitmap bitmap,boolean longPage,Consumer<Rect> chosen,Runnable cancel){
        hide();LinearLayout layout=new LinearLayout(context);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(0xff17151f);layout.setPadding(dp(14),dp(22),dp(14),dp(22));
        TextView title=new TextView(context);title.setText(longPage?"① 스크롤되는 내용만 선택해 주세요":"필요한 부분만 선택해 주세요");title.setTextColor(Color.WHITE);title.setTextSize(19);layout.addView(title);
        TextView note=new TextView(context);note.setText(longPage?"고정된 상단 메뉴·아래 버튼은 빼주세요. 모서리를 움직여 범위를 맞춰요.":"네 모서리를 끌어 크기를 조절하거나, 네모 안을 끌어 이동해요.");note.setTextColor(0xffc9c0e2);note.setPadding(0,dp(8),0,dp(8));layout.addView(note);
        SelectionView selection=new SelectionView(context,bitmap,longPage);layout.addView(selection,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(context);Button back=new Button(context);back.setText("취소");back.setOnClickListener(v->cancel.run());buttons.addView(back,new LinearLayout.LayoutParams(0,dp(52),1));
        Button save=new Button(context);save.setText(longPage?"이 범위로 긴 캡처 시작":"선택한 부분 저장");save.setOnClickListener(v->{Rect rect=selection.region();if(rect.width()>=16&&rect.height()>=16)chosen.accept(rect);});buttons.addView(save,new LinearLayout.LayoutParams(0,dp(52),2));layout.addView(buttons);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        root=layout;try{windows.addView(root,p);}catch(Exception e){root=null;cancel.run();}
    }
    void hide(){if(root!=null){try{windows.removeViewImmediate(root);}catch(Exception ignored){}root=null;}}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private final class SelectionView extends View{
        private final Bitmap image;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF bounds=new RectF(),selection=new RectF();private final boolean longPage;private float lastX,lastY;private int handle;
        SelectionView(Context c,Bitmap image,boolean longPage){super(c);this.image=image;this.longPage=longPage;setContentDescription("선을 끌어서 캡처할 사각형을 선택하세요");}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){float s=Math.min(w/(float)image.getWidth(),h/(float)image.getHeight());float iw=image.getWidth()*s,ih=image.getHeight()*s;bounds.set((w-iw)/2,(h-ih)/2,(w+iw)/2,(h+ih)/2);selection.set(bounds.left+iw*.04f,bounds.top+ih*(longPage?.14f:.08f),bounds.right-iw*.04f,bounds.bottom-ih*(longPage?.09f:.08f));}
        @Override protected void onDraw(Canvas c){
            c.drawBitmap(image,null,bounds,paint);paint.setColor(0xb0000000);
            c.drawRect(bounds.left,bounds.top,bounds.right,selection.top,paint);c.drawRect(bounds.left,selection.bottom,bounds.right,bounds.bottom,paint);
            c.drawRect(bounds.left,selection.top,selection.left,selection.bottom,paint);c.drawRect(selection.right,selection.top,bounds.right,selection.bottom,paint);
            paint.setColor(0xffc8b8ff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));c.drawRect(selection,paint);paint.setStyle(Paint.Style.FILL);
            c.drawCircle(selection.left,selection.top,dp(8),paint);c.drawCircle(selection.right,selection.top,dp(8),paint);
            c.drawCircle(selection.left,selection.bottom,dp(8),paint);c.drawCircle(selection.right,selection.bottom,dp(8),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=x;lastY=y;float near=dp(36);handle=Math.abs(x-selection.left)<near&&Math.abs(y-selection.top)<near?1:Math.abs(x-selection.right)<near&&Math.abs(y-selection.top)<near?2:Math.abs(x-selection.left)<near&&Math.abs(y-selection.bottom)<near?3:Math.abs(x-selection.right)<near&&Math.abs(y-selection.bottom)<near?4:selection.contains(x,y)?5:0;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float min=dp(30);if(handle==5){float dx=Math.max(bounds.left-selection.left,Math.min(bounds.right-selection.right,x-lastX));float dy=Math.max(bounds.top-selection.top,Math.min(bounds.bottom-selection.bottom,y-lastY));selection.offset(dx,dy);}else{if(handle==1||handle==3)selection.left=Math.max(bounds.left,Math.min(selection.right-min,x));if(handle==2||handle==4)selection.right=Math.min(bounds.right,Math.max(selection.left+min,x));if(handle==1||handle==2)selection.top=Math.max(bounds.top,Math.min(selection.bottom-min,y));if(handle==3||handle==4)selection.bottom=Math.min(bounds.bottom,Math.max(selection.top+min,y));}lastX=x;lastY=y;invalidate();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        Rect region(){float sx=image.getWidth()/bounds.width(),sy=image.getHeight()/bounds.height();return new Rect(Math.max(0,Math.round((selection.left-bounds.left)*sx)),Math.max(0,Math.round((selection.top-bounds.top)*sy)),Math.min(image.getWidth(),Math.round((selection.right-bounds.left)*sx)),Math.min(image.getHeight(),Math.round((selection.bottom-bounds.top)*sy)));}
    }
}
