
package com.mergebase.strings;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FilesAnalyzer {
    static final String CLASS_EXT = ".class";
    private final ClassPool pool;
    private final Set<File> files;

    public FilesAnalyzer(Set<File> files) {
        this.files = files;
        this.pool = new ClassPool(true);
    }

    public void preloadClasses() {
        Iterator var1 = this.files.iterator();

        while (var1.hasNext()) {
            File f = (File) var1.next();
            String name = f.getName().toLowerCase(Locale.ENGLISH);
            if (name.endsWith(".class")) {
                try {
                    this.preloadClass(f);
                } catch (Throwable var5) {
                    System.out.println("corrupt class file: [" + f.getName() + "] " + var5);
                    // var5.printStackTrace(System.out);
                }
            }
        }

    }

    private void preloadClass(File f) throws IOException {
        FileInputStream fin = new FileInputStream(f);
        Throwable var4 = null;

        try {
            CtClass c = this.pool.makeClass(fin);
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
                    CtBehavior[] var6 = c.getDeclaredBehaviors();
                    int var23 = var6.length;

                    for (x = 0; x < var23; ++x) {
                        CtBehavior b = var6[x];
                        b.getParameterTypes();
                        if (b instanceof CtMethod) {
                            ((CtMethod) b).getReturnType();
                        }

                        b.getExceptionTypes();
                    }

                    CtConstructor[] var22 = c.getConstructors();
                    var23 = var22.length;

                    for (x = 0; x < var23; ++x) {
                        CtConstructor ctor = var22[x];
                        ctor.getParameterTypes();
                        ctor.getExceptionTypes();
                    }
                } catch (NotFoundException var19) {
                    hadException = true;
                    String msg = var19.getMessage();
                    x = msg.lastIndexOf(58);
                    String missing = msg.trim();
                    if (x >= 0) {
                        missing = msg.substring(x + 1).trim();
                    }

                    CtClass missingClass = this.pool.makeClass(missing);
                    missingClass.defrost();
                }
            } while (hadException);
        } catch (Throwable var20) {
            var4 = var20;
            throw var20;
        } finally {
            if (fin != null) {
                if (var4 != null) {
                    try {
                        fin.close();
                    } catch (Throwable var18) {
                        var4.addSuppressed(var18);
                    }
                } else {
                    fin.close();
                }
            }

        }

    }

    public Map<String, int[]> getLineCounts() {
        Map<String, int[]> result = new TreeMap();
        this.preloadClasses();
        Iterator var2 = this.files.iterator();

        while (var2.hasNext()) {
            File f = (File) var2.next();
            String name = f.getName().toLowerCase(Locale.ENGLISH);
            if (!name.contains("$") && !f.isDirectory() && name.endsWith(".class")) {
                String className = toClassName(f.getPath());

                try {
                    CtClass cc = this.pool.get(className);
                    ClassAnalyzer classAnalyzer = new ClassAnalyzer(cc);
                    int[] lines = classAnalyzer.countLines();
                    result.put(className, lines);
                } catch (NotFoundException var9) {
                    // System.out.println("DOH - " + className + " - " + var9);
                    // var9.printStackTrace(System.out);
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
