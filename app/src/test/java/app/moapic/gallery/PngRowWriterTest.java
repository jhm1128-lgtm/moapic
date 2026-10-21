package app.moapic.gallery;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
public class PngRowWriterTest{
    @Test public void writesExactPixelsAndTallDimensions()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int w=17,h=17001;int[] row=new int[w];
        try(PngRowWriter png=new PngRowWriter(bytes,w,h)){for(int y=0;y<h;y++){for(int x=0;x<w;x++)row[x]=0xff000000|((y&255)<<16)|(x<<8)|(y%127);png.row(row);}}
        // Use the host JDK's independent PNG reader (desktop types are not in Android's compile bootclasspath).
        Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(h,image.getClass().getMethod("getHeight").invoke(image));assertEquals(w,image.getClass().getMethod("getWidth").invoke(image));
        assertEquals(0xff000000|((17000&255)<<16)|(16<<8)|(17000%127),image.getClass().getMethod("getRGB",int.class,int.class).invoke(image,16,17000));
    }
    @Test public void refusesIncompleteOutput()throws Exception{boolean refused=false;try(PngRowWriter png=new PngRowWriter(new ByteArrayOutputStream(),2,3)){png.row(new int[]{0,0});}catch(IOException expected){refused=true;assertTrue(expected.getMessage().contains("Incomplete"));}assertTrue(refused);}
}
