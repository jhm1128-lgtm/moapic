package app.moapic.gallery;

import android.content.Context;
import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import java.io.InputStream;

/** Reads long screenshots in overlapping bands instead of shrinking the entire page. */
final class OcrScanner {
    static String read(Context context,Photo photo,TextRecognizer korean,TextRecognizer latin)throws Exception{
        int rotation=0;
        try(InputStream stream=context.getContentResolver().openInputStream(photo.uri)){
            if(stream!=null){int o=new ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);rotation=o==6?90:o==8?270:o==3?180:0;}
        }catch(Exception ignored){}
        BitmapRegionDecoder decoder=null;
        try(InputStream stream=context.getContentResolver().openInputStream(photo.uri)){
            if(stream==null)throw new java.io.IOException("Image unavailable");
            decoder=BitmapRegionDecoder.newInstance(stream,false);if(decoder==null)throw new java.io.IOException("Decode failed");
            int sample=1;while(decoder.getWidth()/sample>2200)sample*=2;
            int step=1700*sample,height=decoder.getHeight();StringBuilder text=new StringBuilder();
            for(int y=0;y<height;y+=step){
                while(CaptureService.running)Thread.sleep(200); // Keep capture/selection memory available during an active session.
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
                Bitmap band=decoder.decodeRegion(new Rect(0,Math.max(0,y-100*sample),decoder.getWidth(),Math.min(height,y+step)),options);
                if(band==null)throw new java.io.IOException("Region unavailable");
                try{InputImage image=InputImage.fromBitmap(band,rotation);text.append(Tasks.await(korean.process(image)).getText()).append('\n').append(Tasks.await(latin.process(image)).getText()).append('\n');}
                finally{band.recycle();}
            }
            return text.toString();
        }finally{if(decoder!=null)decoder.recycle();}
    }
}
