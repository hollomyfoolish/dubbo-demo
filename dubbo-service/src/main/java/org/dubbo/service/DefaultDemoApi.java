package org.dubbo.service;

import java.util.Arrays;
import java.util.List;

import org.dubbo.api.Demo;
import org.dubbo.api.DemoApi;

import com.alibaba.dubbo.rpc.RpcContext;

public class DefaultDemoApi implements DemoApi {

	public List<Demo> getDemos() {
		System.out.println("rpcContext out 111" + RpcContext.getContext().getAttachment("test"));
        return Arrays.asList(
                new Demo(1, "name")
                , new Demo(2, "name2")
                , new Demo(3, "name3")
        );
	}

	public void insert(Demo demo) {
		System.out.println(String.format("save dome s%",demo));
	}

}
