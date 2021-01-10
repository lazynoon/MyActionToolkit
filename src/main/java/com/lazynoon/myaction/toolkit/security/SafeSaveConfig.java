package com.lazynoon.myaction.toolkit.security;

import com.lazynoon.commons.safesave.SafeCryptoException;
import myaction.extend.AppConfig;
import myaction.extend.MiddlewareClient;
import net_io.myaction.CheckException;
import net_io.myaction.tool.crypto.AES;
import net_io.myaction.tool.crypto.RSA;
import net_io.myaction.tool.exception.CryptoException;
import net_io.utils.DateUtils;
import net_io.utils.EncodeUtils;
import net_io.utils.Mixed;
import net_io.utils.MixedUtils;

import java.io.IOException;
import java.util.Map;

public class SafeSaveConfig {
	public static final int PRODUCTION_MIN_KEY_ID = 1000;
	public static final int PRODUCTION_MIN_MAPPING_ID = 10;
	private static String encryptKeyPrefix = "KtL0lO55i3rhAMbS";
	private static RSA privateRSA = null;
	private static long TIMEOUT = 28 * 1000;
	private static String NAME_ENV = "myaction.env";
	public static String NAME_KEK_DSN = "myaction.safesave.kek.dsn";
	public static String NAME_KEK_RSA = "myaction.safesave.kek.rsa";
	public static String NAME_KEK_AES = "myaction.safesave.kek.aes";
	public static String NAME_DEK_KEY_ID = "myaction.safesave.dek.key.id";
	public static String NAME_DEK_MAPPING_CLASS = "myaction.safesave.dek.mapping.class";
	public static String NAME_DEK_MAPPING_ID = "myaction.safesave.dek.mapping.id";


	public static boolean isProductionEnv() {
		return "production".equalsIgnoreCase(AppConfig.getProperty("myaction.env"));
	}

	public static Map<Integer, byte[]> getCurrentMappingConfig() throws SafeCryptoException {
		String mappingClassName = AppConfig.getProperty(NAME_DEK_MAPPING_CLASS);
		if (MixedUtils.isEmpty(mappingClassName)) {
			throw new SafeCryptoException(601, "undefined byte mapping class: " + NAME_DEK_MAPPING_CLASS);
		}
		try {
			Class clazz = Class.forName(mappingClassName);
			ByteMappingBaseBean bean = (ByteMappingBaseBean) clazz.newInstance();
			if (isProductionEnv()) {
				if (!bean.isProductionClass()) {
					throw new SafeCryptoException(601, "Byte Mapping Bean must production class");
				}
			} else {
				if (bean.isProductionClass()) {
					throw new SafeCryptoException(601, "Byte Mapping Bean must not production class");
				}
			}
			return bean.getByteMappingConfig();
		} catch (ClassNotFoundException e) {
			throw new SafeCryptoException(602, "Byte Mapping Bean class not found: " + mappingClassName);
		} catch (InstantiationException e) {
			throw new SafeCryptoException(603, "ByteMappingExBean declare error. InstantiationException: " + e.getMessage());
		} catch (IllegalAccessException e) {
			throw new SafeCryptoException(604, "ByteMappingExBean declare error. IllegalAccessException: " + e.getMessage());
		}
	}

	public static int getCurrentKeyId() throws SafeCryptoException {
		String keyId = AppConfig.getProperty(NAME_DEK_KEY_ID);
		if (MixedUtils.isEmpty(keyId)) {
			throw new SafeCryptoException(605, "undefined key id: " + NAME_DEK_KEY_ID);
		}
		int id = MixedUtils.parseInt(keyId);
		if (id <= 0) {
			throw new SafeCryptoException(605, "key id must greater then 0");
		}
		if (isProductionEnv()) {
			if (id < PRODUCTION_MIN_KEY_ID) {
				throw new SafeCryptoException(605, "min key id is " + PRODUCTION_MIN_KEY_ID);
			}
		} else {
			if (id >= PRODUCTION_MIN_KEY_ID) {
				throw new SafeCryptoException(605, "max key id is " + (PRODUCTION_MIN_KEY_ID - 1));
			}
		}
		return id;
	}

	public static int getCurrentMappingId() throws SafeCryptoException {
		String mappingId = AppConfig.getProperty(NAME_DEK_MAPPING_ID);
		if (MixedUtils.isEmpty(mappingId)) {
			throw new SafeCryptoException(605, "undefined byte mapping id: " + NAME_DEK_MAPPING_ID);
		}
		int id = MixedUtils.parseInt(mappingId);
		if (id <= 0) {
			throw new SafeCryptoException(605, "mapping id must greater then 0");
		}
		if (isProductionEnv()) {
			if (id < PRODUCTION_MIN_MAPPING_ID) {
				throw new SafeCryptoException(605, "min mapping id is " + PRODUCTION_MIN_MAPPING_ID);
			}
		} else {
			if (id >= PRODUCTION_MIN_MAPPING_ID) {
				throw new SafeCryptoException(605, "max mapping id is " + (PRODUCTION_MIN_MAPPING_ID - 1));
			}
		}
		return id;
	}

	private static String getAesForKEK() throws SafeCryptoException {
		String rsaKey = AppConfig.getProperty(NAME_KEK_AES);
		if (MixedUtils.isEmpty(rsaKey)) {
			throw new SafeCryptoException(605, "undefined kek rsa key: " + NAME_KEK_RSA);
		}
		return rsaKey;
	}

	private static String getRsaForKEK() throws SafeCryptoException {
		String rsaKey = AppConfig.getProperty(NAME_KEK_RSA);
		if (MixedUtils.isEmpty(rsaKey)) {
			throw new SafeCryptoException(605, "undefined kek rsa key: " + NAME_KEK_RSA);
		}
		return rsaKey;
	}

	public static byte[] getDataKey(int keyId) throws SafeCryptoException {
		String rsaKey = getRsaForKEK();
		String aesKey = getAesForKEK();
		try {
			rsaKey = KEKUtils.decryptKey(rsaKey, aesKey);
		} catch (CryptoException e) {
			throw new SafeCryptoException(605, "[CryptoException] "+ e.getMessage());
		}
		String kekDsn = AppConfig.getProperty(NAME_KEK_DSN);
		if (MixedUtils.isEmpty(kekDsn)) {
			throw new SafeCryptoException(605, "undefined key: " + NAME_KEK_DSN);
		}
		try {
			MiddlewareClient kekClient = MiddlewareClient.instance(kekDsn);
			Mixed args = new Mixed();
			args.put("key_id", keyId);
			Mixed result = kekClient.request("safesave/dek.get", args, TIMEOUT);
			if(result.getInt("error") != 0) {
				throw new SafeCryptoException(606, "Can not get DEK. error: " + result.getInt("error")
						+ ", reason: " + result.getString("reason"));
			}
			Mixed data = result.get("data");
			String dek = data.getString("dek");
			RSA rsa = new RSA(RSA.KeyMode.PRIVATE_KEY, rsaKey);
			return rsa.decrypt(dek.getBytes(EncodeUtils.Charsets.UTF_8));
		} catch (IOException e) {
			throw new SafeCryptoException(607, "Can not get KEK. IOException: " + e.getMessage());
		} catch (CryptoException e) {
			throw new SafeCryptoException(605, "[CryptoException] "+ e.getMessage());
		}
	}

}
