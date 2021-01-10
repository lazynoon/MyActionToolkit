package com.lazynoon.myaction.toolkit.security;

import java.util.Map;

abstract public class ByteMappingBaseBean {
	abstract public Map<Integer, byte[]> getByteMappingConfig();
	abstract public boolean isProductionClass();
}
