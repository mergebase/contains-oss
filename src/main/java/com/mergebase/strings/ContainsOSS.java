package com.mergebase.strings;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ContainsOSS {
    private static Map<String, Long> HASHES = new HashMap();
    private static Set<String> DUPLICATES = new HashSet<>();
    private static Set<String> OPEN_SOURCE_NAMES = new HashSet<>();
    private static Map<String, String> IDENTITY = new HashMap<>();

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

    private static String toPkgName(String className) {
        int x = className.lastIndexOf('.');
        if (x >= 0) {
            return identity(className.substring(0, x).trim());
        } else {
            return className;
        }
    }

    private static String identity(String s) {
        String ss = IDENTITY.get(s);
        if (ss != null) {
            return ss;
        } else {
            IDENTITY.put(s, s);
            return s;
        }
    }

    private static void analyze(String path, Map<String, int[]> lineCounts, Long crc64, Map results) {
        long lineTotal = 0L;
        long behaviourTotal = 0L;
        double avg = 0.0D;

        Map<String, String> groupMapping = Grouper.toGroupings(lineCounts.keySet());
        Map<String, Long> groupLinesInternal = new TreeMap<>();
        Map<String, Long> groupLinesExternal = new TreeMap<>();
        Map<String, Long> groupBehaviours = new TreeMap<>();
        for (Map.Entry<String, int[]> entry : lineCounts.entrySet()) {
            String key = entry.getKey();
            int[] val = entry.getValue();
            lineTotal += val[0];
            behaviourTotal += val[1];

            String groupKey = groupMapping.get(key);


            // System.out.println("groupMappings.get(key) - key=" + key);

            Map<String, Long> groupLines = OPEN_SOURCE_NAMES.contains(toPkgName(key)) ? groupLinesExternal : groupLinesInternal;
            Long groupLine = groupLines.get(groupKey);
            Long groupBehaviour = groupBehaviours.get(groupKey);
            if (groupLine == null) {
                groupLine = 0L;
            }
            if (groupBehaviour == null) {
                groupBehaviour = 0L;
            }

            groupLine += val[0];
            groupBehaviour += val[1];
            groupLines.put(groupKey, groupLine);
            groupBehaviours.put(groupKey, groupBehaviour);
        }

        Map<String, Long>[] groupings = new Map[]{groupLinesInternal, groupLinesExternal};
        Set<String> zeroes = new HashSet<>();
        for (Map<String, Long> groupLines : groupings) {
            for (Map.Entry<String, Long> groupLineEntry : groupLines.entrySet()) {
                String key = groupLineEntry.getKey();
                Long count = groupLineEntry.getValue();
                if (count != null && count == 0) {
                    Long behaviourCount = groupBehaviours.get(key);
                    count = calculateBehaviourHeuristic(count, behaviourCount);
                    groupLineEntry.setValue(count);
                }
                if (count == null || count == 0) {
                    zeroes.add(key);
                }
            }
            groupLines.keySet().removeAll(zeroes);
        }

        // lineTotal = calculateBehaviourHeuristic(lineTotal, behaviourTotal);
        results.put(path, tallyVals((Map) groupLinesInternal) + tallyVals((Map) groupLinesExternal));
        results.put(path + ".INTERNAL", groupLinesInternal);
        results.put(path + ".EXTERNAL", groupLinesExternal);
        HASHES.put(path, crc64);
    }

    private static Long calculateBehaviourHeuristic(Long lineTotal, Long behaviourTotal) {
        if (lineTotal != null && behaviourTotal != null) {
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
        }
        return lineTotal;
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
        {
            boolean isGz = false;
            File f = new File("names.uniq");
            File g = new File("names.uniq.gz");
            if (g.exists() && g.canRead()) {
                f = g;
                isGz = true;
            }
            if (f.exists() && f.canRead()) {
                InputStream in = new FileInputStream(f);
                final InputStream inOrig = in;
                GZIPInputStream gzin;
                if (isGz) {
                    gzin = new GZIPInputStream(in);
                    in = gzin;
                }
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    int x = line.lastIndexOf('.');
                    if (x >= 0) {
                        String name = line.trim();
                        OPEN_SOURCE_NAMES.add(toPkgName(name));
                        // System.out.println("Added... [" + name + "]");
                    }
                }
                br.close();
                isr.close();
                in.close();
                if (isGz) {
                    inOrig.close();
                }
                String uniq = "names.uniq";
                if (isGz) {
                    uniq = "names.uniq.gz";
                }
                System.err.println(" -- Defined " + OPEN_SOURCE_NAMES.size() + " open-source package names from '" + uniq + "' file");
            }
        }

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
            System.out.println("Usage: java -jar contains-oss.jar [paths to count...]");
            System.out.println();
            System.out.println("About - MergeBase contains-oss tool (version 2022.02.23)");
            System.out.println("        Recursively counts lines of code in jar files.");
            System.out.println();
            // System.out.println("Docs  - https://github.com/mergebase/contains-oss");
            System.out.println("(C) Copyright 2022 Mergebase Software Inc. GPLv3 License. See LICENSE.TXT.");
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
        long totalInternal = 0L;
        long totalExternal = 0L;

        HashSet<Long> seenHashes = new HashSet<>();
        for (Map<String, Object> m : list) {
            for (Map.Entry<String, Object> entry : m.entrySet()) {
                String key = entry.getKey();

                // System.out.println("KEY = [" + key + "]");

                Long crc64 = HASHES.get(key);
                if (seenHashes.contains(crc64)) {
                    DUPLICATES.add(key);
                } else {
                    seenHashes.add(crc64);
                    Object o = entry.getValue();
                    if (o instanceof Long) {
                        Long val = (Long) o;
                        total += val;
                    }
                }
            }
        }

        for (Map<String, Object> m : list) {
            for (Map.Entry<String, Object> entry : m.entrySet()) {
                Map subMap = new LinkedHashMap();
                String key = entry.getKey();
                Object o = entry.getValue();
                if (o instanceof Long) {
                    Long crc64 = HASHES.get(key);
                    if (crc64 != null) {
                        subMap.put("crc64", crc64);
                    }

                    if (DUPLICATES.contains(key)) {
                        subMap.put("isDuplicate", true);
                    } else {
                        long val = (Long) entry.getValue();
                        double ratio = 100.0D * ((double) val * 1.0D / (double) total);
                        if (total > 0) {
                            subMap.put("percentage", Double.parseDouble(formatter.format(ratio)));
                        } else {
                            subMap.put("percentage", -1.0);
                        }
                        Map internalSubs = (Map) m.get(key + ".INTERNAL");
                        Map externalSubs = (Map) m.get(key + ".EXTERNAL");

                        long internalTally = tallyVals(internalSubs);
                        long externalTally = tallyVals(externalSubs);
                        totalInternal += internalTally;
                        totalExternal += externalTally;
                        subMap.put("lines", val);
                        subMap.put("lines.internal", internalTally);
                        subMap.put("lines.external", externalTally);
                        subMap.put("breakdown.internal", internalSubs);
                        subMap.put("breakdown.external", externalSubs);
                    }
                    result.put(key, subMap);
                }
            }
        }

        new ArrayList();
        if (printCSV) {
            System.out.println("percentage,lines,crc64,path");
        } else {
            System.out.println("{");
            System.out.println("\"args\":" + Java2Json.format(argsList) + ",\n");
            System.out.println("\"totalLines\":" + total + ",");
            System.out.println("\"totalInternal\":" + totalInternal + ",");
            System.out.println("\"totalExternal\":" + totalExternal + ",");
            System.out.println("\"proportionExternal\":" + ratio(totalExternal, total) + ",");
        }

        Iterator var29 = result.entrySet().iterator();

        StringBuilder jsonBuf = new StringBuilder();
        StringBuilder csvBuf = new StringBuilder();
        boolean hadSome = false;
        while (var29.hasNext()) {
            hadSome = true;
            final Entry<String, Map> entry = (Entry) var29.next();
            if (printCSV) {
                String key = entry.getKey();
                Map value = entry.getValue();
                Long crc64 = (Long) value.get("crc64");
                if (!value.containsKey("isDuplicate")) {
                    Double percent = (Double) value.get("percentage");
                    Long lines = (Long) value.get("lines");
                    csvBuf.append(percent).append(',');
                    csvBuf.append(lines).append(',');
                    csvBuf.append(crc64).append(',');
                    csvBuf.append(key).append(',');
                } else {
                    csvBuf.append(",");
                    csvBuf.append(",");
                    csvBuf.append(crc64).append(',');
                    csvBuf.append(key).append(',');
                    csvBuf.append("duplicate");
                }
                csvBuf.append("\n");
            } else {
                jsonBuf.append("  ").append(Java2Json.format(true, entry)).append(",\n");
            }
        }

        if (printCSV) {
            System.out.println(csvBuf);
        } else {
            if (hadSome) {
                jsonBuf.deleteCharAt(jsonBuf.length() - 2); // trailing comma
            }
            jsonBuf.append("\n}");
            System.out.println(jsonBuf);
        }
    }

    private static String ratio(Long top, Long bottom) {
        if (bottom > 0) {
            return Double.toString(((double) top / (double) bottom));
        } else {
            return "-1";
        }
    }

    private static long tallyVals(Map<Object, Object> m) {
        long total = 0;
        if (m != null && !m.isEmpty()) {
            for (Map.Entry<Object, Object> entry : m.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof Number) {
                    Number n = (Number) v;
                    long longVal = n.longValue();
                    if (longVal > 0) {
                        total += longVal;
                    }
                }
            }
        }
        return total;
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
