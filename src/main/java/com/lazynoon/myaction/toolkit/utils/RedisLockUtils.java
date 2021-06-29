package com.lazynoon.myaction.toolkit.utils;

import com.lazynoon.myaction.toolkit.cache.RedisUtil;
import myaction.model.ServiceLock;

public class RedisLockUtils {
	public static ServiceLock instance = newInstance();
	
	private static ServiceLock newInstance() {
		return new ServiceLock(new ServiceLock.SignalStorage() {
			
			public void remove(String key) {
				RedisUtil.remove(key);
			}
			
			public long increaseAndGet(String key, long timeout) {
				long num = RedisUtil.increaseAndGet(key);
				if(num == 1) {
					RedisUtil.setPExpire(key, timeout);
				} else {
					long time = RedisUtil.getPExpire(key);
					if(time < 0) {
						RedisUtil.setPExpire(key, timeout);
					}
				}
				return num;
			}
		});
	}
}
