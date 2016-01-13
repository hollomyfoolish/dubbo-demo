package org.dubbo.extension;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.dubbo.util.StringUtils;
import org.dubbo.util.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.com.caucho.hessian.HessianException;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.http.HttpBinder;
import com.alibaba.dubbo.remoting.http.HttpHandler;
import com.alibaba.dubbo.remoting.http.HttpServer;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.AbstractProxyProtocol;
import com.caucho.hessian.client.AbstractHessianConnection;
import com.caucho.hessian.client.AbstractHessianConnectionFactory;
import com.caucho.hessian.client.HessianConnection;
import com.caucho.hessian.client.HessianConnectionException;
import com.caucho.hessian.client.HessianConnectionFactory;
import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.io.HessianMethodSerializationException;
import com.caucho.hessian.server.HessianSkeleton;

public class HessianHttpProtocol extends AbstractProxyProtocol {

	private static final Logger logger = LoggerFactory.getLogger(HessianHttpProtocol.class);

	private final Map<String, HttpServer> serverMap = new ConcurrentHashMap<String, HttpServer>();

	private final Map<String, HessianSkeleton> skeletonMap = new ConcurrentHashMap<String, HessianSkeleton>();

	private HttpBinder httpBinder;

	private HessianProxyFactory httpClientProxyFactory;

	private HessianProxyFactory urlConnectionProxyFactory;

	private int connectionTimeout = 5000;

	private int readTimeout = 5000;

	private static final String TENANT_ID_KEY = "tenant-id";

	public HessianHttpProtocol() {
		super(HessianException.class);
		initHessianProxyFactory();
	}

	public void setHttpBinder(HttpBinder httpBinder) {
		this.httpBinder = httpBinder;
	}

	public int getDefaultPort() {
		return 80;
	}

	private class HessianHandler implements HttpHandler {

		public void handle(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			String uri = request.getRequestURI();
			HessianSkeleton skeleton = skeletonMap.get(uri);
			if (!request.getMethod().equalsIgnoreCase("POST")) {
				response.setStatus(500);
			} else if (skeleton == null) {
				logger.error("no matching service is found for url {}", uri);
				response.setStatus(404);
			} else {
				RpcContext.getContext().setRemoteAddress(request.getRemoteAddr(), request.getRemotePort());
				try {
					skeleton.invoke(request.getInputStream(), response.getOutputStream());
				} catch (Throwable e) {
					throw new ServletException(e);
				}
			}
		}

	}

	protected <T> Runnable doExport(T impl, Class<T> type, URL url) throws RpcException {
		String addr = url.getIp() + ":" + url.getPort();
		HttpServer server = serverMap.get(addr);
		if (server == null) {
			server = httpBinder.bind(url, new HessianHandler());
			serverMap.put(addr, server);
		}
		final String path = url.getAbsolutePath();
		HessianSkeleton skeleton = new HessianSkeleton(impl, type);
		skeletonMap.put(path, skeleton);
		logger.info("Service {} (interface {}) exported to url {}", impl, url.getServiceInterface(), path);

		return new Runnable() {
			public void run() {
				skeletonMap.remove(path);
			}
		};
	}

	@SuppressWarnings("unchecked")
	protected <T> T doRefer(Class<T> serviceType, URL url) throws RpcException {
		HessianProxyFactory hessianProxyFactory = this.getHessianProxyFactory(url);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (url.getProtocol().equals("hessian-https")) {
			return (T) hessianProxyFactory.create(serviceType, url.setProtocol("https").toJavaURL(), cl);
		} else {
			return (T) hessianProxyFactory.create(serviceType, url.setProtocol("http").toJavaURL(), cl);
		}
	}

	protected void initHessianProxyFactory() {
		httpClientProxyFactory = new HessianProxyFactory();
		httpClientProxyFactory.setOverloadEnabled(true);
		HttpClientConnectionFactory httpClientConnectionFactory = new HttpClientConnectionFactory();
		httpClientConnectionFactory.setHessianProxyFactory(httpClientProxyFactory);
		httpClientProxyFactory.setConnectionFactory(httpClientConnectionFactory);

		urlConnectionProxyFactory = new HessianProxyFactory();
		urlConnectionProxyFactory.setOverloadEnabled(true);
		HessianConnectionFactory hessian2urlConnectionFactory = new HessianHttpURLConnectionFactory();
		hessian2urlConnectionFactory.setHessianProxyFactory(urlConnectionProxyFactory);

		if (this.connectionTimeout > 0) {
			httpClientProxyFactory.setConnectTimeout(this.connectionTimeout);
			urlConnectionProxyFactory.setConnectTimeout(this.connectionTimeout);
		}
		if (this.readTimeout > 0) {
			httpClientProxyFactory.setReadTimeout(this.readTimeout);
			urlConnectionProxyFactory.setReadTimeout(this.readTimeout);
		}
	}

