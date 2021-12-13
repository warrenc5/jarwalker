#!/bin/bash -x
jar cvf myjar.jar ./target/dependency/log4j-api-2.15.0.jar
mvn compile
java -cp target/classes/:target/jarwalker-1.0-SNAPSHOT-shade.jar  mofokom.jarwalker.JarWalker $@
#java -jar target/jarwalker-1.0-SNAPSHOT-shade.jar -cp target/classes/ mofokom.jarwalker.JarWalker $@
