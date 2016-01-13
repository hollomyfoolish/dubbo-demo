package org.dubbo.extension;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.remoting.http.HttpHandler;

public class RpcDispatcherServletHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(RpcDispatcherServletHandler.class);

	private static final Map<Integer, HttpHandler> handlers = new ConcurrentHashMap<Integer, HttpHandler>();

	private LinkedBlockingQueue<RpcHttpRequest> requests = new LinkedBlockingQueue<RpcHttpRequest>();

	private ExecutorService monitor;

	private ExecutorService worker;

	public void addHttpHandler(int port, HttpHandler processor) {
		handlers.put(port, processor);
	}

	public void removeHttpHandler(int port) {
		handlers.remove(port);
	}

	public void start() {
		monitor = Executors.newSingleThreadExecutor();
		worker = Executors.newCachedThreadPool();
		monitor.execute(new Runnable() {
			@Override
			public void run() {
				while (!monitor.isTerminated()) {
					try {
						final RpcHttpRequest request = requests.take();
						worker.execute(new Runnable() {
							public void run() {
								handle(request);
							}
						});
					} catch (Exception ex) {
						LOGGER.error("Exception thrown in monitoring thread", ex);
					}
				}
			}
		});
	}

	public void asyncHandle(HttpServletRequest req, HttpServletResponse resp) {
		AsyncContext asyncContext = req.startAsync(req, resp);
		RpcHttpRequest request = new RpcHttpRequest(asyncContext);
		try {
			LOGGER.debug("received rpc request {}" + req);
			requests.put(request);
		} catch (InterruptedException ex) {
			LOGGER.debug("received interrupted signal", ex);
			Thread.currentThread().interrupt();
		}
	}

	public void handle(HttpServletRequest request, HttpServletResponse response) {
		HttpHandler handler = handlers.get(request.getLocalPort());
		if (handler == null) {
			handler = handlers.get(0);
			if (handler == null) {
				LOGGER.debug("No proper service found for the request, make sure handler is properly configured");
				try {
					response.sendError(HttpServletResponse.SC_NOT_FOUND, "Service not found.");
				} catch (Exception ex) {
					LOGGER.warn("Fail to send error", ex);
				}
				return;
			}
		}

		try {
			LOGGER.debug("Processing RPC request {}" + request.getRequestURI());
			handler.handle(request, response);
			LOGGER.debug("Request {} processed" + request.getRequestURI());
		} catch (Exception ex) {
			LOGGER.error(String.format("Fail to process request %s", request.getRequestURI()), ex);
			try {
				String error = "Internal server error:" + ex.getMessage();
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
			} catch (IOException e) {
				LOGGER.warn("Fail to send error", e);
			}
		}
	}

	public void handle(RpcHttpRequest request) {
		AsyncContext ac = request.getAsyncContext();
		try {
			handle((HttpServletRequest) ac.getRequest(), (HttpServletResponse) ac.getResponse());
			ac.complete();
		} finally {
//			ThreadLocalTenantContextAccessor.resetTenantContext();
		}

	}

	public void stop() {
		if (worker != null)
			worker.shutdown();

		if (monitor != null)
			monitor.shutdown();
	}

	private static class RpcHttpRequest {

		private final AsyncContext asyncContext;

		public RpcHttpRequest(AsyncContext asyncContext) {
			this.asyncContext = asyncContext;
		}

		public AsyncContext getAsyncContext() {
			return asyncContext;
		}

	}

}
