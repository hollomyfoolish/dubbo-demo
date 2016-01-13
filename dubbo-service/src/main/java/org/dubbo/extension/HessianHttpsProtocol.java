package org.dubbo.extension;

public class HessianHttpsProtocol extends HessianHttpProtocol {

	@Override
	public int getDefaultPort() {
		return 443;
	}

}