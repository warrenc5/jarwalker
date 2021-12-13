# jarwalker

command line cli utility to walk into jars wars ears rars looking for regular expressions 

> jarwalker -m mofokom.slee.testfw.model.FactoryProxy -d . -r

```
  
java -jar target/jarwalker-1.0-SNAPSHOT-shade.jar -cp target/classes/ mofokom.jarwalker.JarWalker --help

Usage -j [-m regexp] [-c] [-d] [-o] directory|jar|ear|war ...
-j detect duplicate jars files
-r recursive for directories
-f flat output. don't group by jar file
-m regexp to match
-c show contents of jar files
-o show contents
-x exclude regex
-v verbose, -v -v even more verbose
-h help
```
