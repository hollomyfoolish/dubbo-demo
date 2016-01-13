package org.dubbo.extension;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.remoting.http.HttpBinder;
import com.alibaba.dubbo.remoting.http.HttpHandler;
import com.alibaba.dubbo.remoting.http.HttpServer;
import com.alibaba.dubbo.remoting.http.support.AbstractHttpServer;

public class ContainerHttpBinder implements HttpBinder {

	@Adaptive()
	public HttpServer bind(URL url, HttpHandler handler) {
		return new ContainerHttpServer(url, handler);
	}

	static class ContainerHttpServer extends AbstractHttpServer {

		public ContainerHttpServer(URL url, HttpHandler handler) {
			super(url, handler);
			RpcDispatcherServlet.addHttpHandler(0, handler);
		}

	}

}