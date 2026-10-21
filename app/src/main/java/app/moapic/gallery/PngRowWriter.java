package app.moapic.gallery;

import java.io.*;
import java.util.zip.*;

/** Streams a tall PNG without allocating a bitmap as tall as the entire page. */
final class PngRowWriter implements AutoCloseable {
    private final DataOutputStream out;
    private final IdatStream chunks;
    private final DeflaterOutputStream compressed;
    private final Deflater deflater=new Deflater(4);
    private final byte[] row;
    private final int height;
    private int written;
    PngRowWriter(OutputStream output,int width,int height)throws IOException{
        out=new DataOutputStream(output);this.height=height;row=new byte[1+width*4];
        out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(header);h.writeInt(width);h.writeInt(height);h.write(new byte[]{8,6,0,0,0});chunk("IHDR",header.toByteArray());
        chunks=new IdatStream();compressed=new DeflaterOutputStream(chunks,deflater,65536);
    }
    void row(int[] pixels)throws IOException{
        if(written>=height||pixels.length*4+1!=row.length)throw new IOException("Unexpected row");
        row[0]=0;int i=1;for(int p:pixels){row[i++]=(byte)(p>>16);row[i++]=(byte)(p>>8);row[i++]=(byte)p;row[i++]=(byte)(p>>>24);}compressed.write(row);written++;
    }
    private void chunk(String type,byte[] data)throws IOException{
        byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(name);crc.update(data);out.writeInt(data.length);out.write(name);out.write(data);out.writeInt((int)crc.getValue());
    }
    @Override public void close()throws IOException{try{if(written!=height)throw new IOException("Incomplete image");compressed.finish();chunks.flush();chunk("IEND",new byte[0]);out.flush();}finally{try{compressed.close();}finally{deflater.end();out.close();}}}
    private final class IdatStream extends OutputStream{
        private final ByteArrayOutputStream buffer=new ByteArrayOutputStream(65536);
        public void write(int b)throws IOException{buffer.write(b);if(buffer.size()>=65536)flush();}
        public void write(byte[] bytes,int offset,int length)throws IOException{while(length>0){int amount=Math.min(length,65536-buffer.size());buffer.write(bytes,offset,amount);offset+=amount;length-=amount;if(buffer.size()==65536)flush();}}
        public void flush()throws IOException{if(buffer.size()>0){chunk("IDAT",buffer.toByteArray());buffer.reset();}}
    }
}
