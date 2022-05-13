package com.mergebase.strings;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;

import java.util.Iterator;
import java.util.List;

public class ClassAnalyzer {
    private final CtClass cc;

    public ClassAnalyzer(CtClass cc) {
        this.cc = cc;
    }

    public int[] countLines() {
        int lineCount = 0;
        int behaviourCount = 0;
        CtBehavior[] var3 = this.cc.getDeclaredBehaviors();
        int var4 = var3.length;

        label38:
        for (int var5 = 0; var5 < var4; ++var5) {
            CtBehavior b = var3[var5];
            ++behaviourCount;
            MethodInfo mi = b.getMethodInfo2();
            if (mi != null) {
                CodeAttribute ca = mi.getCodeAttribute();
                if (ca != null) {
                    List<AttributeInfo> attrs = ca.getAttributes();
                    Iterator var10 = attrs.iterator();

                    while (true) {
                        AttributeInfo ai;
                        do {
                            if (!var10.hasNext()) {
                                continue label38;
                            }

                            ai = (AttributeInfo) var10.next();
                        } while (!(ai instanceof LineNumberAttribute));

                        LineNumberAttribute lna = (LineNumberAttribute) ai;
                        int maxLen = lna.get().length;
                        int maxPos = (maxLen - 4) / 4;

                        for (int i = 0; i <= maxPos; ++i) {
                            lineCount = Math.max(lna.lineNumber(i), lineCount);
                        }
                    }
                }
            }
        }

        return new int[]{lineCount, behaviourCount};
    }
}
