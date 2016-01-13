package org.dubbo.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

public abstract class StringUtils {

	public static final String EMPTY_STRING = "";

	public static String safeToString(Object obj) {
		return obj == null ? null : obj.toString();
	}

	public static boolean isEqual(String a, String b) {
		if (a == null) {
			return b == null;
		} else {
			return a.equals(b);
		}
	}

	public static boolean isEmpty(Object obj) {
		return (obj == null || "".equals(obj));
	}

	public static boolean isNotEmpty(Object obj) {
		return !isEmpty(obj);
	}

	public static String join(Object[] elements, char delim) {
		if (elements == null || elements.length == 0) {
			return "";
		}
		if (elements.length == 1) {
			return elements[0].toString();
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < elements.length; i++) {
			if (i > 0) {
				sb.append(delim);
			}
			sb.append(elements[i]);
		}
		return sb.toString();
	}

	public static String join(Collection<String> coll, String delim) {
		if (coll.isEmpty())
			return "";

		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String s : coll) {
			if (isFirst)
				isFirst = false;
			else
				sb.append(delim);
			sb.append(s);
		}
		return sb.toString();
	}

	public static String concatMapAndJoin(Collection<String> coll, String prefix, String suffix, String delim) {
		if (coll.isEmpty())
			return "";

		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String s : coll) {
			if (isFirst)
				isFirst = false;
			else
				sb.append(delim);
			sb.append(prefix + s + suffix);
		}
		return sb.toString();
	}

	public static boolean hasLength(CharSequence str) {
		return (str != null && str.length() > 0);
	}

	public static String[] split(String str, String delimiter, boolean trim) {
		if (str == null) {
			return new String[0];
		}

		StringTokenizer tokenizer = new StringTokenizer(str, delimiter);
		List<String> parts = new ArrayList<String>();
		while (tokenizer.hasMoreTokens()) {
			if (trim) {
				parts.add(tokenizer.nextToken().trim());
			} else {
				parts.add(tokenizer.nextToken());
			}
		}
		return parts.toArray(new String[0]);
	}

	public static String[] split(String str, String delimiter) {
		return split(str, delimiter, false);
	}

}
