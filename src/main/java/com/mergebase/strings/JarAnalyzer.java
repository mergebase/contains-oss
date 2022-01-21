
package com.mergebase.strings;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarAnalyzer {
    static final String CLASS_EXT = ".class";
    private final ClassPool pool;
    private final Zipper zipper;

    public JarAnalyzer(Zipper zipper) {
        this.zipper = zipper;
        this.pool = new ClassPool(true);
    }

    public Enumeration<ZipEntry> entries(final ZipInputStream jarInputStream) {
        return new Enumeration<ZipEntry>() {
            ZipEntry nextEntry = null;
            boolean done = false;

            private void advance() {
                if (!this.done && this.nextEntry == null) {
                    try {
                        this.nextEntry = jarInputStream.getNextEntry();
                        if (this.nextEntry == null) {
                            this.done = true;
                        }
                    } catch (IOException var2) {
                        throw new RuntimeException("Failed to getNextJarEntry() - " + var2, var2);
                    }
                }
            }

            public boolean hasMoreElements() {
                this.advance();
                return !this.done;
            }

            public ZipEntry nextElement() {
                this.advance();
                ZipEntry ze = this.nextEntry;
                this.nextEntry = null;
                return ze;
            }
        };
    }

    public void preloadClasses() {
        ZipInputStream jarIn = this.zipper.getFreshZipStream();
        Enumeration en = this.entries(jarIn);

        while (en.hasMoreElements()) {
            JarEntry je = (JarEntry) en.nextElement();
            if (je.getName().endsWith(".class")) {
                try {
                    this.preloadClass(jarIn, je);
                } catch (Throwable var5) {
                    System.out.println("corrupt class file: [" + je.getName() + "] " + var5);
                    // var5.printStackTrace(System.out);
                }
            }
        }

    }

    private void preloadClass(ZipInputStream jarIn, ZipEntry ze) throws IOException {
        CtClass c = this.pool.makeClass(jarIn);
        c.defrost();

        boolean hadException;
        do {
            hadException = false;

            int x;
            try {
                c.getInterfaces();
                c.getSuperclass();
                c.getFields();
                c.getClassInitializer();
                CtBehavior[] var5 = c.getDeclaredBehaviors();
                int var12 = var5.length;

                for (x = 0; x < var12; ++x) {
                    CtBehavior b = var5[x];
                    b.getParameterTypes();
                    if (b instanceof CtMethod) {
                        ((CtMethod) b).getReturnType();
                    }

                    b.getExceptionTypes();
                }

                CtConstructor[] var11 = c.getConstructors();
                var12 = var11.length;

                for (x = 0; x < var12; ++x) {
                    CtConstructor ctor = var11[x];
                    ctor.getParameterTypes();
                    ctor.getExceptionTypes();
                }
            } catch (NotFoundException var10) {
                hadException = true;
                String msg = var10.getMessage();
                x = msg.lastIndexOf(58);
                String missing = msg.trim();
                if (x >= 0) {
                    missing = msg.substring(x + 1).trim();
                }

                CtClass missingClass = this.pool.makeClass(missing);
                missingClass.defrost();
            }
        } while (hadException);

    }

    public Map<String, int[]> getLineCounts() {
        Map<String, int[]> result = new TreeMap();
        this.preloadClasses();
        ZipInputStream jarIn = this.zipper.getFreshZipStream();
        Enumeration en = this.entries(jarIn);

        while (en.hasMoreElements()) {
            JarEntry je = (JarEntry) en.nextElement();
            String name = je.getName();
            if (!name.contains("$") && !je.isDirectory() && name.endsWith(".class")) {
                String className = toClassName(name);

                try {
                    CtClass cc = this.pool.get(className);
                    ClassAnalyzer classAnalyzer = new ClassAnalyzer(cc);
                    int[] lines = classAnalyzer.countLines();
                    result.put(className, lines);
                } catch (NotFoundException var10) {
                    // System.out.println("DOH - " + className + " - " + var10);
                    // var10.printStackTrace(System.out);
                }
            }
        }

        return result;
    }

    private static String toClassName(String name) {
        String classInZip = name.substring(0, name.length() - ".class".length());
        int x = classInZip.indexOf("META-INF/");
        if (x >= 0) {
            classInZip = classInZip.substring(x + "META-INF/".length());
        }

        x = classInZip.indexOf("WEB-INF/");
        if (x >= 0) {
            classInZip = classInZip.substring(x + "WEB-INF/".length());
        }

        x = classInZip.indexOf("classes/");
        if (x >= 0) {
            classInZip = classInZip.substring(x + "classes/".length());
        }

        return classInZip.replace('/', '.');
    }
}
