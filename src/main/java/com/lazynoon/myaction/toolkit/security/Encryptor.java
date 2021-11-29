package com.lazynoon.myaction.toolkit.security;


import com.lazynoon.commons.safesave.*;
import net_io.utils.EncodeUtils;

import java.util.Map;

/**
 * 加解密助手类示例
 *
 * @author Hansen
 */
public class Encryptor {
	private static final int BYTE_SIZE = 256;
	private static Encryptor instance = null;
	private SafeKeyStore keyStore = new SafeKeyStore();
	private int currentMajorVersion = 1;
	private int currentMinorVersion = 0;
	private int currentKeyId = 0;
	private int currentMappingId = 0;

	private Encryptor() throws SafeCryptoException {
		//注册到密钥对象
		Map<Integer, byte[]> mappings = SafeSaveConfig.getCurrentMappingConfig();
		for (Integer mappingId : mappings.keySet()) {
			keyStore.registerByteMapping(mappingId, mappings.get(mappingId));
		}
		currentKeyId = SafeSaveConfig.getCurrentKeyId();
		currentMappingId = SafeSaveConfig.getCurrentMappingId();
		keyStore.registerSecretKey(currentKeyId, SafeSaveConfig.getDataKey(currentKeyId));
	}

	/** 初始化加解密对象 **/
	private static synchronized void instanceInit() throws SafeCryptoException {
		if (instance == null) {
			instance = new Encryptor();
		}
	}


	/**
	 * 以当前实例解密
	 * @param data SafeSave密文
	 * @return SafeData对象
	 * @throws SafeCryptoException
	 */
	protected SafeData _decrypt(byte[] data) throws SafeCryptoException {
		int majorVersion = SafeEncryptorFactory.getMajorVersion(data);
		int minorVersion = SafeEncryptorFactory.getMinorVersion(data);
		SafeEncryptor safeEncryptor = SafeEncryptorFactory.getInstance(majorVersion, minorVersion, keyStore);
		if(safeEncryptor == null) {
			throw new SafeCryptoException(-1, "not support encrypt version: "+majorVersion+", "+minorVersion);
		}
		return safeEncryptor.decrypt(data);
	}


	/**
	 * 以当前实例加密
	 * @param data 明文
	 * @return SafeSave密文
	 * @throws SafeCryptoException
	 */
	protected byte[] _encrypt(byte[] data) throws SafeCryptoException {
		SafeEncryptor safeEncryptor = SafeEncryptorFactory.getInstance(currentMajorVersion, currentMinorVersion, keyStore);
		if(safeEncryptor == null) {
			throw new SafeCryptoException(-2, "not support encrypt version: "+currentMajorVersion+", "+currentMinorVersion);
		}
		return safeEncryptor.encrypt(data, currentKeyId, currentMappingId);
	}

	/**
	 * 加密返回字节数组
	 * @param data 明文字节数组
	 * @return 密文字节数组
	 * @throws SafeCryptoException
	 */
	public static byte[] encryptBytes(byte[] data) throws SafeCryptoException {
		if (instance == null) {
			instanceInit();
		}
		return instance._encrypt(data);
	}

	/**
	 * 解密返回SafeData对象
	 * @param data 密文字节数组
	 * @return SafeData对象
	 * @throws SafeCryptoException
	 */
	public static SafeData decryptSafeData(byte[] data) throws SafeCryptoException {
		if (instance == null) {
			instanceInit();
		}
		return instance._decrypt(data);
	}

	/**
	 * 解密返回字节数组
	 * @param data SafeSave密文字节数组
	 * @return 明文字节数组
	 * @throws SafeCryptoException
	 */
	public static byte[] decryptBytes(byte[] data) throws SafeCryptoException {
		if(data == null || data.length == 0) {
			return data;
		}
		SafeData safeData = decryptSafeData(data);
		if (safeData == null || safeData.getErrorCode() != 0) {
			throw new SafeCryptoException(1101, "decrypt error");
		}
		return safeData.getPlaintextData();
	}

	/**
	 * 解密返回字节数组
	 * @param str BASE64编码的SafeSave密文
	 * @return 明文字节数组
	 * @throws SafeCryptoException
	 */
	public static byte[] decryptBytes(String str) throws SafeCryptoException {
		if (str == null) {
			return null;
		}
		if (str.length() == 0) {
			return new byte[0];
		}
		return decryptBytes(EncodeUtils.decodeBase64(str));
	}

	/**
	 * 加密返回BASE64编码的字符串
	 * @param data 明文字节流
	 * @return ASE64编码的密文
	 * @throws SafeCryptoException
	 */
	public static String encryptString(byte[] data) throws SafeCryptoException {
		if (data == null) {
			return null;
		}
		if (data.length == 0) {
			return "";
		}
		if (instance == null) {
			instanceInit();
		}
		data = instance._encrypt(data);
		return EncodeUtils.encodeBase64ToString(data);
	}

	/**
	 * 加密返回BASE64编码的字符串
	 * @param str 明文字符串（UTF-8）
	 * @return BASE64编码的密文
	 * @throws SafeCryptoException
	 */
	public static String encryptString(String str) throws SafeCryptoException {
		if(str == null || str.isEmpty()) {
			return str;
		}
		byte[] data = str.getBytes(EncodeUtils.Charsets.UTF_8);
		return encryptString(data);
	}

	/**
	 * 解密返回字符串（UTF-8字符集）
	 * @param str BASE64编码的密文
	 * @return 明文字符串（UTF-8）
	 * @throws SafeCryptoException
	 */
	public static String decryptString(String str) throws SafeCryptoException {
		if(str == null || str.isEmpty()) {
			return str;
		}
		byte[] data = decryptBytes(EncodeUtils.decodeBase64(str));
		return new String(data, EncodeUtils.Charsets.UTF_8);
	}
}

