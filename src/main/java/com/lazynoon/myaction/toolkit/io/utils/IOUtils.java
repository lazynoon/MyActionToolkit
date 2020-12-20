package com.lazynoon.myaction.toolkit.io.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class IOUtils {
	/** 1K **/
	public static final int SIZE_1K = 1024;
	/** 32K **/
	public static final int SIZE_32K = 1024 * 32;
	/** 64K **/
	public static final int SIZE_64K = 1024 * 64;
	
	public static byte[] fullRead(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buff = new byte[1024];
		int size;
		while((size = in.read(buff)) > 0) {
			out.write(buff, 0, size);
		}
		return out.toByteArray();
	}
}
