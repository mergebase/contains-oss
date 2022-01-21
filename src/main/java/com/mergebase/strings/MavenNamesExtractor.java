/*
 * This file is licensed to the public under the terms of the GNU Public License 3.0
 * (aka GPLv3).
 *
 * To be clear, for the purposes of copyright law, any program ["The Importing Program"] that
 * imports this file (via Java's "import" mechanism or via Java reflection or via any
 * other software technique for importing or referencing functionality) is considered
 * a derivative work of this work, and must also comply with the conditions of the GPLv3
 * license in The Importing Program's totality to be granted a copyright license to this work,
 * and must also use the same definition as defined here for what constitutes a derivative work
 * of itself.
 *
 */
package com.mergebase.strings;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MavenNamesExtractor {

    private static boolean verbose = false;
    private static boolean debug = false;
    private static File currentDir = null;
    private static String currentPath = null;
    private static boolean printFullPaths = false;

    public static void main(String[] args) throws IOException {
        currentDir = canonicalize(new File("."));
        currentPath = currentDir.getPath();

        List<String> argsList = new ArrayList<String>();
        Collections.addAll(argsList, args);

        Iterator<String> it = argsList.iterator();
        List<String> stdinLines = new ArrayList<String>();
        while (it.hasNext()) {
            final String argOrig = it.next().trim();
            if ("--debug".equals(argOrig)) {
                debug = true;
                it.remove();
            } else if ("--verbose".equals(argOrig)) {
                verbose = true;
                it.remove();
            } else {
                File f;
                if (argOrig.length() == 2 && ':' == argOrig.charAt(1) && Character.isLetter(argOrig.charAt(0))) {
                    f = new File(argOrig + File.separator);
                } else {
                    f = new File(argOrig);
                }
                if (!f.exists()) {
                    System.err.println("Invalid file: [" + f.getPath() + "]");
                    System.exit(102);
                }
            }
        }

        if (argsList.isEmpty()) {
            System.out.println();
            System.out.println("MavenNamesExtractor - invalid input");
            System.out.println();
            System.exit(100);
        }

        for (String arg : argsList) {
            File dir;
            if (arg.length() == 2 && ':' == arg.charAt(1) && Character.isLetter(arg.charAt(0))) {
                dir = new File(arg + File.separator);
            } else {
                dir = new File(arg);
            }
            analyze(dir);
        }
    }

    private static int[] pop4(InputStream in) throws IOException {
        int[] four = new int[4];
        four[0] = in.read();
        four[1] = in.read();
        four[2] = in.read();
        four[3] = in.read();
        return four;
    }

    private static int nextByte(int[] four, InputStream in) throws IOException {
        four[0] = four[1];
        four[1] = four[2];
        four[2] = four[3];
        four[3] = in.read();
        File f = new File("blah");
        return four[3];
    }

    private static boolean isZipSentinel(int[] chunk) {
        return chunk[0] == 0x50 && chunk[1] == 0x4B && chunk[2] == 3 && chunk[3] == 4;
    }

    private static final Comparator<File> FILES_ORDER_BY_NAME = new Comparator<File>() {
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

    /**
     * @param fileName name to examine for type
     * @return 0 == zip, 1 == class, 2 = log4j-core/pom.properties, -1 = who knows...
     */
    private static int fileType(String fileName) {
        int c = fileName.lastIndexOf('.');
        if (c >= 0) {
            String suffix = fileName.substring(c + 1);

            // Special logic for "log4j-core/pom.properties" last-resort version source.
            if ("properties".equalsIgnoreCase(suffix)) {
                String lower = fileName.toLowerCase(Locale.ROOT);
            } else if ("class".equalsIgnoreCase(suffix)) {
                return 1;
            } else if ("zip".equalsIgnoreCase(suffix)
                    || "jpi".equalsIgnoreCase(suffix)
                    || "hpi".equalsIgnoreCase(suffix)
                    || suffix.endsWith("ar")) {
                return 0;
            }
        }
        return -1;
    }

    private static void findClassNamesRecursive(
            final String zipPath, final Zipper zipper
    ) {
        ZipInputStream zin;
        try {
            try {
                zin = zipper.getFreshZipStream();
            } catch (Exception e) {
                System.err.println("-- Problem: " + zipPath + " - " + e);
                if (verbose) {
                    e.printStackTrace(System.err);
                }
                return;
            }
            if (zin == null) {
                if (fileType(zipPath) == 0) {
                    System.err.println("-- Problem: " + zipPath + " - Not actually a zip!?! (no magic number)");
                } else {
                    if (verbose) {
                        System.err.println("-- Ignoring: " + zipPath + " - (not a zip)");
                    }
                }
                return;
            } else {
                if (verbose) {
                    System.err.println("-- Examining " + zipPath + "... ");
                }
            }

            ZipEntry ze;
            while (true) {
                try {
                    ze = zin.getNextEntry();
                } catch (Exception oops) {
                    System.err.println("-- Problem " + zipPath + " - " + oops);
                    if (verbose) {
                        oops.printStackTrace(System.err);
                    }
                    return;
                }
                if (ze == null) {
                    break;
                }
                if (ze.isDirectory()) {
                    continue;
                }

                long zipEntrySize = ze.getSize();
                final String path = ze.getName().trim();
                final String fullPath = zipPath + "!/" + path;

                int fileType = fileType(path);
                boolean isSubZip = fileType == 0;
                boolean isClassEntry = fileType == 1;
                if (debug) {
                    System.err.println("-- DEBUG - " + fullPath + " size=" + zipEntrySize + " isZip=" + isSubZip + " isClass=" + isClassEntry);
                }
                byte[] b = new byte[0];
                if (isSubZip) {
                    try {
                        b = Bytes.streamToBytes(zin, false, zipEntrySize + 1);
                    } catch (Exception e) {
                        System.err.println("-- Problem - could not extract " + fullPath + " (size=" + zipEntrySize + ") - " + e);
                        if (verbose) {
                            e.printStackTrace(System.err);
                        }
                        continue;
                    }
                } else if (isClassEntry) {
                    String javaName = extractJavaName(path);
                    if (javaName != null) {
                        System.out.println(javaName);
                    } else {
                        // System.out.println("NOT JAVA: [" + path + "] FROM=" + fullPath);
                    }
                }
                final byte[] bytes = b;

                if (isSubZip) {
                    try {
                        Zipper recursiveZipper = new Zipper() {
                            private Long crc64 = null;
                            private ByteArrayInputStream bin = new ByteArrayInputStream(bytes);

                            public ZipInputStream getFreshZipStream() {
                                int pos = getZipStart(bin);
                                if (pos < 0) {
                                    throw new RuntimeException("Inner-zip - could not find ZIP magic number: " + fullPath);
                                }
                                bin = new ByteArrayInputStream(bytes);

                                // Advance to beginning of zip...
                                for (int i = 0; i < pos; i++) {
                                    int c = bin.read();
                                    if (c < 0) {
                                        throw new RuntimeException("Inner-zip closed early i=" + i + " - should be impossible");
                                    }
                                }
                                return new ZipInputStream(bin);
                            }

                            @Override
                            public long crc64() {
                                if (crc64 == null) {
                                    crc64 = CRC64.hash(bytes);
                                }
                                return crc64;
                            }

                            public void close() {
                            }
                        };

                        findClassNamesRecursive(fullPath, recursiveZipper);
                    } catch (Exception e) {
                        System.err.println(fullPath + " FAILED: " + e);
                        e.printStackTrace(System.err);
                    }
                }
            }
        } finally {
            if (zipper != null) {
                zipper.close();
            }
        }
    }

    private static String extractJavaName(final String path) {
        String pathLower = path.toLowerCase(Locale.ROOT);
        String pathAdjusted = path;
        if (pathLower.startsWith("boot-inf/classes/")) {
            pathAdjusted = pathAdjusted.substring("boot-inf/classes/".length());
        } else if (pathLower.startsWith("web-inf/classes/")) {
            pathAdjusted = pathAdjusted.substring("web-inf/classes/".length());
        }
        if (pathLower.endsWith(".class")) {
            pathAdjusted = pathAdjusted.substring(0, pathAdjusted.length() - ".class".length());
        } else {
            return null;
        }
        for (int i = 0; i < pathAdjusted.length(); i++) {
            char c = pathAdjusted.charAt(i);
            if (c != '_' && c != '$' && c != '/' && c != '\\' && !Character.isLetterOrDigit(c)) {
                return null;
            }
        }
        pathAdjusted = pathAdjusted.replace('/', '.');
        pathAdjusted = pathAdjusted.replace('\\', '.');
        return pathAdjusted;
    }

    private static void scan(
            final File zipFile
    ) {
        Zipper myZipper = new Zipper() {
            private Long crc64 = null;
            private FileInputStream fin;
            private BufferedInputStream bin;
            private ZipInputStream zin;

            public ZipInputStream getFreshZipStream() {
                Util.close(zin, bin, fin);
                try {
                    fin = new FileInputStream(zipFile);
                    bin = new BufferedInputStream(fin);
                    if (startsWithZipMagic(bin)) {
                        zin = new ZipInputStream(bin);
                        return zin;
                    } else {
                        int pos = getZipStart(bin);
                        if (pos < 0) {
                            bin.close();
                            fin.close();
                            return null;
                        }
                        bin.close();
                        fin.close();

                        fin = new FileInputStream(zipFile);
                        bin = new BufferedInputStream(fin);
                        // Advance to beginning of zip...
                        for (int i = 0; i < pos; i++) {
                            int c = bin.read();
                            if (c < 0) {
                                throw new RuntimeException("Zip closed early i=" + i + " - should be impossible");
                            }
                        }
                        zin = new ZipInputStream(bin);
                        return zin;
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }

            public void close() {
                Util.close(zin, bin, fin);
            }

            public long crc64() {
                if (crc64 == null) {
                    crc64 = CRC64.hash(this.getFreshZipStream());
                }
                return crc64;
            }
        };

        try {
            String zip = zipFile.getPath();
            findClassNamesRecursive(zip, myZipper);
        } catch (Exception e) {
            System.err.println("-- Problem: " + zipFile.getPath() + " FAILED: " + e);
            e.printStackTrace(System.err);
        } finally {
            myZipper.close();
        }
    }

    private static boolean startsWithZipMagic(BufferedInputStream in) {
        in.mark(4);
        try {
            int[] fourBytes = pop4(in);
            return isZipSentinel(fourBytes);
        } catch (IOException ioe) {
            return false;
        } finally {
            try {
                in.reset();
            } catch (IOException ioe) {
                throw new RuntimeException("BufferedInputStream.reset() failed: " + ioe);
            }
        }
    }

    private static int getZipStart(InputStream in) {
        int pos;
        try {
            int[] fourBytes = pop4(in);
            pos = 0;
            if (!isZipSentinel(fourBytes)) {
                int read = nextByte(fourBytes, in);
                pos++;
                while (read >= 0) {
                    if (isZipSentinel(fourBytes)) {
                        break;
                    }
                    read = nextByte(fourBytes, in);
                    pos++;
                }
                if (read < 0) {
                    pos = -1;
                }
            }
        } catch (IOException ioe) {
            pos = -1;
        }
        return pos;
    }

    private static final HashSet<Long> visited = new HashSet<Long>();

    private static File canonicalize(File f) {
        try {
            f = f.getCanonicalFile();
        } catch (Exception e) {
            // oh well
            if (verbose) {
                System.err.println("f.getCanonicalFile() failed: " + f.getPath() + " - " + e);
            }
            f = f.getAbsoluteFile();
        }
        return f;
    }

    private static void analyze(File f) {
        f = canonicalize(f);

        // Hopefully this stops symlink cycles.
        // Using CRC-64 of path to save on memory (since we're storing *EVERY* path we come across).
        String path = f.getPath();
        File parent = f.getParentFile();
        while (parent != null) {
            String parentPath = parent.getPath();
            parent = parent.getParentFile();
        }
        long crc = CRC64.hash(path);
        if (visited.contains(crc)) {
            return;
        } else {
            visited.add(crc);
        }

        if (f.isDirectory()) {
            if (!f.canRead()) {
                System.err.println("-- Problem: no permission to read directory - " + f.getPath());
                return;
            }

            File[] fileList = f.listFiles();
            if (fileList != null) {
                Arrays.sort(fileList, FILES_ORDER_BY_NAME);
                for (File ff : fileList) {
                    analyze(ff);
                }
            }
        } else {
            if (f.isFile() || f.isHidden()) {
                int fileType = fileType(f.getName());
                if (0 == fileType) {
                    if (!f.canRead()) {
                        System.err.println("-- Problem: no permission to read contents of zip file - " + f.getPath());
                        return;
                    }
                    scan(f);
                }
            } else {
                if (verbose) {
                    System.err.println("-- Skipping " + f.getPath() + " - Not a regular file.");
                }
            }
        }
    }

}