	protected HessianProxyFactory getHessianProxyFactory(URL url) {
		String client = url.getParameter(Constants.CLIENT_KEY, Constants.DEFAULT_HTTP_CLIENT);
		logger.debug("Reference service {} via client {}", url.getServiceInterface(), client);
		if ("httpclient".equals(client)) {
			return httpClientProxyFactory;
		} else if (client != null && client.length() > 0 && !Constants.DEFAULT_HTTP_CLIENT.equals(client)) {
			logger.debug("unsupportted client " + client + ", use default");
		}
		return urlConnectionProxyFactory;
	}

	protected int getErrorCode(Throwable e) {
		if (e instanceof HessianConnectionException) {
			if (e.getCause() != null) {
				Class<?> cls = e.getCause().getClass();
				if (SocketTimeoutException.class.equals(cls)) {
					return RpcException.TIMEOUT_EXCEPTION;
				}
			}
			return RpcException.NETWORK_EXCEPTION;
		} else if (e instanceof HessianMethodSerializationException) {
			return RpcException.SERIALIZATION_EXCEPTION;
		}
		return super.getErrorCode(e);
	}

	public void destroy() {
		super.destroy();
		for (String key : new ArrayList<String>(serverMap.keySet())) {
			HttpServer server = serverMap.remove(key);
			if (server != null) {
				try {
					if (logger.isInfoEnabled()) {
						logger.info("Close hessian server " + server.getUrl());
					}
					server.close();
				} catch (Throwable t) {
					logger.warn(t.getMessage(), t);
				}
			}
		}
	}

	static class HessianHttpURLConnectionFactory extends AbstractHessianConnectionFactory {

		private static final Logger LOGGER = LoggerFactory.getLogger(HessianHttpURLConnectionFactory.class);

		private int getReadTimeout(java.net.URL url) {
			String value = URLUtils.getQueryParameter(url, Constants.TIMEOUT_KEY);
			if (StringUtils.isNotEmpty(value)) {
				try {
					return Integer.parseInt(value);
				} catch (NumberFormatException ex) {
					LOGGER.warn("invalid timeout value, {} is not an integer", value);
					return (int) this.getHessianProxyFactory().getConnectTimeout();
				}
			} else {
				return (int) this.getHessianProxyFactory().getConnectTimeout();
			}
		}

		public HessianConnection open(java.net.URL url) throws IOException {
			URLConnection conn = url.openConnection();
			if (conn instanceof HttpsURLConnection) {
				HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
				try {
					SSLContext c = SSLContexts.custom().loadTrustMaterial(null, TrustAnyStrategy.INSTANCE).build();
					httpsConn.setSSLSocketFactory(c.getSocketFactory());
					httpsConn.setHostnameVerifier(IgnoreHostnameVerifier.INSTANCE);
				} catch (Exception ex) {
					throw new RpcException("unable to open connection to " + url, ex);
				}
			}
			conn.setDoOutput(true);
			HessianHttpURLConnection connection = new HessianHttpURLConnection(url, conn);
			connection.setConnectionTimeout((int) this.getHessianProxyFactory().getConnectTimeout());
			connection.setReadTimeout(this.getReadTimeout(url));
			connection.addHeader(TENANT_ID_KEY, String.valueOf(1));

			return connection;
		}
	}

	static class HessianHttpURLConnection extends AbstractHessianConnection {

		private java.net.URL url;

		private URLConnection conn;

		private int statusCode;

		private String statusMessage;

		public HessianHttpURLConnection(java.net.URL url, URLConnection conn) {
			this.url = url;
			this.conn = conn;
		}

		public void setReadTimeout(int readTimeout) {
			if (readTimeout > 0) {
				conn.setReadTimeout(readTimeout);
			}
		}

		public void setConnectionTimeout(int connectionTimeout) {
			if (connectionTimeout > 0) {
				conn.setConnectTimeout(connectionTimeout);
			}
		}

		@Override
		public void addHeader(String key, String value) {
			conn.setRequestProperty(key, value);
		}

		public OutputStream getOutputStream() throws IOException {
			return conn.getOutputStream();
		}

		public void sendRequest() throws IOException {
			if (conn instanceof HttpURLConnection) {
				HttpURLConnection httpConn = (HttpURLConnection) conn;
				statusCode = 500;
				statusCode = httpConn.getResponseCode();
				parseResponseHeaders(httpConn);
				InputStream is = null;

				if (statusCode != 200) {
					StringBuffer sb = new StringBuffer();
					int ch;

					try {
						is = httpConn.getInputStream();

						if (is != null) {
							while ((ch = is.read()) >= 0)
								sb.append((char) ch);

							is.close();
						}

						is = httpConn.getErrorStream();
						if (is != null) {
							while ((ch = is.read()) >= 0)
								sb.append((char) ch);
						}

						statusMessage = sb.toString();
					} catch (FileNotFoundException e) {
						throw new HessianConnectionException("HessianProxy cannot connect to '" + url, e);
					} catch (IOException e) {
						if (is == null)
							throw new HessianConnectionException(statusCode + ": " + e, e);
						else
							throw new HessianConnectionException(statusCode + ": " + sb, e);
					}

					if (is != null)
						is.close();

					throw new HessianConnectionException(statusCode + ": " + sb.toString());
				}
			}
		}

