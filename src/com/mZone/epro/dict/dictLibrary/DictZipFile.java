package com.mZone.epro.dict.dictLibrary;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.mZone.epro.dict.zipLibrary.ZInputStream;



class Chunk {
	public int offset;
	public int size;
	public Chunk(int o, int s) {
		offset = o;
		size = s;
	}
}

public class DictZipFile {
	/**
	 * 
	 */
	private RandomAccessFile dictzip;
	
	final int FTEXT = 1;
	final int FHCRC = 2;
	final int FEXTRA =4;
	final int FNAME = 8;
	final int FCOMMENT = 16;
	
	final int READ = 1;
	final int WRITE = 2;
	
	private int pos = 0;
	private int chlen = 0;
	private int _firstpos = 0;
	
	public String last_error = "";
	
	private List<Chunk> chunks;
	
	/**
	 * 
	 * @param dictzipfilename
	 */
	public DictZipFile(File dictzipfilename) {
		try {
			dictzip = new RandomAccessFile(dictzipfilename,"r");
			pos = 0;
			_firstpos = 0;
			chunks = new ArrayList<Chunk> ();
			this._read_gzip_header();
		}
		catch(Exception e) {
			last_error = e.toString();
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param buff
	 * @param size
	 * @return
	 */
	public int read(byte[] buff, int size) throws Exception{
		if(size<=0) {
			return 0;
		}
		int firstchunk = this.pos/this.chlen;
        int offset = this.pos - firstchunk*this.chlen;
        int lastchunk = (this.pos+size)/this.chlen;
        /*
         * int finish = 0;
         * int npos = 0;
         * finish = offset+size;
         * npos = this.pos+size;
         */
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        for(int i=firstchunk;i<=lastchunk;i++) {
        	byteStream.write(this._readchunk(i));
        }
        byte [] buf = byteStream.toByteArray();
        for(int i=0;i<size;i++) {
        	buff[i]=buf[offset+i];
        }
        return 0;
	}
	
	/**
	 * 
	 * @param pos
	 * @param where
	 */
	public void seek(int pos, int where) {
		if (where == 0) {
			this.pos=pos;
		}
		else if (where==1) {
				this.pos+=pos;
		}
		else {
			this.pos=pos;
		}
	}
	
	/**
	 * 
	 * @param pos
	 */
	public void seek(int pos) {
		this.seek(pos,0);
	}
	
	/**
	 * 
	 * @return
	 */
	public int tell() {
		return this.pos;
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	public void close() throws Exception{
		this.dictzip.close();
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	private void _read_gzip_header() throws Exception {
		byte [] buffer = new byte[2];
		dictzip.read(buffer);
		this._firstpos+=2;
		if (buffer[0]!= 31 || buffer[1] != -117) {
			throw new Exception("Not a gzipped file");
		}
		byte b = dictzip.readByte();
		this._firstpos+=1;
		if(b != 8) {
			throw new Exception("Unknown compression method");
		}
		byte flag = dictzip.readByte();
		//System.out.println("flag = "+flag);
		this._firstpos+=1;
		dictzip.readInt();
		dictzip.readByte();
		dictzip.readByte();
		this._firstpos+=6;
		int xlen = 0;
		if((flag & FEXTRA)!=0) {
			xlen = dictzip.readUnsignedByte();
			xlen +=  256*dictzip.readUnsignedByte();
			byte [] extra = new byte[xlen];
			dictzip.read(extra);
			this._firstpos+=2+xlen;
			int ext = 0;
			while(true) {
				 int l = ((int)extra[ext+2]&0xff)+(256*((int)extra[ext+3]&0xff));
				 if (extra[ext+0]!='R' || extra[ext+1]!='A') {
					 ext=4+l;
					 if(ext>xlen) {
						 throw new Exception("Missing dictzip extension");
					 }
				 }
				 else {
					 break;
				 }
			}
			this.chlen = ((int)extra[ext+6]&0xff) + (256*((int)extra[ext+7]&0xff));
			int chcnt = ((int)extra[ext+8]&0xff) + (256*((int)extra[ext+9]&0xff));
			int p = 10;
			List <Integer> lens = new ArrayList<Integer>(); 
			for(int i=0;i<chcnt;i++) {
				int thischlen = ((int)extra[ext+p]&0xff) + (256*((int)extra[ext+p+1]&0xff));
				p+=2;
				lens.add(thischlen);
			}
			int chpos = 0;
			for(Integer i : lens) {
				this.chunks.add(new Chunk(chpos,i));
				chpos += i;
			}
		}
		else {
			throw new Exception("Missing dictzip extension");
		}
		
		if((flag & FNAME)!=0) {
			//Read and discard a null-terminated string containing the filename
			byte s = 0;
			while(true) {
				s = dictzip.readByte();
				this._firstpos+=1;
                if(s==0)
                	break;
			}
		}

		if((flag & FCOMMENT)!=0) {
			//Read and discard a null-terminated string containing a comment
			byte s = 0;
			while(true) {
				s = dictzip.readByte();
				this._firstpos+=1;
                if(s==0)
                	break;
			}
		}
		
		if((flag & FHCRC)!=0) {
			//Read & discard the 16-bit header CRC
			dictzip.readByte();
			dictzip.readByte();
			this._firstpos+=2;
		}
	}
	
	/**
	 * 
	 * @param n
	 * @return
	 * @throws Exception
	 */
	private byte [] _readchunk(int n) throws Exception{
		if(n>=this.chunks.size()) {
			return null;
		}
		this.dictzip.seek(this._firstpos+this.chunks.get(n).offset);
		int size = this.chunks.get(n).size;
		byte [] buff = new byte[size];
		this.dictzip.read(buff);
		/* jzlib */
		@SuppressWarnings("resource")
		ZInputStream zIn =new ZInputStream(new ByteArrayInputStream(buff), true);
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); 
		byte[] buf = new byte[1024]; 
        int numRead = 0; 
        while((numRead = zIn.read(buf, 0 ,1024)) != -1) { 
            byteStream.write(buf, 0, numRead); 
        } 
        return byteStream.toByteArray(); 
	}
	
	public void runtest() {
		System.out.println("chunklen="+this.chlen);
		System.out.println("_firstpos="+this._firstpos);
	}
	
	public String test() {
		return ("chunklen="+this.chlen);
	}
}
