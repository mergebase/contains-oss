package com.mergebase.strings;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ContainsOSS {
    private static Map<String, Long> HASHES = new HashMap();
    private static final Comparator<File> FILES_ORDER_BY_PATH = new Comparator<File>() {
        public int compare(File f1, File f2) {
            String s1 = f1 != null ? f1.getPath() : "";
            String s2 = f2 != null ? f2.getPath() : "";
            int c = s1.compareToIgnoreCase(s2);
            if (c == 0) {
                c = s1.compareTo(s2);
                if (c == 0 && f1 != null) {
                    c = f1.compareTo(f2);
                }
            }

            return c;
        }
    };
    private static final Comparator<String> CASE_SENSITIVE_SANE = new Comparator<String>() {
        public int compare(String s1, String s2) {
            int c = s1.compareToIgnoreCase(s2);
            if (c == 0) {
                c = s1.compareTo(s2);
            }

            return c;
        }
    };
    static boolean printCSV = false;

    public ContainsOSS() {
    }

    private static void scanRecursive(String zipPath, Zipper zipper, Map results) throws IOException {
        ZipInputStream zin = zipper.getFreshZipStream();
        if (zin == null) {
            System.err.println(zipPath + " NULL !");
        } else {
            boolean isZip = false;

            while (true) {
                String fullPath;
                byte[] bytes;
                String PATH;
                do {
                    ZipEntry ze;
                    do {
                        if ((ze = zin.getNextEntry()) == null) {
                            return;
                        }

                        isZip = true;
                    } while (ze.isDirectory());

                    String path = ze.getName();
                    fullPath = zipPath + "!/" + path;
                    bytes = Bytes.streamToBytes(zin, false);
                    PATH = path.toUpperCase(Locale.ENGLISH);
                } while (!PATH.endsWith(".ZIP") && !PATH.endsWith(".WAR") && !PATH.endsWith(".EAR") && !PATH.endsWith(".JAR"));

                try {
                    final byte[] bytesFinal = bytes;
                    Zipper recursiveZipper = new Zipper() {
                        public JarInputStream getFreshZipStream() {
                            ByteArrayInputStream bin = new ByteArrayInputStream(bytesFinal);

                            try {
                                return new JarInputStream(bin);
                            } catch (IOException var3) {
                                throw new RuntimeException("Impossible situation - " + var3, var3);
                            }
                        }

                        public void close() {
                        }

                        public long crc64() {
                            return CRC64.hash(bytesFinal);
                        }
                    };
                    scanRecursive(fullPath, recursiveZipper, results);
                    JarAnalyzer analyzer = new JarAnalyzer(recursiveZipper);
                    analyze(fullPath, analyzer.getLineCounts(), recursiveZipper.crc64(), results);
                } catch (Exception var12) {
                    System.out.println(fullPath + " FAILED: " + var12);
                    var12.printStackTrace(System.out);
                }
            }
        }
    }

    private static void analyze(String path, Map<String, int[]> lineCounts, Long crc64, Map results) {
        long lineTotal = 0L;
        long behaviourTotal = 0L;
        double avg = 0.0D;

        int[] val;
        for (Iterator var10 = lineCounts.values().iterator(); var10.hasNext(); behaviourTotal += (long) val[1]) {
            val = (int[]) var10.next();
            lineTotal += (long) val[0];
        }

        if (lineTotal == 0L) {
            lineTotal = behaviourTotal * 17L;
            long mod;
            if (lineTotal > 1000L) {
                mod = lineTotal % 1000L;
                lineTotal -= mod;
            } else if (lineTotal > 100L) {
                mod = lineTotal % 100L;
                lineTotal -= mod;
            }
        }

        results.put(path, lineTotal);
        HASHES.put(path, crc64);
    }

    private static boolean containsJava(String zipPath) {
        String PATH = zipPath.toUpperCase(Locale.ENGLISH);
        return PATH.endsWith(".CLASS");
    }

    public static Map scan(final File zipFile) {
        Zipper myZipper = new Zipper() {
            private FileInputStream fin;
            private BufferedInputStream bin;
            private JarInputStream zin;

            public JarInputStream getFreshZipStream() {
                Util.close(this.zin, this.bin, this.fin);

                try {
                    this.fin = new FileInputStream(zipFile);
                    this.fin.close();
                    this.fin = new FileInputStream(zipFile);
                    this.bin = new BufferedInputStream(this.fin);
                    this.zin = new JarInputStream(this.bin);
                    return this.zin;
                } catch (IOException var2) {
                    throw new RuntimeException(var2);
                }
            }

            public void close() {
                Util.close(this.zin, this.bin, this.fin);
            }

            public long crc64() {
                this.getFreshZipStream();

                try {
                    byte[] b = Bytes.streamToBytes(this.fin);
                    return CRC64.hash(b);
                } catch (IOException var2) {
                    throw new RuntimeException("Failed read of FileInputStream after getFreshZipStream() - " + var2, var2);
                }
            }
        };
        TreeMap results = new TreeMap();

        try {
            String zip = zipFile.getPath();
            scanRecursive(zip, myZipper, results);
            JarAnalyzer analyzer = new JarAnalyzer(myZipper);
            analyze(zip + "!/**/*.class", analyzer.getLineCounts(), myZipper.crc64(), results);
        } catch (Exception var8) {
            System.out.println(zipFile.getPath() + " FAILED: " + var8);
            var8.printStackTrace(System.out);
        } finally {
            myZipper.close();
        }

        return results;
    }

    public static void main(String[] args) throws Exception {
        List<String> argsList = new ArrayList(Arrays.asList(args));
        Iterator it = argsList.iterator();

        while (it.hasNext()) {
            String arg = (String) it.next();
            if ("--csv".equalsIgnoreCase(arg)) {
                printCSV = true;
            } else {
                File f = new File(arg);
                if (!f.exists()) {
                    System.out.println("Invalid file: [" + f.getPath() + "]");
                    System.exit(102);
                }
            }
        }

        if (argsList.isEmpty()) {
            System.out.println();
            System.out.println("Usage: java -jar count-java.jar [--csv] [paths to count...]");
            System.out.println();
            System.out.println("About - Mergebase count-java tool (version 1.0.2)");
            System.out.println("        Recursively counts lines of code in jar files.");
            System.out.println();
            System.out.println("Docs  - https://mergebase.com/count-java/");
            System.out.println("(C) Copyright 2021 Mergebase Software Inc. All Rights Reserved.");
            System.out.println();
            System.exit(100);
        }

        NumberFormat formatter = new DecimalFormat("#0.00");
        Set<File> regularFilesToAnalyze = new TreeSet(FILES_ORDER_BY_PATH);
        Set<File> zipFilesToAnalyze = new TreeSet(FILES_ORDER_BY_PATH);
        Iterator var5 = argsList.iterator();

        File f;
        while (var5.hasNext()) {
            String arg = (String) var5.next();
            f = new File(arg);
            gatherFilesToAnalyze(f, regularFilesToAnalyze, zipFilesToAnalyze);
        }

        List<Map> list = new ArrayList();
        if (!regularFilesToAnalyze.isEmpty()) {
            FilesAnalyzer fa = new FilesAnalyzer(regularFilesToAnalyze);
            Map results = new TreeMap();
            analyze("**/*.class", fa.getLineCounts(), (Long) null, results);
            list.add(results);
        }

        Iterator var25 = zipFilesToAnalyze.iterator();

        while (var25.hasNext()) {
            f = (File) var25.next();
            Map m = scan(f);
            list.add(m);
        }

        TreeMap<String, Map> result = new TreeMap();
        long total = 0L;
        Iterator var9 = list.iterator();

        Map m;
        Iterator var11;
        while (var9.hasNext()) {
            m = (Map) var9.next();

            Long val;
            for (var11 = m.values().iterator(); var11.hasNext(); total += val) {
                val = (Long) var11.next();
            }
        }

        var9 = list.iterator();

        while (var9.hasNext()) {
            m = (Map) var9.next();

            String key;
            LinkedHashMap subMap;
            for (var11 = m.entrySet().iterator(); var11.hasNext(); result.put(key, subMap)) {
                Entry<String, Long> entry = (Entry) var11.next();
                key = (String) entry.getKey();
                long val = (Long) entry.getValue();
                double ratio = 100.0D * ((double) val * 1.0D / (double) total);
                subMap = new LinkedHashMap();
                subMap.put("percentage", Double.parseDouble(formatter.format(ratio)));
                subMap.put("lines", val);
                Long crc64 = (Long) HASHES.get(key);
                if (crc64 != null) {
                    subMap.put("crc64", crc64);
                }
            }
        }

        new ArrayList();
        if (printCSV) {
            System.out.println("percentage,lines,crc64,path");
        } else {
            System.out.println("{");
        }

        Iterator var29 = result.entrySet().iterator();

        while (var29.hasNext()) {
            Entry<String, Map> entry = (Entry) var29.next();
            String key = (String) entry.getKey();
            Map value = (Map) entry.getValue();
            Double percent = (Double) value.get("percentage");
            Long lines = (Long) value.get("lines");
            Long crc64 = (Long) value.get("crc64");
            String[] row = new String[]{key, percent.toString(), lines.toString(), crc64.toString()};
            StringBuilder buf = new StringBuilder();
            buf.append(row[1]).append(',');
            buf.append(row[2]).append(',');
            buf.append(row[3]).append(',');
            buf.append(row[0]);
            if (printCSV) {
                System.out.println(buf);
            } else {
                System.out.println("  " + Java2Json.format(entry));
            }
        }

        if (!printCSV) {
            System.out.println("}");
        }

    }

    private static void gatherFilesToAnalyze(File f, Set<File> regularFiles, Set<File> zipFiles) {
        boolean isSymlink = Files.isSymbolicLink(f.toPath());
        boolean cannotRead = !f.canRead();
        if (!isSymlink && !cannotRead) {
            if (f.isDirectory()) {
                File[] fileList = f.listFiles();
                File[] var6 = fileList;
                int var7 = fileList.length;

                for (int var8 = 0; var8 < var7; ++var8) {
                    File ff = var6[var8];
                    gatherFilesToAnalyze(ff, regularFiles, zipFiles);
                }
            } else {
                String NAME = f.getName().toUpperCase(Locale.ENGLISH);
                if (!NAME.endsWith(".ZIP") && !NAME.endsWith(".WAR") && !NAME.endsWith(".EAR") && !NAME.endsWith(".JAR")) {
                    regularFiles.add(f);
                } else {
                    zipFiles.add(f);
                }
            }

        }
    }
}
