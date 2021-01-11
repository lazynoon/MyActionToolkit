package com.lazynoon.myaction.toolkit.security;

import net_io.myaction.tool.crypto.AES;
import net_io.myaction.tool.exception.CryptoException;
import net_io.utils.ByteUtils;
import net_io.utils.EncodeUtils;

/**
 * 密钥解密：
 */
public class KEKDecryptUtils {
	private static final int MAJOR_VERSION = 1;
	private static final int HEAD_LENGTH = 32;
	private static final int MD5_CODE_LENGTH = 16;

	public static String decryptKey(String sourceKey, String protectKey) throws CryptoException {
		byte[] encryptedKey = EncodeUtils.myBase62Decode(sourceKey);
		AES aes = new AES(protectKey);
		encryptedKey = aes.decrypt(encryptedKey);
		int majorVersion = encryptedKey[0] & 0xFF;
		if (majorVersion != MAJOR_VERSION) {
			throw new CryptoException("KEK major version is not " + MAJOR_VERSION);
		}
		byte[] plaintextMd5Code = new byte[MD5_CODE_LENGTH];
		int md5CodeOffset = HEAD_LENGTH - MD5_CODE_LENGTH;
		System.arraycopy(encryptedKey, md5CodeOffset, plaintextMd5Code, 0, plaintextMd5Code.length);
		byte[] plaintextKey = new byte[encryptedKey.length - HEAD_LENGTH];
		System.arraycopy(encryptedKey, HEAD_LENGTH, plaintextKey, 0, plaintextKey.length);
		byte[] md5Code = EncodeUtils.digestMD5(plaintextKey);
		if (!ByteUtils.isEqual(md5Code, plaintextMd5Code)) {
			throw new CryptoException("KEK hash code is not matched");
		}
		return EncodeUtils.base64Encode(plaintextKey);
	}
}
