package com.lazynoon.myaction.toolkit.utils;

import net_io.utils.ByteUtils;

import java.util.Date;

public class EqualUtils {
	public static final double PRECISION_DOUBLE = 0.0000001;
	public static final double PRECISION_FLOAT = 0.0001;

	public static boolean isEqual(String str1, String str2) {
		if (str1 == null) {
			if (str2 == null) {
				return true;
			} else {
				return false;
			}
		} else if(str2 == null) {
			return false;
		}
		return str1.equals(str2);
	}

	public static boolean isEqual(Date date1, Date date2) {
		if (date1 == null) {
			if (date2 == null) {
				return true;
			} else {
				return false;
			}
		} else if(date2 == null) {
			return false;
		}
		return date1.getTime() == date2.getTime();
	}

	public static boolean isEqual(float num1, float num2) {
		return Math.abs(num1 - num2) < PRECISION_FLOAT;
	}

	public static boolean isEqual(double num1, double num2) {
		return Math.abs(num1 - num2) < PRECISION_DOUBLE;
	}

	public static boolean isEqual(byte[] bts1, byte[] bts2) {
		return ByteUtils.isEqual(bts1, bts2);
	}

}
