package com.lazynoon.myaction.toolkit.security;

import net_io.utils.EncodeUtils;

import java.util.LinkedHashMap;
import java.util.Map;

abstract public class ByteMappingBaseBean {
	abstract public boolean isProductionClass();
	abstract public Map<Integer, String> getByteMappingString();

	public Map<Integer, byte[]> getByteMappingConfig() {
		LinkedHashMap<Integer, byte[]> result = new LinkedHashMap<Integer, byte[]>();
		Map<Integer, String> encodedMap = getByteMappingString();
		for (Integer key : encodedMap.keySet()) {
			result.put(key, EncodeUtils.myBase62Decode(encodedMap.get(key)));
		}
		return result;
	}
}
