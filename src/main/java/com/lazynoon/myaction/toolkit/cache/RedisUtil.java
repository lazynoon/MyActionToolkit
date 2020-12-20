package com.lazynoon.myaction.toolkit.cache;

import myaction.extend.AppConfig;
import myaction.model.Subscriber;
import net_io.utils.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisUtil {

	private static JedisPool pool = null;  
	private static JedisPoolConfig config = new JedisPoolConfig();
	private static String subscribeChannel = "RedisDefaultChannel";
	private static boolean subscribeThreadStarted = false;
	private static ConcurrentHashMap<String, Subscriber> subscriberFactory = new ConcurrentHashMap<String, Subscriber>();
	private static int timeout = 15000;
	static {
		//最大分配的对象数
		config.setMaxTotal(Integer.valueOf(1024));
		//最大能够保持idel状态的对象数
		config.setMaxIdle(Integer.valueOf(32));  
		//池内没有返回对象时，最大等待时间
		config.setMaxWaitMillis(Long.valueOf(5000));
		//当调用borrow Object方法时，是否进行有效性检查
		config.setTestOnBorrow(Boolean.valueOf(true));  
		//当调用return Object方法时，是否进行有效性检查
		config.setTestOnReturn(Boolean.valueOf(true));
	}
	
	private static JedisPool instance() {
		if(pool != null) {
			return pool;
		}
		String host = AppConfig.getProperty("myaction.redis.host");
		int port = MixedUtils.parseInt(AppConfig.getProperty("myaction.redis.port"));
		if(MixedUtils.isEmpty(host) || port <= 0) {
			throw new IllegalArgumentException("[config.properties] miss key myaction.redis.host/myaction.redis.port");
		}
		String password = AppConfig.getProperty("myaction.redis.pass");
		if(MixedUtils.isEmpty(password)) {
			pool = new JedisPool(config, host, port, timeout);
		} else {
			pool = new JedisPool(config, host, port, timeout, password);
		}
		return pool;
	}

	public static Mixed get(String key) throws JSONException {
		Jedis redis = instance().getResource();
		try {
			String str = redis.get(key);
			if(str == null) {
				return null;
			}
			return JSONUtils.parseJSON(str);
		} finally {
			redis.close();
		}
	}
	
	public static Map<String, Mixed> mget(List<String>keys) throws JSONException {
		Jedis redis = instance().getResource();
		try {
			List<String> list = redis.mget(keys.toArray(new String[keys.size()]));
			if(list == null || keys.size() != list.size()) {
				return null;
			}
			HashMap<String, Mixed> map = new HashMap<String, Mixed>(); 
			for(int i=0; i<keys.size(); i++) {
				String str = list.get(i);
				if(str == null) {
					continue;
				}
				map.put(keys.get(i), JSONUtils.parseJSON(str));
			}
			return map;
		} finally {
			redis.close();
		}
	}
	
	/**
	 * 
	 * @param key
	 * @param data
	 * @param timeout 单位：秒
	 */
	public static void set(String key, Mixed data, int timeout) {
		Jedis redis = instance().getResource();
		try {
			String str = data.toJSON();
			redis.setex(key, timeout, str);
		} finally {
			redis.close();
		}
	}
	
	public static void remove(String key) {
		Jedis redis = instance().getResource();
		try {
			redis.del(key);
		} finally {
			redis.close();
		}		
	}
	
	public static long increaseAndGet(String key) {
		Jedis redis = instance().getResource();
		try {
			Long num = redis.incr(key);
			if(num == null) {
				return 0;
			}
			return num.longValue();
		} finally {
			redis.close();
		}
	}
	
	/** 设置过期时间（单位：秒） **/
	public static void setExpire(String key, int seconds) {
		Jedis redis = instance().getResource();
		try {
			redis.expire(key, seconds);
		} finally {
			redis.close();
		}
	}
	
	/**
	 * 获取过期时间（单位：秒）
	 * @return 当 key 不存在时，返回 -2 。 当 key 存在但没有设置剩余生存时间时，返回 -1 。 否则，以秒为单位，返回 key 的剩余生存时间。
	 */
	public static int getExpire(String key) {
		Jedis redis = instance().getResource();
		try {
			Long obj = redis.ttl(key);
			if(obj == null) {
				return -2;
			}
			return obj.intValue();
		} finally {
			redis.close();
		}
	}
	
	
	/** 设置过期时间（单位：毫秒） **/
	public static void setPExpire(String key, long millisecond) {
		Jedis redis = instance().getResource();
		try {
			redis.pexpire(key, millisecond);
		} finally {
			redis.close();
		}
	}
	
	/**
	 * 获取过期时间（单位：毫秒）
	 * @return 当 key 不存在时，返回 -2 。 当 key 存在但没有设置剩余生存时间时，返回 -1 。 否则，以毫秒为单位，返回 key 的剩余生存时间。
	 */
	public static long getPExpire(String key) {
		Jedis redis = instance().getResource();
		try {
			Long obj = redis.pttl(key);
			if(obj == null) {
				return -2;
			}
			return obj.longValue();
		} finally {
			redis.close();
		}
	}
	
	
	public static void publish(String path, Mixed data) {
		Jedis redis = instance().getResource();
		try {
			Mixed info = new Mixed();
			info.put("path", path);
			info.put("data", data);
			redis.publish(subscribeChannel, info.toJSON());
		} finally {
			redis.close();
		}
	}
	
	public static void subscribe(String path, Subscriber subscriber) {
		//创建到订阅者处理工厂
		path = getPath(path);
		subscriberFactory.put(path, subscriber);
		//首次注册，启动线程
		if(subscribeThreadStarted == false) {
			startSubscribeThread(new JedisPubSub() {
			    public void onMessage(String channel, String message) {
			    	try {
						Mixed info = JSONUtils.parseJSON(message);
						if(info == null) {
							NetLog.logInfo("Unkown subscribe message: "+message);
						}
						String path = getPath(info.getString("path"));
						Mixed data = info.get("data");
						Subscriber subscriber = subscriberFactory.get(path);
						if(subscriber != null) {
							subscriber.onMessage(path, data);
						} else {
							NetLog.logDebug("miss subscriber message. path: "+path+", message: "+message);
						}
					} catch (Exception e) {
						NetLog.logWarn(e);
					}
			    }

			    public void onSubscribe(String channel, int subscribedChannels) {
			    	NetLog.logInfo(String.format("subscribe redis channel success, channel %s, subscribedChannels %d", 
			                channel, subscribedChannels));
			    }

			    public void onUnsubscribe(String channel, int subscribedChannels) {
			        NetLog.logInfo(String.format("unsubscribe redis channel, channel %s, subscribedChannels %d", 
			                channel, subscribedChannels));
			    }
			});
		}
	}
	
	private static String getPath(String path) {
		if(path == null) {
			return path;
		}
		path = path.trim().toLowerCase();
		return path;
	}
	
	
	synchronized private static void startSubscribeThread(final JedisPubSub jedisPubSub) {
		if(subscribeThreadStarted) {
			return;
		}
		subscribeThreadStarted = true;
		Thread subscribeThread = new Thread() {
			@Override
			public void run() {
				while(true) {
					try {
						Jedis redis = instance().getResource();
						try {
							redis.subscribe(jedisPubSub, subscribeChannel);
						} finally {
							redis.close();
						}
					} catch(Exception e) {
						NetLog.logError(e);
					}
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						NetLog.logError(e);
					}
				}
			}
		};
		subscribeThread.setDaemon(true);
		subscribeThread.setName("subscribe-"+subscribeChannel);
		subscribeThread.start();
	}
	

}
