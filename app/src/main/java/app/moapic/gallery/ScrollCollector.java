package app.moapic.gallery;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Original-pixel strips on private cache, streamed to a single lossless PNG on finish. */
final class ScrollCollector implements AutoCloseable{
    private final Context context;private final File directory;private final List<File> strips=new ArrayList<>();
    private int width,height,frameHeight;private int[] previous;
    ScrollCollector(Context c)throws IOException{context=c;directory=new File(c.getCacheDir(),"scroll-"+UUID.randomUUID());if(!directory.mkdirs())throw new IOException("Cache unavailable");}
    static void cleanInterruptedCache(Context c){File[] dirs=c.getCacheDir().listFiles(f->f.isDirectory()&&f.getName().startsWith("scroll-"));if(dirs!=null)for(File dir:dirs){File[] files=dir.listFiles(f->f.isFile()&&f.getName().matches("[0-9]+\\.png"));if(files!=null)for(File f:files)f.delete();dir.delete();}}
    int count(){return strips.size();}
    int add(Bitmap image)throws IOException{
        if(width!=0&&(width!=image.getWidth()||frameHeight!=image.getHeight()))throw new IOException("화면 크기가 달라졌어요. 회전하지 말고 다시 시도해 주세요.");
        Bitmap signature=Bitmap.createScaledBitmap(image,96,image.getHeight(),false);int[] pixels=new int[96*image.getHeight()];signature.getPixels(pixels,0,96,0,0,96,image.getHeight());if(signature!=image)signature.recycle();
        int shift=previous==null?image.getHeight():ScrollOverlap.shift(previous,pixels,96,image.getHeight());
        if(shift<0)throw new IOException("겹치는 부분을 못 찾았어요. 조금 위로 올린 뒤 다시 추가해 주세요.");
        if(shift==0)return 0;
        if(strips.size()>=40||(long)image.getWidth()*(height+shift)>60000000L)throw new IOException("한 장에 담을 수 있는 길이에 도달했어요. 완료·저장 후 이어서 캡처해 주세요.");
        Bitmap piece=Bitmap.createBitmap(image,0,image.getHeight()-shift,image.getWidth(),shift);File file=new File(directory,strips.size()+".png");
        try(OutputStream out=new FileOutputStream(file)){if(!piece.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("저장 공간을 확인해 주세요.");}finally{if(piece!=image)piece.recycle();}
        strips.add(file);width=image.getWidth();frameHeight=image.getHeight();height+=shift;previous=pixels;return shift;
    }
    Uri save()throws Exception{
        if(strips.isEmpty())throw new IOException("추가한 화면이 없어요.");
        Uri target=MediaRepository.createPending(context.getContentResolver(),"Screenshot_Moapic_Long_"+System.currentTimeMillis()+".png","Pictures/Screenshots/Moapic/","image/png");
        try{
            OutputStream output=context.getContentResolver().openOutputStream(target);if(output==null)throw new IOException("저장 실패");
            try(PngRowWriter png=new PngRowWriter(output,width,height)){
                int[] row=new int[width];for(File file:strips){Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());if(bitmap==null)throw new IOException("중간 이미지를 열 수 없어요.");try{for(int y=0;y<bitmap.getHeight();y++){bitmap.getPixels(row,0,width,0,y,width,1);png.row(row);}}finally{bitmap.recycle();}}
            }
            MediaRepository.publish(context.getContentResolver(),target);return target;
        }catch(Exception|OutOfMemoryError e){context.getContentResolver().delete(target,null,null);throw e;}
    }
    @Override public void close(){for(File file:strips)file.delete();directory.delete();previous=null;strips.clear();}
}
