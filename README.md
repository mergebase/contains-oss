
# contains-oss - How To Use

`contains-oss` is a Java tool to examine binary Java artifacts (e.g., *.jar, *.ear, *.war, *.class, etc)
to count the lines of code they contain, and to classify and tally each line of code as either
"Externally Developed" (a.k.a. open-source) or "Internally Developed" (a.k.a. proprietary
in-house code).

```
java -jar contains-oss-2022.07.07.jar <path-to-analyze>
```

## Requirements

Requires at least Java 8.

Because `contains-oss` unzips Jars completely into memory before analyzing,
it requires around twice as much RAM (Java Heap) as the largest Jar or Zip file you plan
to analyze.


## Sample Output

contains-oss is quite powerful and will recursively scan any supplied paths aggressively,
including zip-files-inside-zip-files-inside-zip-files, etc.

To get started, try pointing contains-oss at itself!

```
java -jar contains-oss-2022.07.07.jar ./contains-oss-2022.07.07.jar
```

The output (on February 23rd, 2022) should look something like this:

```

{
"args":[".\/contains-oss-2022.07.07.jar"],

"totalLines":104873,
"totalInternal":3525,
"totalExternal":101348,
"proportionExternal":0.9663879168136699,

".\/contains-oss-2022.07.07.jar!\/**\/*.class":{
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

## What About *.class Files Without Debug Symbols?

If there are no debug symbols in the *.class files, `contains-oss` uses the following heuristic to tally lines of code:
Each Java method counts as 17 lines of code.

In addition, `contains-oss` rounds up the total lines-of-code in this case to the nearest 100 or 1000, making it
straightforward to spot this situation in the output. For example, consider the following snippet
of `contains-oss` output. Notice how `org.w3c.css.sac` totaled to 2,000 lines, `org.w3c.dom.svg` totaled to 10,000 lines,
and `org.w3c.dom.smil` totaled to 100 lines. It's very unlikely these totals came from debug symbols.

```
"..\/easybuggy\/target\/easybuggy.jar!\/.war!\/WEB-INF\/lib\/xml-apis-ext-1.3.04.jar":{
  "crc64":169152488317647960,
  "percentage":0.24,
  "lines":12100,
  "lines.internal":0,
  "lines.external":12100,
  "breakdown.internal":{},
  "breakdown.external":{
    "org.w3c.css.sac":2000,
    "org.w3c.dom.smil":100,
    "org.w3c.dom.svg":10000
  }
},
```

To see this for yourself, try cloning and building https://github.com/k-tamura/easybuggy.
This is a great sample (unrelated to us) that happens to include a large number of Java artifacts
without debug symbols. Note: Easybuggy builds best using `mvn package` rather than `mvn install`.


## names.uniq.gz

If a `names.uniq.gz` file exists in the current directory, `contains-oss` will use
that file to categorize Jar file contents as either "Internal" or "External".  Any
names that match names inside `names.uniq.gz` are considered "External".

We have pre-generated a `names.uniq.gz` file and included it in our Github repo.
It contains 3,636,788 fully-qualified Java classnames that we observed across
all artifacts we know of in Maven Central (circa January 2022).


# contains-oss - how to build

For convenience, we have included a pre-compiled version of `contains-oss.jar` in the root
of our repository as `./contains-oss-2022.07.07.jar`, but you can also build
this tool yourself using the following sequence of commands:


```
mvn clean
mvn install
java -jar target/contains-oss-2022.07.07.jar
```