		protected void parseResponseHeaders(HttpURLConnection conn) throws IOException {
		}

		public int getStatusCode() {
			return statusCode;
		}

		public String getStatusMessage() {
			return statusMessage;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return conn.getInputStream();
		}

		@Override
		public String getContentEncoding() {
			return conn.getContentEncoding();
		}

		@Override
		public void close() {
		}

		@Override
		public void destroy() {
			close();
			URLConnection conn = this.conn;
			conn = null;

			if (conn instanceof HttpURLConnection) {
				((HttpURLConnection) conn).disconnect();
			}
		}
	}

	public static class TrustAnyStrategy implements TrustStrategy {

		static final TrustAnyStrategy INSTANCE = new TrustAnyStrategy();

		@Override
		public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			return true;
		}

	}

	static class IgnoreHostnameVerifier implements HostnameVerifier {
		static final IgnoreHostnameVerifier INSTANCE = new IgnoreHostnameVerifier();

		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}

	}

	static class HttpClientConnectionFactory extends AbstractHessianConnectionFactory {

		private HttpClient httpClient;

		private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientConnectionFactory.class);

		public HttpClientConnectionFactory() {
			this.httpClient = buildDefaultHttpClient();
		}

		public HttpClientConnectionFactory(HttpClient httpClient) {
			this.httpClient = httpClient;
		}

		@Override
		public HessianConnection open(java.net.URL url) throws IOException {
			HttpClientConnection conn = new HttpClientConnection(httpClient, url);
			conn.setConnectionTimeout((int) this.getHessianProxyFactory().getConnectTimeout());
			conn.setReadTimeout(this.getReadTimeout(url));
			conn.addHeader(TENANT_ID_KEY, String.valueOf(1));

			return conn;
		}

		private int getReadTimeout(java.net.URL url) {
			String value = URLUtils.getQueryParameter(url, Constants.TIMEOUT_KEY);
			if (StringUtils.isNotEmpty(value)) {
				try {
					return Integer.parseInt(value);
				} catch (NumberFormatException ex) {
					LOGGER.warn("invalid timeout value, {} is not an integer", value);
					return (int) this.getHessianProxyFactory().getConnectTimeout();
				}
			} else {
				return (int) this.getHessianProxyFactory().getConnectTimeout();
			}
		}

		protected CloseableHttpClient buildDefaultHttpClient() {
			try {
				SSLContext context = SSLContexts.custom().loadTrustMaterial(null, TrustAnyStrategy.INSTANCE).build();
				SSLConnectionSocketFactory f = new SSLConnectionSocketFactory(context, IgnoreHostnameVerifier.INSTANCE);
				return HttpClients.custom().setSSLSocketFactory(f).build();
			} catch (Exception ex) {
				throw new RpcException("faield to create default http client", ex);
			}
		}

	}

	static class HttpClientConnection extends AbstractHessianConnection {

		private final HttpClient httpClient;

		private final ByteArrayOutputStream output;

		private final HttpPost request;

		private volatile HttpResponse response;

		public HttpClientConnection(HttpClient httpClient, java.net.URL url) {
			this.httpClient = httpClient;
			this.output = new ByteArrayOutputStream();
			this.request = new HttpPost(url.toString());
		}

		public void setReadTimeout(int readTimeout) {
			if (readTimeout > 0) {
				RequestConfig config = this.request.getConfig();
				if (config != null) {
					config = RequestConfig.copy(config).setSocketTimeout(readTimeout).build();
				} else {
					config = RequestConfig.custom().setSocketTimeout(readTimeout).build();
				}
				this.request.setConfig(config);
			}
		}

		public void setConnectionTimeout(int connectionTimeout) {
			if (connectionTimeout > 0) {
				RequestConfig config = this.request.getConfig();
				if (config != null) {
					config = RequestConfig.copy(config).setConnectTimeout(connectionTimeout).build();
				} else {
					config = RequestConfig.custom().setConnectTimeout(connectionTimeout).build();
				}
				this.request.setConfig(config);
			}
		}

		public void addHeader(String key, String value) {
			request.addHeader(new BasicHeader(key, value));
		}

		public OutputStream getOutputStream() throws IOException {
			return output;
		}

		public void sendRequest() throws IOException {
			request.setEntity(new ByteArrayEntity(output.toByteArray()));
			this.response = httpClient.execute(request);
		}

		public int getStatusCode() {
			return response == null || response.getStatusLine() == null ? 0 : response.getStatusLine().getStatusCode();
		}

		public String getStatusMessage() {
			return response == null || response.getStatusLine() == null ? null
					: response.getStatusLine().getReasonPhrase();
		}

		public InputStream getInputStream() throws IOException {
			return response == null || response.getEntity() == null ? null : response.getEntity().getContent();
		}

		public void close() throws IOException {
			request.releaseConnection();
		}

		public void destroy() throws IOException {
			close();
		}

	}
}