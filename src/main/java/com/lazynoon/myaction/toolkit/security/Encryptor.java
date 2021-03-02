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

	private static synchronized void instanceInit() throws SafeCryptoException {
		if (instance == null) {
			instance = new Encryptor();
		}
	}


	protected SafeData _decrypt(byte[] data) throws SafeCryptoException {
		int majorVersion = SafeEncryptorFactory.getMajorVersion(data);
		int minorVersion = SafeEncryptorFactory.getMinorVersion(data);
		SafeEncryptor encryptor = SafeEncryptorFactory.getInstance(majorVersion, minorVersion, keyStore);
		if(encryptor == null) {
			throw new SafeCryptoException(-1, "not support encrypt version: "+majorVersion+", "+minorVersion);
		}
		return encryptor.decrypt(data);
	}

	protected byte[] _encrypt(byte[] data) throws SafeCryptoException {
		SafeEncryptor encryptor = SafeEncryptorFactory.getInstance(currentMajorVersion, currentMinorVersion, keyStore);
		if(encryptor == null) {
			throw new SafeCryptoException(-2, "not support encrypt version: "+currentMajorVersion+", "+currentMinorVersion);
		}
		return encryptor.encrypt(data, currentKeyId, currentMappingId);
	}

	public static SafeData decryptBytes(byte[] data) throws SafeCryptoException {
		if (instance == null) {
			instanceInit();
		}
		return instance._decrypt(data);
	}
	public static byte[] encryptBytes(byte[] data) throws SafeCryptoException {
		if (instance == null) {
			instanceInit();
		}
		return instance._encrypt(data);
	}
	public static String encryptString(String str) throws SafeCryptoException {
		if(str == null || str.length() == 0) {
			return str;
		}
		if (instance == null) {
			instanceInit();
		}
		byte[] data = str.getBytes(EncodeUtils.Charsets.UTF_8);
		data = instance._encrypt(data);
		return EncodeUtils.encodeBase64ToString(data);
	}
	public static String decryptString(String str) throws SafeCryptoException {
		if(str == null || str.length() == 0) {
			return str;
		}
		if (instance == null) {
			instanceInit();
		}
		byte[] data = EncodeUtils.decodeBase64(str);
		SafeData safeData = instance._decrypt(data);
		if(safeData == null || safeData.getErrorCode() != 0) {
			throw new SafeCryptoException(1101, "decrypt error");
		}
		return new String(safeData.getPlaintextData(), EncodeUtils.Charsets.UTF_8);
	}
}

