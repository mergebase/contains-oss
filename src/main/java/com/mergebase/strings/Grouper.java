package com.mergebase.strings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Grouper {

    public static void main(String[] args) throws Exception {

        List<String> list = new ArrayList<>();
        list.add(null);
        list.add("");
        list.add(".Q.Q");
        list.add("Q");
        list.add("Q.QQ");
        list.add("Q.QQQ.QQQQQ");
        list.add("a.b.c.d.E");
        list.add("a.b.c.x.X");
        list.add("a.b.y.X");
        list.add("org.apache.commons.codec.binary.A");
        list.add("org.apache.commons.codec.digest.B");
        list.add("org.apache.commons.codec.util.C");

        System.out.println(Java2Json.format(true, toGroupings(list)));
    }

    public static Map<String, String> toGroupings(Collection<String> names) {
        Map<String, String> m = new TreeMap<>();
        if (names != null) {
            for (String name : names) {
                if (name != null) {
                    String[] split = name.split("\\.+");
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < Math.min(4, split.length); i++) {
                        buf.append(split[i]).append(".");
                    }
                    if (buf.length() > 0) {
                        buf.deleteCharAt(buf.length() - 1);
                    }

                    int dotCount = Strings.countChar(name, '.');
                    switch (dotCount) {
                        case 0:
                            m.put(name, "");
                            break;
                        case 1:
                            m.put(name, split[0]);
                            break;
                        case 2:
                            m.put(name, split[0] + "." + split[1]);
                            break;
                        case 3:
                            m.put(name, split[0] + "." + split[1] + "." + split[2]);
                            break;
                        default:
                            m.put(name, buf.toString());
                            break;
                    }
                }
            }
        }
        return m;
    }


}
