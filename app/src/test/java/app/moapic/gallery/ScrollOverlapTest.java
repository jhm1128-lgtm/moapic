package app.moapic.gallery;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ScrollOverlapTest {
    @Test public void preservesEveryRowForSeveralScrollDistances(){
        int w=64,h=640;int[] document=new int[w*1400];Random random=new Random(51);
        for(int i=0;i<document.length;i++)document[i]=0xff000000|random.nextInt(0xffffff);
        int[] first=Arrays.copyOfRange(document,0,w*h);
        for(int shift:new int[]{1,37,128,301,479})assertEquals(shift,ScrollOverlap.shift(first,Arrays.copyOfRange(document,w*shift,w*(shift+h)),w,h));
    }
    @Test public void rejectsUnrelatedScreensAndRecognizesDuplicates(){
        Random r=new Random(6);int[] a=new int[64*320],b=new int[a.length];for(int i=0;i<a.length;i++){a[i]=r.nextInt();b[i]=r.nextInt();}
        assertEquals(0,ScrollOverlap.shift(a,a.clone(),64,320));assertEquals(-1,ScrollOverlap.shift(a,b,64,320));
    }
}
