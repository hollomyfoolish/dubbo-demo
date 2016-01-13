package gongyu.dubbo.demo;

import java.io.IOException;

import org.dubbo.api.DemoApi;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DemoInvokerTest {

	   public static void main(String[] args) throws IOException {
	        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:application-consumer.xml");
	        DemoApi api = (DemoApi)context.getBean("dubboDemoApi");
	        System.out.println("[Dubbo-result]: " + api.getDemos());
	    }

}
