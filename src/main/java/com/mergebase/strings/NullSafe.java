package com.mergebase.strings;

import java.util.Comparator;
import java.util.Objects;

/**
 * @author julius.davies@phonelist.ca
 * @since 3-Nov-2008
 */
public class NullSafe {

    public static <T extends Comparable<T>> int compare(final T a, final T b) {
        return compareNullIsBigger(a, b);
    }

    private static <T extends Comparable<T>> int compareNullIsBigger(final T a, final T b) {
        return a == b ? 0 : a == null ? 1 : b == null ? -1 : a.compareTo(b);
    }

    public static <T> int compare(final T a, final T b, final Comparator<T> c) {
        return compareNullIsBigger(a, b, c);
    }

    private static <T> int compareNullIsBigger(final T a, final T b, final Comparator<T> c) {
        return a == b ? 0 : a == null ? 1 : b == null ? -1 : c.compare(a, b);
    }

    public static int compareIgnoreCase(final String a, final String b) {
        return Objects.equals(a, b) ? 0 : a == null ? 1 : b == null ? -1 : a.compareToIgnoreCase(b);
    }

    public static boolean equals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        } else if (o1 == null || o2 == null) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }

    public static String max(String[] array, Comparator<String> c) {
        String max = null;
        if (array != null) {
            for (String s : array) {
                if (max == null) {
                    max = s;
                } else if (s != null) {
                    if (compare(s, max, c) > 0) {
                        max = s;
                    }
                }
            }
        }
        return max;
    }

    public static String toTrimmedString(Object o) {
        if (o == null) {
            return "";
        } else if (o instanceof String) {
            return ((String) o).trim();
        } else {
            return o.toString().trim();
        }
    }

}
