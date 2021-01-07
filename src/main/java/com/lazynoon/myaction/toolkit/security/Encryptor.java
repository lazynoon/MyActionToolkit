package com.lazynoon.myaction.toolkit.security;


import com.lazynoon.commons.safesave.*;
import net_io.utils.EncodeUtils;

/**
 * 加解密助手类示例
 *
 * @author Hansen
 */
public class Encryptor {
	private static final int BYTE_SIZE = 256;
	private static final Encryptor instance = new Encryptor();
	private SafeKeyStore keyStore = new SafeKeyStore();
	private int currentMajorVersion = 1;
	private int currentMinorVersion = 0;
	private int currentKeyId = 1;
	private int currentMappingId = 1;

	private Encryptor() {
		init();
	}

	private void init() {
		//字节映射表
		byte[][] byteMappingPool = new byte[2][];
		for(int i=0; i<byteMappingPool.length; i++) {
			byteMappingPool[i] = new byte[BYTE_SIZE];
		}
		//加号生成
		for(int i=0; i<BYTE_SIZE; i++) {
			byteMappingPool[0][i] = (byte)(i + 5);
		}
		//移位生成
		for(int i=0; i<BYTE_SIZE; i++) {
			int num = i;
			num = (num << 1 |  num >>> 7) & 0xFF;
			byteMappingPool[1][i] = (byte)num;
		}
		//密钥
		byte[][] keyPool = new byte[2][];
		keyPool[0] = "626a8034bb8e".getBytes(EncodeUtils.Charsets.UTF_8);
		keyPool[1] = "ea91bc0084f6".getBytes(EncodeUtils.Charsets.UTF_8);
		//注册到密钥对象
		for(int i=0; i<byteMappingPool.length; i++) {
			keyStore.registerByteMapping(i+1, byteMappingPool[i]);
		}
		for(int i=0; i<keyPool.length; i++) {
			keyStore.registerSecretKey(i+1, keyPool[i]);
		}
	}


	protected SafeData _decrypt(byte[] data) throws SafeCryptoException {
		int majorVersion = SafeEncryptorFactory.getMajorVersion(data);
		int minorVersion = SafeEncryptorFactory.getMinorVersion(data);
		SafeEncryptor encryptor = SafeEncryptorFactory.getInstance(majorVersion, minorVersion, keyStore);
		if(encryptor == null) {
			throw new IllegalArgumentException("not support encrypt version: "+minorVersion+", "+minorVersion);
		}
		return encryptor.decrypt(data);
	}

	protected byte[] _encrypt(byte[] data) throws SafeCryptoException {
		SafeEncryptor encryptor = SafeEncryptorFactory.getInstance(currentMajorVersion, currentMinorVersion, keyStore);
		if(encryptor == null) {
			throw new IllegalArgumentException("not support encrypt version: "+currentMajorVersion+", "+currentMinorVersion);
		}
		return encryptor.encrypt(data, currentKeyId, currentMappingId);
	}

	public static SafeData decryptBytes(byte[] data) throws SafeCryptoException {
		return instance._decrypt(data);
	}
	public static byte[] encryptBytes(byte[] data) throws SafeCryptoException {
		return instance._encrypt(data);
	}
	public static String encryptString(String str) throws SafeCryptoException {
		if(str == null || str.length() == 0) {
			return str;
		}
		byte[] data = str.getBytes(EncodeUtils.Charsets.UTF_8);
		data = instance._encrypt(data);
		return EncodeUtils.encodeBase64ToString(data);
	}
	public static String decryptString(String str) throws SafeCryptoException {
		if(str == null || str.length() == 0) {
			return str;
		}
		byte[] data = EncodeUtils.decodeBase64(str);
		SafeData safeData = instance._decrypt(data);
		if(safeData == null || safeData.getErrorCode() != 0) {
			throw new SafeCryptoException(1101, "decrypt error");
		}
		return new String(safeData.getPlaintextData(), EncodeUtils.Charsets.UTF_8);
	}
}

