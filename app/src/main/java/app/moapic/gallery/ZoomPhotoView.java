package app.moapic.gallery;

import android.content.Context;
import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.InputStream;
import java.util.concurrent.*;

/** Zooms the original URI, decoding only the visible region of large/long images. */
public final class ZoomPhotoView extends View implements AutoCloseable {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix orientation=new Matrix(), matrix=new Matrix();
    private final RectF fullBounds=new RectF(),decodedBounds=new RectF();
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    private BitmapRegionDecoder decoder;
    private InputStream input;
    private Bitmap preview,tile;
    private Rect tileRect,requested;
    private int sourceW,sourceH,displayW,displayH,lastSample=-1,generation;
    private float base=1,zoom=1,offsetX,offsetY;
    private boolean ready,closed;
    private String message="원본 사진을 열고 있어요…";
    private final Runnable refresh=this::requestTile;
    public ZoomPhotoView(Context context,Uri uri){
        super(context);setBackgroundColor(0xFF17151F);setContentDescription("원본 사진. 두 손가락으로 확대하거나 두 번 눌러 확대하세요.");
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){scale(d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onScroll(MotionEvent e1,MotionEvent e2,float dx,float dy){if(!pinch.isInProgress()){offsetX-=dx;offsetY-=dy;changed();}return true;}
            @Override public boolean onDoubleTap(MotionEvent e){float target=zoom>1.1f?1:Math.min(6,Math.max(2,1/base));scale(target/zoom,e.getX(),e.getY());return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}
        });
        io.execute(()->{
            try{
                input=context.getContentResolver().openInputStream(uri);
                if(input==null)throw new java.io.IOException();
                decoder=BitmapRegionDecoder.newInstance(input,false);if(decoder==null)throw new java.io.IOException();
                int w=decoder.getWidth(),h=decoder.getHeight(),exif=1;
                try(InputStream meta=context.getContentResolver().openInputStream(uri)){if(meta!=null)exif=new ExifInterface(meta).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(w/options.inSampleSize>2048||h/options.inSampleSize>2048)options.inSampleSize*=2;
                Bitmap small=decoder.decodeRegion(new Rect(0,0,w,h),options);int rotation=exif;
                main.post(()->{if(closed){if(small!=null)small.recycle();return;}sourceW=w;sourceH=h;preview=small;setOrientation(rotation);ready=true;fit();});
            }catch(Exception|OutOfMemoryError error){main.post(()->{message="원본을 열지 못했어요. 사진 접근 권한을 확인해 주세요.";invalidate();});}
        });
    }
    private void setOrientation(int value){
        float[] values;
        switch(value){
            case 2:values=new float[]{-1,0,sourceW,0,1,0,0,0,1};break;
            case 3:values=new float[]{-1,0,sourceW,0,-1,sourceH,0,0,1};break;
            case 4:values=new float[]{1,0,0,0,-1,sourceH,0,0,1};break;
            case 5:values=new float[]{0,1,0,1,0,0,0,0,1};break;
            case 6:values=new float[]{0,-1,sourceH,1,0,0,0,0,1};break;
            case 7:values=new float[]{0,-1,sourceH,-1,0,sourceW,0,0,1};break;
            case 8:values=new float[]{0,1,0,-1,0,sourceW,0,0,1};break;
            default:values=new float[]{1,0,0,0,1,0,0,0,1};
        }
        orientation.setValues(values);boolean swap=value>=5&&value<=8;displayW=swap?sourceH:sourceW;displayH=swap?sourceW:sourceH;
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){if(ready)fit();}
    private void fit(){if(getWidth()==0||getHeight()==0)return;boolean longPage=displayH>displayW*3;base=longPage?getWidth()/(float)displayW:Math.min(getWidth()/(float)displayW,getHeight()/(float)displayH);zoom=1;offsetX=(getWidth()-displayW*base)/2;offsetY=longPage?0:(getHeight()-displayH*base)/2;changed();}
    private void scale(float factor,float x,float y){
        if(!ready)return;float next=Math.max(1,Math.min(Math.max(8,2/base),zoom*factor)),actual=next/zoom;
        offsetX=x-(x-offsetX)*actual;offsetY=y-(y-offsetY)*actual;zoom=next;changed();
    }
    private void changed(){
        if(!ready)return;float w=displayW*base*zoom,h=displayH*base*zoom;
        offsetX=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,offsetX));
        offsetY=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,offsetY));
        matrix.set(orientation);matrix.postScale(base*zoom,base*zoom);matrix.postTranslate(offsetX,offsetY);
        invalidate();main.removeCallbacks(refresh);main.postDelayed(refresh,50);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(!ready){paint.setColor(Color.WHITE);paint.setTextSize(15*getResources().getDisplayMetrics().scaledDensity);canvas.drawText(message,24,getHeight()/2f,paint);return;}
        int save=canvas.save();canvas.concat(matrix);
        fullBounds.set(0,0,sourceW,sourceH);if(preview!=null)canvas.drawBitmap(preview,null,fullBounds,paint);
        if(tile!=null&&tileRect!=null){decodedBounds.set(tileRect);canvas.drawBitmap(tile,null,decodedBounds,paint);}
        canvas.restoreToCount(save);
    }
    private void requestTile(){
        if(!ready||closed)return;Matrix inverse=new Matrix();if(!matrix.invert(inverse))return;
        RectF area=new RectF(0,0,getWidth(),getHeight());inverse.mapRect(area);
        Rect region=new Rect(Math.max(0,(int)Math.floor(area.left)),Math.max(0,(int)Math.floor(area.top)),Math.min(sourceW,(int)Math.ceil(area.right)),Math.min(sourceH,(int)Math.ceil(area.bottom)));
        if(region.isEmpty())return;int sample=1;while(sample*2<=1/(base*zoom))sample*=2;
        while((long)(region.width()/sample)*(region.height()/sample)>6000000)sample*=2;
        if(region.equals(requested)&&sample==lastSample)return;requested=new Rect(region);lastSample=sample;int task=++generation;int decodeSample=sample;
        io.execute(()->{
            if(closed)return;
            try{BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=decodeSample;Bitmap decoded=decoder.decodeRegion(region,options);
                main.post(()->{if(closed||task!=generation){if(decoded!=null)decoded.recycle();return;}Bitmap old=tile;tile=decoded;tileRect=region;invalidate();if(old!=null)old.recycle();});
            }catch(Exception|OutOfMemoryError ignored){}
        });
    }
    @Override public boolean onTouchEvent(MotionEvent event){pinch.onTouchEvent(event);gestures.onTouchEvent(event);return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void close(){
        if(closed)return;closed=true;main.removeCallbacks(refresh);if(preview!=null){preview.recycle();preview=null;}if(tile!=null){tile.recycle();tile=null;}
        io.execute(()->{if(decoder!=null)decoder.recycle();try{if(input!=null)input.close();}catch(Exception ignored){}});io.shutdown();
    }
}
