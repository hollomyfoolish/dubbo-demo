package org.dubbo.service.init;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration.Dynamic;

import org.dubbo.extension.RpcDispatcherServlet;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;

public class MicoServiceApplicationInitializer implements WebApplicationInitializer{

	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		System.out.println("==============Spring started======================");
		servletContext.addListener(ContextLoaderListener.class);
		
		Dynamic servlet = servletContext.addServlet("rpcDispatcher", RpcDispatcherServlet.class);
		servlet.addMapping("/rpc/*");
		servlet.setLoadOnStartup(1);
		servlet.setAsyncSupported(true);
	}

}
