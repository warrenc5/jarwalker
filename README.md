# jarwalker

Jarwalker is an old command line cli utility to walk into jars wars ears rars looking for regular expressions, dumping contents of manifest and service files, optionally deleting and rewriting nested jar files in place for hot patching.

Print the manifest in the jar file

>jarwalker common/lib/log4j.jar -m MANIFEST -c

```
Manifest-Version: 1.0
Implementation-Vendor: JBoss Inc.
Name: org/apache/log4j/
Implementation-Vendor: "Apache Software Foundation"
Implementation-Title: log4j
Implementation-Version: 1.2.14
```

Recursively scan nested jars looking matching entry

>jarwalker ./ -r -m 'log4j.*Jndi'

>jarwalker ./ -r -m 'log4j.*SocketServer'

Delete from and rewrite nested jars matching entry

>jarwalker ./ -r -m 'log4j.*Jndi' -d -d

```
  
java -jar target/jarwalker-1.0-SNAPSHOT.jar mofokom.jarwalker.JarWalker --help

Usage java -jar jarwalker-1.0-SNAPSHOT.jar -j [-m regexp] [-c] [-d] [-o] directory|jar|ear|war ...");
        -j detect duplicate jars files
        -r recursive for directories
        -f flat output. don't group by jar file
        -m regexp to match
        -c show contents of jar files
        -d delete matching entries from jars (readOnly) -d -d (overwrite)
        -x exclude regex
        -v verbose, -v -v even more verbose
        -h help"
```
