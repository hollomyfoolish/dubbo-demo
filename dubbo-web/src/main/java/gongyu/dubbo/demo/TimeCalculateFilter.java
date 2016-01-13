package gongyu.dubbo.demo;

import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

@Activate (group = "consumer", order = 1)
public class TimeCalculateFilter implements Filter {

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation)
			throws RpcException {
		System.out.println("TimeCalculateFilter");
		long start = System.currentTimeMillis();
		try{
			Result result = invoker.invoke(invocation);
			System.out.println("result in TimeCalculateFilter: " + result);
			return result;
		}catch(Throwable e){
			System.out.println("======================");
			e.printStackTrace();
			System.out.println("======================");
			throw e;
		}finally{
			long end = System.currentTimeMillis();
			System.out.println("[TimeCalculateFilter]" + invoker.getUrl() + ": " + (end - start)/1000);
		}
	}

}
