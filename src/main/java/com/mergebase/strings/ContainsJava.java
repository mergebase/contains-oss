package com.mergebase.strings;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ContainsJava {

    private final static Comparator<File> FILES_ORDER_BY_NAME = new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            String s1 = f1 != null ? f1.getName() : "";
            String s2 = f2 != null ? f2.getName() : "";
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

    private final static Comparator<String> CASE_SENSITIVE_SANE = new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
            int c = s1.compareToIgnoreCase(s2);
            if (c == 0) {
                c = s1.compareTo(s2);
            }
            return c;
        }
    };

    private static boolean verbose = false;

    private static boolean containsJavaRecursive(
            final String zipPath, final Zipper zipper
    ) throws IOException {

        if (verbose) {
            System.err.println("Scan... \t " + zipPath);
        }

        ZipEntry ze;
        ZipInputStream zin;

        // 1st pass... look for archives inside the archive
        zin = zipper.getFreshZipStream();
        if (zin == null) {
            System.err.println(zipPath + " NULL !");
            return false;
        }

        boolean isZip = false;
        while ((ze = zin.getNextEntry()) != null) {
            isZip = true;

            if (ze.isDirectory()) {
                continue;
            }
            final String path = ze.getName();
            final String fullPath = zipPath + "!/" + path;
            final byte[] bytes = Bytes.streamToBytes(zin, false);

            String PATH = path.toUpperCase(Locale.ENGLISH);
            if (PATH.endsWith(".ZIP") || PATH.endsWith(".WAR") || PATH.endsWith(".EAR") || PATH.endsWith(".JAR")) {
                try {
                    Zipper recursiveZipper = new Zipper() {
                        public JarInputStream getFreshZipStream() {
                            ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
                            try {
                                return new JarInputStream(bin);
                            } catch (IOException ioe) {
                                throw new RuntimeException("JarInputStream failed - " + ioe, ioe);
                            }
                        }

                        public void close() {
                        }

                        public long crc64() {
                            return CRC64.hash(bytes);
                        }
                    };

                    if (containsJavaRecursive(fullPath, recursiveZipper)) {
                        return true;
                    }
                } catch (Exception e) {
                    System.out.println(fullPath + " FAILED: " + e);
                    e.printStackTrace(System.out);
                }


            } else {
                if (containsJava(fullPath)) {
                    return true;
                }
            }
        }

        if (!isZip) {
            if (containsJava(zipPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsJava(String zipPath) {
        String PATH = zipPath.toUpperCase(Locale.ENGLISH);
        return PATH.endsWith(".CLASS") || PATH.endsWith(".JAVA");
    }

    public static boolean scan(
            final File zipFile
    ) {

        Zipper myZipper = new Zipper() {
            private FileInputStream fin;
            private BufferedInputStream bin;
            private JarInputStream zin;
            long crc64 = -1;

            public JarInputStream getFreshZipStream() {
                Util.close(zin, bin, fin);
                try {
                    fin = new FileInputStream(zipFile);
                    crc64 = CRC64.hash(fin);
                    fin.close();

                    fin = new FileInputStream(zipFile);
                    bin = new BufferedInputStream(fin);
                    zin = new JarInputStream(bin);
                    return zin;
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }

            public void close() {
                Util.close(zin, bin, fin);
            }

            public long crc64() {
                return crc64;
            }
        };

        try {
            String zip = zipFile.getPath();
            if (containsJavaRecursive(zip, myZipper)) {
                return true;
            }
        } catch (Exception e) {
            System.out.println(zipFile.getPath() + " FAILED: " + e);
            e.printStackTrace(System.out);
        } finally {
            myZipper.close();
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        Iterator<String> it = argsList.iterator();
        boolean sawFile = false;
        while (it.hasNext()) {
            final String argOrig = it.next();
            String arg = argOrig.toLowerCase(Locale.ENGLISH);
            if (!sawFile) {
                if (arg.startsWith("--verbose")) {
                    verbose = true;
                    it.remove();
                    continue;
                }
            }

            int x = argOrig.indexOf('!');
            arg = argOrig;
            if (x >= 0) {
                arg = argOrig.substring(0, x);
            }
            File f = new File(arg);
            if (!f.exists()) {
                System.out.println("Invalid file: [" + f.getPath() + "]");
                System.exit(102);
            } else {
                sawFile = true;
            }
        }

        if (argsList.isEmpty()) {
            System.out.println();
            System.out.println("Usage: java -jar contains-java.jar [arguments...] [paths to scan...]");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println();
            System.out.println("  --verbose             Show every file scanned (on stderr)");
            System.out.println();
            System.out.println("About - Mergebase contains-java scanner (version 1.0.1)");
            System.out.println("Docs  - https://mergebase.com/contains-java/");
            System.out.println("(C) Copyright 2020 Mergebase Software Inc. All Rights Reserved.");
            System.out.println();
            System.exit(100);
        }
        for (String arg : argsList) {
            File dir = new File(arg);
            analyze(dir);
        }
        if (recordCount > 0) {
            System.out.println("\n]");
        }
        if (javaCount <= 0) {
            System.exit(1);
        }
    }


    private static int recordCount = 0;
    private static int javaCount = 0;

    private static void analyze(File f) {
        boolean isSymlink = Files.isSymbolicLink(f.toPath());
        boolean cannotRead = !f.canRead();
        if (isSymlink || cannotRead) {
            return;
        }

        if (f.isDirectory()) {
            File[] fileList = f.listFiles();
            Arrays.sort(fileList, FILES_ORDER_BY_NAME);
            for (File ff : fileList) {
                analyze(ff);
            }
        } else {
            boolean containsJava = scan(f);
            if (containsJava) {
                javaCount++;
            }
            Map<String, Boolean> m = new HashMap<>();
            m.put(f.getPath(), containsJava);

            if (recordCount == 0) {
                System.out.println("[\n");
            } else if (recordCount > 0) {
                System.out.println(",");
            }
            System.out.print("  " + Java2Json.format(m));
            recordCount++;
        }
    }

}
