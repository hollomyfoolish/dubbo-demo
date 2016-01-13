package gongyu.dubbo.demo;

import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;

@Activate (group = "consumer", order = 0)
public class MyDubboFilter implements Filter{

	public Result invoke(Invoker<?> invoker, Invocation invocation)
			throws RpcException {
		System.out.println("MyDubboFilter");
		RpcInvocation inv = (RpcInvocation) invocation;
		inv.setAttachmentIfAbsent("test", "test value from RPC");
		Result result = invoker.invoke(invocation);
		System.out.println("result in MyDubboFilter: " + result);
		return result;
	}

}
