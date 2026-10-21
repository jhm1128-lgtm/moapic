package app.moapic.gallery;

/** Finds the vertical distance scrolled between two same-sized content regions. */
public final class ScrollOverlap {
    private ScrollOverlap(){}
    public static int shift(int[] before,int[] after,int width,int height){
        if(width<8||height<80||before.length!=width*height||after.length!=before.length)return -1;
        long diff=0;int samples=0;
        for(int y=0;y<height;y+=4)for(int x=0;x<width;x+=3){diff+=distance(before[y*width+x],after[y*width+x]);samples++;}
        if(diff/(double)Math.max(1,samples)<1.2)return 0;
        int max=height*3/4,best=-1;double bestScore=Double.MAX_VALUE;
        double[] scores=new double[max+1];
        for(int d=1;d<=max;d++){double score=score(before,after,width,height,d);scores[d]=score;if(score<bestScore){bestScore=score;best=d;}}
        if(best<0)return -1;
        if(bestScore>18)return -1;
        // Repeated identical rows can make the scroll distance unknowable; refuse a lossy guess.
        for(int d=1;d<=max;d++)if(Math.abs(d-best)>12&&scores[d]<bestScore+.3)return -1;
        return best;
    }
    private static double score(int[] a,int[] b,int w,int h,int shift){
        long sum=0;int count=0;int step=Math.max(1,(h-shift)/96);
        for(int y=0;y<h-shift;y+=step)for(int x=1;x<w-1;x+=2){
            int ai=(y+shift)*w+x,bi=y*w+x;
            if(distance(a[ai],a[ai-1])<10&&distance(b[bi],b[bi-1])<10)continue;
            sum+=distance(a[ai],b[bi]);count++;
        }
        return count<80?Double.MAX_VALUE:sum/(double)count;
    }
    private static int distance(int a,int b){return (Math.abs(((a>>16)&255)-((b>>16)&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs((a&255)-(b&255)))/3;}
}
