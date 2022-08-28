package com.lazynoon.myaction.toolkit.io.net;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import myaction.utils.LogUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import net_io.utils.Mixed;

/**
 * @version 20191211 Sock5代理，连接内有效的代理账户
 * @version 20191216 GET请求带参
 */

@SuppressWarnings("deprecation")
public class CURL {
	public enum ProxyType {
		DIRECT,
		HTTP,
		SOCK5
	};
	public enum LogLevel {
		DEBUG,
		INFO,
		WARN,
		ERROR
	};
	private long timeout = 30L * 1000L;
	private LinkedHashMap<String, String> headers = null;
	private LinkedHashMap<String, String> cookies = null;
	private String sendEncoding = "UTF-8";
	/** 是否禁用SSL验证 **/
	private boolean disableSSLVerify = false;
	private ProxyType proxyType = ProxyType.DIRECT;
	private InetSocketAddress proxyAddress = null;
	private String proxyUser = null;
	private String proxyPass = null;
	private String cookieSpec = CookieSpecs.STANDARD;

	static {
		try {
			initLogLevel(LogLevel.ERROR); //默认不输出WARN及以下日志
		} catch (Exception e) {
			LogUtil.logError("CURL.initLogLevel", e);
		}
	}

	public static void initLogLevel(LogLevel level) {
		String simpleLogLevel;
		java.util.logging.Level jdkLogLevel;
		if (level == null) {
			throw new IllegalArgumentException("CURL setLogLevel parameter is null");
		} else if (level == LogLevel.DEBUG) {
			simpleLogLevel = "debug";
			jdkLogLevel = java.util.logging.Level.FINE;
		} else if (level == LogLevel.INFO) {
			simpleLogLevel = "info";
			jdkLogLevel = java.util.logging.Level.INFO;
		} else if (level == LogLevel.WARN) {
			simpleLogLevel = "warn";
			jdkLogLevel = java.util.logging.Level.WARNING;
		} else if (level == LogLevel.ERROR) {
			simpleLogLevel = "error";
			jdkLogLevel = java.util.logging.Level.SEVERE;
		} else {
			throw new IllegalArgumentException("CURL setLogLevel parameter not support: " + level);
		}
		//清除缓存（非业务class绑定或初始化时有效）
		org.apache.commons.logging.LogFactory.releaseAll();
		//涉及的主要class
		String[] httpClassNames = {
				ResponseProcessCookies.class.getName()
		};
		//SimpleLog
		String simpleLogKey = "org.apache.commons.logging.simplelog.org.apache.http.client";
		System.setProperty(simpleLogKey, simpleLogLevel);
		//JdkLog
		for (String className : httpClassNames) {
			java.util.logging.Logger.getLogger(className).setLevel(jdkLogLevel);
		}
	}

	public CURL setTimeout(long timeout) {
		timeout = Math.min(timeout, Integer.MAX_VALUE);
		this.timeout = timeout;
		return this;
	}

	public CURL setSendEncoding(String encoding) {
		if(encoding == null || encoding.length() == 0) {
			throw new IllegalArgumentException("encoding is empty");
		}
		this.sendEncoding = encoding;
		return this;
	}

	public CURL addHeader(String name, String value) {
		if(headers == null) {
			headers = new LinkedHashMap<String, String>();
		}
		headers.put(name, value);
		return this;
	}

	public CURL addCookie(String name, String value) {
		if(cookies == null) {
			cookies = new LinkedHashMap<String, String>();
		}
		cookies.put(name, value);
		return this;
	}

	public CURL setProxy(ProxyType proxyType, String proxyHost, int proxyPort) {
		return setProxy(proxyType, proxyHost, proxyPort, null, null);
	}

	public CURL setProxy(ProxyType proxyType, String proxyHost, int proxyPort, String proxyUser, String proxyPass) {
		this.proxyType = proxyType;
		this.proxyAddress = new InetSocketAddress(proxyHost, proxyPort);
		this.proxyUser = proxyUser;
		this.proxyPass = proxyPass;
		return this;
	}

	public String getCookieSpec() {
		return cookieSpec;
	}

	public void setCookieSpec(String cookieSpec) {
		if (cookieSpec == null || cookieSpec.length() == 0) {
			throw new IllegalArgumentException("cookieSpec must be not empty");
		}
		this.cookieSpec = cookieSpec;
	}

