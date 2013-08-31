package pyutils;

import java.util.ArrayList;
import java.util.List;

public class Strings {

	public static String join(String sep, String[] stuff) {
		StringBuilder sb = new StringBuilder();
		int len = stuff.length;
		int i = 0;
		for (String s : stuff) {
			sb.append(s);
			if ((++i) < len)
				sb.append(sep);
		}
		return sb.toString();
	}

	public static String join(String sep, List<String> stuff) {
		String[] t = {};
		return join(sep, stuff.toArray(t));
	}
}
