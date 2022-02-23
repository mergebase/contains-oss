
# contains-oss - how to use #

contains-oss examines binary Java artifacts (e.g., *.jar, *.ear, *.war, *.class, etc)
to count the lines of code they contain, and to classify and tally each line of code as either
"Externally Developed" (a.k.a. open-source) or "Internally Developed" (a.k.a. proprietary
in-house code).

```
java -jar contains-oss-2022.02.23.jar <path-to-analyze>
```

contains-oss is quite powerful and will recursively scan any supplied paths aggressively,
including zip-files-inside-zip-files-inside-zip-files, etc.

To get started, try pointing contains-oss at itself!

```
java -jar contains-oss-2022.02.23.jar ./contains-oss-2022.02.23.jar
```

The output (on February 23rd, 2022) should look something like this:

```

{
"args":[".\/contains-oss-2022.02.23.jar"],

"totalLines":104873,
"totalInternal":3525,
"totalExternal":101348,
"proportionExternal":0.9663879168136699,

".\/contains-oss-2022.02.23.jar!\/**\/*.class":{
  "crc64":3683609628194793798,
  "percentage":100.0,
  "lines":104873,
  "lines.internal":3525,
  "lines.external":101348,
  "breakdown.internal":{
    "com.mergebase.strings":3525
  },
  "breakdown.external":{
    "javassist":14079,
    "javassist.bytecode":55593,
    "javassist.bytecode.analysis":3515,
    "javassist.bytecode.annotation":2811,
    "javassist.bytecode.stackmap":2517,
    "javassist.compiler":9051,
    "javassist.compiler.ast":1543,
    "javassist.convert":1096,
    "javassist.expr":2187,
    "javassist.runtime":292,
    "javassist.scopedpool":829,
    "javassist.tools":259,
    "javassist.tools.reflect":1555,
    "javassist.tools.rmi":1216,
    "javassist.tools.web":1065,
    "javassist.util":524,
    "javassist.util.proxy":3216
  }
}

}
```

# names.uniq.gz #

If a "names.uniq.gz" file exists in the current directory, "contains-oss" will use
that file to categorize Jar file contents as either "Internal" or "External".  Any
names that match names inside "names.uniq.gz" are considered "External".

We have pre-generated a "names.uniq.gz" file and included it in our Github repo.
It contains 3,636,788 fully-qualified Java classnames that we observed across
all artifacts we know of in Maven Central (circa January 2022).


# contains-oss - how to build #

```
mvn clean
mvn install
java -jar target/contains-oss-2022.02.23.jar
```
