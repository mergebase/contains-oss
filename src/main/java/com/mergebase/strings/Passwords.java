package com.mergebase.strings;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Passwords {

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
    private static List<byte[]> passwordsAsBytes = new ArrayList<>();

    private static void findPasswordsRecursive(
            final String zipPath, final Zipper zipper
    ) throws IOException {

        if (verbose) {
            System.out.println("Scan... \t " + zipPath);
        }

        ZipEntry ze;
        ZipInputStream zin;

        // 1st pass... look for archives inside the archive
        zin = zipper.getFreshZipStream();
        if (zin == null) {
            System.out.println(zipPath + " NULL !");
            return;
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

                    findPasswordsRecursive(fullPath, recursiveZipper);
                } catch (Exception e) {
                    System.out.println(fullPath + " FAILED: " + e);
                    e.printStackTrace(System.out);
                }


            } else {
                checkForPasswords(fullPath, bytes);
            }
        }

        if (!isZip) {
            File f = new File(zipPath);
            if (f.canRead() && f.length() < 5000000) {
                FileInputStream fin = null;
                try {
                    fin = new FileInputStream(f);
                    byte[] bytes = Bytes.streamToBytes(fin);
                    checkForPasswords(zipPath, bytes);
                } catch (IOException ioe) {
                    System.out.println("FAILED TO READ " + zipPath + ": " + ioe);
                } finally {
                    if (fin != null) {
                        try {
                            fin.close();
                        } catch (IOException ioe) {
                            // swallow close exception
                        }
                    }
                }
            }
        }

    }

    private static void checkForPasswords(String zipPath, byte[] bytes) {
        int hit = 1;
        for (byte[] password : passwords()) {
            int matched = Bytes.kmp(bytes, password);
            if (matched >= 0) {
                System.out.println("* HIT" + hit + "  \t " + zipPath);
            }
            hit++;
        }
    }

    public static void scan(
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
                    fin.close();

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
            findPasswordsRecursive(zip, myZipper);
        } catch (Exception e) {
            System.out.println(zipFile.getPath() + " FAILED: " + e);
            e.printStackTrace(System.out);
        } finally {
            myZipper.close();
        }
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
                if (arg.startsWith("--password=")) {
                    String a = argOrig.substring("--password=".length());
                    passwordsAsBytes.add(a.getBytes(StandardCharsets.UTF_8));
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
            System.out.println("Usage: java -jar passwords.jar [arguments...] [paths to scan...]");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println();
            System.out.println("  --verbose             Show every file scanned, including those with no hits.");
            System.out.println();
            System.out.println("  --password=[password] Password to look for. Repeat this argument to search");
            System.out.println("                        for several passwords in parallel.");
            System.out.println();
            System.out.println("About - Mergebase passwords scanner (version 1.0.1)");
            System.out.println("Docs  - https://mergebase.com/passwords-scanner/");
            System.out.println("(C) Copyright 2019 Mergebase Software Inc. All Rights Reserved.");
            System.out.println();
            System.exit(100);
        }
        for (String arg : argsList) {
            File dir = new File(arg);
            analyze(dir);
        }
    }

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
            scan(f);
        }
    }

    private static List<byte[]> passwords() {
        return passwordsAsBytes;
    }
}