	public Result get(String url, Map<String, String> params) throws IOException {
		if(params == null || params.size() == 0) {
			return get(url);
		}
		StringBuilder build = new StringBuilder();
		if(url.indexOf('?') < 0) {
			build.append("?");
		} else {
			build.append("&");
		}
		build.append(buildQuery(params));
		url += build.toString();
		return get(url);
	}

	public Result get(String url, Mixed params) throws IOException {
		if(params == null || params.size() == 0) {
			return get(url);
		}
		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		for(String key : params.keys()) {
			map.put(key, params.getString(key));
		}
		return get(url, map);
	}

	public Result get(String url) throws IOException {
		return _request(new HttpGet(url));
	}

	public Result put(String url) throws IOException {
		return _request(new HttpPut(url));
	}

	public Result delete(String url) throws IOException {
		return _request(new HttpDelete(url));
	}

	private Result _request(HttpRequestBase httpRequest) throws IOException {
		int timeoutInt = (int)timeout;
		CloseableHttpClient httpclient = getHttpClient();
		//设置请求和传输超时时间
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(timeoutInt).setConnectTimeout(timeoutInt).build();
		httpRequest.setConfig(requestConfig);
		if(headers != null) {
			for(String key : headers.keySet()) {
				httpRequest.setHeader(key, headers.get(key));
			}
		}
		CloseableHttpResponse response = httpclient.execute(httpRequest);

		byte[] content = null;
		try {
			HttpEntity entity = response.getEntity();
			content = EntityUtils.toByteArray(entity);
			// do something useful with the response body
			// and ensure it is fully consumed
			EntityUtils.consume(entity);
		} finally {
			response.close();
		}
		int code = response.getStatusLine().getStatusCode();
		return new Result(code, response.getAllHeaders(), content);
	}

