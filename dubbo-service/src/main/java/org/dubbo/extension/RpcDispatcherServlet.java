package org.dubbo.extension;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.dubbo.remoting.http.HttpHandler;

public class RpcDispatcherServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static RpcDispatcherServletHandler handler = new RpcDispatcherServletHandler();

	public static void addHttpHandler(int port, HttpHandler processor) {
		handler.addHttpHandler(port, processor);
	}

	public static void removeHttpHandler(int port) {
		handler.removeHttpHandler(port);
	}

	@Override
	public void init() throws ServletException {
		handler.start();
	}

	@Override
	public void destroy() {
		handler.stop();
	}

	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		handler.handle(request, response);
	}
}
