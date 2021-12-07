package com.mergebase.strings;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * A non-standard CRC-64 implementation.
 * <p>
 * I say it's non-standard because I cannot get it to match
 * its output against any known CRC-64 implementation I could
 * find on the web.
 * <p>
 * Nonetheless, it resulted in zero collisions in a large
 * test data set I generated of 5,000,000 strings, so I think
 * it will work just fine for us.
 *
 * @author Julius
 */
public class CRC64 {
    private static final long P = 0x42F0E1EBA9EA3693L;
    private static final long[] T = new long[256];
    public static final long NULL_REPLACEMENT = 7L;
    private static final String NULL_STRING_REPLACEMENT = "\u0000";
    private static final byte[] NULL_BYTES_REPLACEMENT = "\u0000".getBytes(StandardCharsets.UTF_8);

    static {
        for (int b = 0; b < T.length; ++b) {
            long r = b;
            for (int i = 0; i < 8; ++i) {
                if ((r & 1) == 1) {
                    r = (r >>> 1) ^ P;
                } else {
                    r >>>= 1;
                }
            }
            T[b] = r;
        }
    }

    public static long hash(File f) {
        try {
            try (FileInputStream fin = new FileInputStream(f)) {
                return hash(fin);
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to open FileInputStream - " + ioe, ioe);
        }
    }

    public static long hash(InputStream in) {
        long crc = -1;
        if (in == null) {
            return hash((byte[]) null);
        }
        byte[] buf = new byte[4096];
        try {
            int c = in.read(buf);
            while (c >= 0) {
                if (c > 0) {
                    for (int i = 0; i < c; i++) {
                        byte b = buf[i];
                        crc = T[(b ^ (int) crc) & 0xFF] ^ (crc >>> 8);
                    }
                }
                c = in.read(buf);
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to read InputStream - " + ioe, ioe);
        }
        return Math.abs(~crc);
    }

    public static long hash(byte[] bytes) {
        long crc = -1;
        if (bytes == null) {
            bytes = NULL_BYTES_REPLACEMENT;
        }
        for (byte b : bytes) {
            crc = T[(b ^ (int) crc) & 0xFF] ^ (crc >>> 8);
        }
        return Math.abs(~crc);
    }

    public static long hash(String s) {
        byte[] bytes = s != null ? s.getBytes(StandardCharsets.UTF_8) : null;
        return hash(bytes);
    }

    public static long hash(Map.Entry entry) {
        if (entry == null) {
            return hash((byte[]) null);
        } else {
            return hash(toString(entry.getKey()), toString(entry.getValue()));
        }
    }

    private static String toString(Object o) {
        if (o instanceof String) {
            return (String) o;
        } else if (o != null) {
            return o.toString();
        } else {
            return null;
        }
    }

    public static long hash(Iterable<String> iterable) {
        long crc = -1;
        if (iterable == null) {
            return hash((byte[]) null);
        }
        for (String s : iterable) {
            if (s == null) {
                s = NULL_STRING_REPLACEMENT;
            }
            byte[] buf = s.getBytes(StandardCharsets.UTF_8);
            for (byte b : buf) {
                crc = T[(b ^ (int) crc) & 0xFF] ^ (crc >>> 8);
            }
        }
        return Math.abs(~crc);
    }

    public static long hash(Long... longs) {
        if (longs == null) {
            return hash((byte[]) null);
        } else {
            return hash(Arrays.asList(longs));
        }
    }

    public static long hash(String... strings) {
        return hash(Arrays.asList(strings));
    }

    public static long hash(Collection<Long> longs) {
        if (longs == null) {
            return hash((byte[]) null);
        }

        long crc = -1;
        for (Long l : longs) {
            if (l == null) {
                l = NULL_REPLACEMENT;
            }
            byte[] buf = longToBytes(l);
            for (byte b : buf) {
                crc = T[(b ^ (int) crc) & 0xFF] ^ (crc >>> 8);
            }
        }
        return Math.abs(~crc);
    }

    private static byte[] longToBytes(long l) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (l & 0xffL);
            l >>= 8;
        }
        return b;
    }

}