	public Result post(String url, byte[] data) throws ParseException, IOException {
		int timeoutInt = (int)timeout;
		CloseableHttpClient httpclient = getHttpClient();
		HttpPost httpPost = new HttpPost(url);
		//设置请求和传输超时时间
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(timeoutInt).setConnectTimeout(timeoutInt).build();
		httpPost.setConfig(requestConfig);
		if(headers != null) {
			for(String key : headers.keySet()) {
				httpPost.setHeader(key, headers.get(key));
			}
		}
		httpPost.setEntity(new ByteArrayEntity(data));
		CloseableHttpResponse response = httpclient.execute(httpPost);

		byte[] content = null;
		try {
			HttpEntity entity = response.getEntity();
			content = EntityUtils.toByteArray(entity);
			// do something useful with the response body
			// and ensure it is fully consumed
			EntityUtils.consume(entity);
		} finally {
			response.close();
		}
		int code = response.getStatusLine().getStatusCode();
		return new Result(code, response.getAllHeaders(), content);
	}
	public Result post(String url, Map<String, String>params) throws ParseException, IOException {
		int timeoutInt = (int)this.timeout;
		CloseableHttpClient httpclient = getHttpClient();
		HttpPost httpPost = new HttpPost(url);
		//设置请求和传输超时时间
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(timeoutInt)
				.setConnectTimeout(timeoutInt)
				.setCookieSpec(this.cookieSpec)
				.build();
		httpPost.setConfig(requestConfig);
		if(headers != null) {
			for(String key : headers.keySet()) {
				httpPost.setHeader(key, headers.get(key));
			}
		}
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		for (String key : params.keySet()) {
			String value = params.get(key);
			nvps.add(new BasicNameValuePair(key, value));
		}
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, sendEncoding));
		CloseableHttpResponse response = httpclient.execute(httpPost);

		byte[] content = null;
		try {
			HttpEntity entity = response.getEntity();
			content = EntityUtils.toByteArray(entity);
			// do something useful with the response body
			// and ensure it is fully consumed
			EntityUtils.consume(entity);
		} finally {
			response.close();
		}
		int code = response.getStatusLine().getStatusCode();
		return new Result(code, response.getAllHeaders(), content);
	}

	public void setDisableSSLVerify(boolean disableSSLVerify) {
		this.disableSSLVerify = disableSSLVerify;
	}

	public boolean isDisableSSLVerify() {
		return this.disableSSLVerify;
	}

	public String buildQuery(Mixed params) throws UnsupportedEncodingException {
		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		for(String key : params.keys()) {
			map.put(key, params.getString(key));
		}
		return buildQuery(map);
	}

	public String buildQuery(Map<String, String> params) throws UnsupportedEncodingException {
		StringBuilder build = new StringBuilder();
		boolean first = true;
		for(String key : params.keySet()) {
			if(first) {
				first = false;
			} else {
				build.append("&");
			}
			build.append(URLEncoder.encode(key, sendEncoding));
			build.append("=");
			String value = params.get(key);
			if(value == null) {
				value = "";
			}
			build.append(URLEncoder.encode(value, sendEncoding));
		}
		return build.toString();
	}

	private CloseableHttpClient getHttpClient() throws IOException {
		try {
			HttpClientBuilder httpClientBuilder = HttpClients.custom();
			SSLConnectionSocketFactory sslConnectionSocketFactory;
			if(disableSSLVerify) {
				SSLContext context = SSLContext.getInstance("SSL");
				context.init(null, new TrustManager[] { new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] paramArrayOfX509Certificate, String paramString)
							throws CertificateException {
						//System.out.println("paramString: "+paramString);
					}

					public void checkServerTrusted(X509Certificate[] paramArrayOfX509Certificate, String paramString)
							throws CertificateException {
						//System.out.println("paramString2: "+paramString);
					}

					public X509Certificate[] getAcceptedIssuers() {
						//System.out.println("getAcceptedIssuers: null");
						return null;
					}

				}}, new SecureRandom());

				HostnameVerifier verifier = new HostnameVerifier() {
					public boolean verify(String hostname, SSLSession session) {
						//System.out.println("hostname: " + hostname);
						return true;
					}
				};
				sslConnectionSocketFactory = new MySSLConnectionSocketFactory(context, verifier);
			} else {
				sslConnectionSocketFactory = new MySSLConnectionSocketFactory(SSLContexts.createSystemDefault());
			}
			httpClientBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
			Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory> create()
					.register("http", new MyConnectionSocketFactory())
					.register("https", sslConnectionSocketFactory).build();
			PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
			httpClientBuilder.setConnectionManager(cm);
			return new MyHttpClient(httpClientBuilder.build());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("NoSuchAlgorithmException - " + e.getMessage());
		} catch (KeyManagementException e) {
			throw new IOException("KeyManagementException - " + e.getMessage());
		}

	}

	public static class ThreadLocalAuthenticator extends Authenticator {
		private static ThreadLocal<PasswordAuthentication> credentials = new ThreadLocal<PasswordAuthentication>();
		private static ThreadLocalAuthenticator instance = new ThreadLocalAuthenticator();
		static {
			Authenticator.setDefault(instance);
		}

		@Override
		protected PasswordAuthentication getPasswordAuthentication() {
			return credentials.get();
		}

		public static PasswordAuthentication getCredential() {
			return credentials.get();
		}

		public static void setCredential(String user, String pass) {
			credentials.set(new PasswordAuthentication(user, pass.toCharArray()));
		}

		public static void setCredential(PasswordAuthentication auth) {
			credentials.set(auth);
		}

	}

	public static class Result {
		private int httpCode;
		private Header[] headers;
		private byte[] bytes;
		private Result(int code, Header[] headers, byte[] data) {
			if(headers == null) {
				headers = new Header[0];
			}
			this.httpCode = code;
			this.headers = headers;
			this.bytes = data;
		}

		public int getHttpCode() {
			return this.httpCode;
		}

		public byte[] getBodyBytes() {
			return this.bytes;
		}

		public String getBodyString() {
			try {
				return new String(bytes, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new IllegalArgumentException(e);
			}
		}

		public String getBodyString(Charset charset) {
			if(bytes == null) {
				return null;
			}
			return new String(bytes, charset);
		}

		public String getBodyString(String charsetName) throws UnsupportedEncodingException {
			if(bytes == null) {
				return null;
			}
			return new String(bytes, charsetName);
		}

		public Header getFirstHeader(String name) {
			for(int i=0; i<headers.length; i++) {
				if(name.equalsIgnoreCase(headers[i].getName())) {
					return headers[i];
				}
			}
			return null;
		}
		public Header getLastHeader(String name) {
			for(int i=headers.length-1; i>=0; i--) {
				if(name.equalsIgnoreCase(headers[i].getName())) {
					return headers[i];
				}
			}
			return null;
		}
		public String getFirstHeadValue(String name) {
			Header header = getFirstHeader(name);
			if(header == null) {
				return null;
			}
			return header.getValue();
		}
		public String getLastHeadValue(String name) {
			Header header = getLastHeader(name);
			if(header == null) {
				return null;
			}
			return header.getValue();
		}

		public Header[] getHeaders(String name) {
			ArrayList<Header> list = new ArrayList<Header>();
			for(int i=headers.length-1; i>=0; i--) {
				if(name.equalsIgnoreCase(headers[i].getName())) {
					list.add(headers[i]);
				}
			}
			return list.toArray(new Header[0]);
		}

		public Header[] getAllHeaders() {
			Header[] copis = new Header[headers.length];
			System.arraycopy(headers, 0, copis, 0, headers.length);
			return copis;
		}

	}

	private final class MyConnectionSocketFactory extends PlainConnectionSocketFactory {

		@Override
		public Socket createSocket(HttpContext context) throws IOException {
			if(proxyType == ProxyType.SOCK5) {
				return new Socket(new Proxy(Proxy.Type.SOCKS, proxyAddress));
			} else if(proxyType == ProxyType.HTTP) {
				return new Socket(new Proxy(Proxy.Type.HTTP, proxyAddress));
			} else {
				return super.createSocket(context);
			}
		}

		@Override
		public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
			if(proxyType == ProxyType.SOCK5 || proxyType == ProxyType.HTTP) {
				InetSocketAddress unresolvedRemote = InetSocketAddress.createUnresolved(host.getHostName(), remoteAddress.getPort());
				return super.connectSocket(connectTimeout, sock, host, unresolvedRemote, localAddress, context);
			} else {
				return super.connectSocket(connectTimeout, sock, host, remoteAddress, localAddress, context);
			}
		}
	}

	private final class MySSLConnectionSocketFactory extends SSLConnectionSocketFactory {
		MySSLConnectionSocketFactory(SSLContext sslContext) {
			super(sslContext);
		}

		MySSLConnectionSocketFactory(SSLContext sslContext, HostnameVerifier hostnameVerifier) {
			super(sslContext, hostnameVerifier);
		}

		@Override
		public Socket createSocket(HttpContext context) throws IOException {
			if(proxyType == ProxyType.SOCK5) {
				return new Socket(new Proxy(Proxy.Type.SOCKS, proxyAddress));
			} else {
				return super.createSocket(context);
			}
		}

		@Override
		public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
			if(proxyType == ProxyType.SOCK5) {
				InetSocketAddress unresolvedRemote = InetSocketAddress.createUnresolved(host.getHostName(), remoteAddress.getPort());
				return super.connectSocket(connectTimeout, sock, host, unresolvedRemote, localAddress, context);
			} else {
				return super.connectSocket(connectTimeout, sock, host, remoteAddress, localAddress, context);
			}
		}
	}


	private class MyHttpClient extends CloseableHttpClient {
		private CloseableHttpClient httpClient;

		MyHttpClient(CloseableHttpClient httpclient) {
			this.httpClient = httpclient;
		}


		@Override
		protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
			PasswordAuthentication prevCredential = ThreadLocalAuthenticator.getCredential();
			if (proxyType != ProxyType.DIRECT) {
				if (proxyUser != null) {
					ThreadLocalAuthenticator.setCredential(proxyUser, proxyPass);
				}
			}
			try {
				return httpClient.execute(target, request, context);
			} finally {
				if (proxyType != ProxyType.DIRECT && proxyUser != null) {
					ThreadLocalAuthenticator.setCredential(prevCredential);;
				}
			}
		}

		@Override
		public void close() throws IOException {
			httpClient.close();
		}

		@Override
		public HttpParams getParams() {
			return httpClient.getParams();
		}

		@Override
		public ClientConnectionManager getConnectionManager() {
			return httpClient.getConnectionManager();
		}
	}


}

