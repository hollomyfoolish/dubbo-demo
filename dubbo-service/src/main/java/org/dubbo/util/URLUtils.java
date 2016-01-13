package org.dubbo.util;

import java.net.URL;

public class URLUtils {
	public static String getQueryParameter(URL url, String parameterName) {
		String query = url.getQuery();
		if (query == null) {
			return null;
		}
		String[] parts = StringUtils.split(query, "&");
		for (String keyValuePair : parts) {
			int index = keyValuePair.indexOf("=");
			if (index == 0) {
				continue;
			}
			if (index == -1 && keyValuePair.equals(parameterName)) {
				return StringUtils.EMPTY_STRING;
			} else {
				if (keyValuePair.subSequence(0, index).equals(parameterName)) {
					if (index != keyValuePair.length()) {
						return keyValuePair.substring(index + 1);
					} else {
						return StringUtils.EMPTY_STRING;
					}
				}
			}
		}
		return null;
	}

}
