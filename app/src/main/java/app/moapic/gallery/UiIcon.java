package app.moapic.gallery;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native icons: no icon font, bitmap pack or extra UI dependency. */
public final class UiIcon extends Drawable {
    public static final int SETTINGS=0, CAPTURE=1, ADD=2, TEXT=3, DELETE=4, SHORTCUT=5;
    private final int kind, color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public UiIcon(int kind, int color) { this.kind=kind; this.color=color; }
    @Override public void draw(Canvas original) {
        int save=original.save(); Rect b=getBounds(); original.translate(b.left,b.top); original.scale(b.width()/32f,b.height()/32f);
        paint.setColor(color); paint.setStrokeWidth(2); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        switch(kind) {
            case SETTINGS:
                Path gear=new Path();
                for(int i=0;i<32;i++) { double a=i*Math.PI/16; float r=(i%4==0||i%4==3)?13:10.5f; float x=16+(float)Math.cos(a)*r,y=16+(float)Math.sin(a)*r; if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y); }
                gear.close(); original.drawPath(gear,paint); original.drawCircle(16,16,4,paint); break;
            case CAPTURE:
                original.drawLine(4,11,4,5,paint); original.drawLine(4,5,11,5,paint);
                original.drawLine(21,5,28,5,paint); original.drawLine(28,5,28,11,paint);
                original.drawLine(4,21,4,27,paint); original.drawLine(4,27,11,27,paint);
                original.drawLine(21,27,28,27,paint); original.drawLine(28,27,28,21,paint);
                original.drawCircle(16,16,6,paint); break;
            case ADD:
                original.drawRoundRect(3,5,25,27,3,3,paint); original.drawCircle(10,12,2,paint);
                Path mountain=new Path();mountain.moveTo(5,24);mountain.lineTo(12,17);mountain.lineTo(17,22); original.drawPath(mountain,paint);
                original.drawLine(25,13,25,23,paint);original.drawLine(20,18,30,18,paint);break;
            case TEXT:
                paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(13);original.drawText("ABC",16,13,paint);paint.setTextSize(10);original.drawText("가나다",16,27,paint);break;
            case DELETE:
                original.drawLine(5,9,27,9,paint);original.drawRoundRect(11,4,21,9,2,2,paint);
                original.drawRoundRect(8,9,24,28,2,2,paint);original.drawLine(13,14,13,23,paint);original.drawLine(19,14,19,23,paint);break;
            default:
                original.drawRoundRect(5,3,22,29,3,3,paint);original.drawLine(13,20,28,5,paint);original.drawLine(20,5,28,5,paint);original.drawLine(28,5,28,13,paint);
        }
        original.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
