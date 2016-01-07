package org.dubbo.api;

import java.util.List;

public interface DemoApi {

    List<Demo> getDemos();
	
    void insert(Demo demo);
}
