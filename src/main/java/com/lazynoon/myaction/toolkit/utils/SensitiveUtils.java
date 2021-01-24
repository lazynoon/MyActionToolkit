package com.lazynoon.myaction.toolkit.utils;

public class SensitiveUtils {
	public static String obscureMobile(String mobile) {
		if (mobile == null) {
			return null;
		}
		mobile = mobile.trim();
		if (mobile.length() < 3) {
			return mobile;
		}
		int start = Math.max(mobile.length() / 2 - 2, 1);
		int end = Math.min(mobile.length() / 2 + 3, mobile.length() - 1);
		return mobile.substring(0, start) + getObscureChars(end - start) + mobile.substring(end);
	}

	public static String obscureName(String name) {
		if (name == null) {
			return null;
		}
		name = name.trim();
		int len = name.length();
		if (len < 2) {
			return name;
		}
		if (len >= 7) {
			name = name.substring(0, 2) + getObscureChars(Math.min(len - 4, 6)) + name.substring(len - 2);
		} else if (len == 6) {
			name = name.substring(0, 1) + getObscureChars(3) + name.substring(len - 2);
		} else if (len >= 3) {
			name = name.substring(0, 1) + getObscureChars(len - 2) + name.substring(len - 1);
		} else if (len == 2) {
			name = name.substring(0, 1) + getObscureChars(1);
		}
		return name;
	}

	private static String getObscureChars(int count) {
		StringBuilder builder = new StringBuilder();
		for (int i=0; i<count; i++) {
			builder.append('*');
		}
		return builder.toString();
	}
}
