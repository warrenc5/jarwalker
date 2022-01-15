#!/bin/bash -x

rm -rf test-jars
mkdir test-jars

mvn dependency:copy -Dartifact=org.apache.logging.log4j:log4j-api:2.15.0
mvn dependency:copy -Dartifact=log4j:log4j:1.2.17

jar cvf test-jars/myjar.jar ./target/dependency/log4j-api-2.15.0.jar target/dependency/log4j-1.2.17.jar
jar cvf test-jars/mynested.jar test-jars/myjar.jar ./target/dependency/log4j-api-2.15.0.jar

mvn test -Dtest=TestJarWalker#runDelete

#java -cp target/classes/:target/jarwalker-1.0-SNAPSHOT-shade.jar  mofokom.jarwalker.JarWalker $@
#java -jar target/jarwalker-1.0-SNAPSHOT-shade.jar -cp target/classes/ mofokom.jarwalker.JarWalker $@

cd test-jars

jar tvf mynested.jar  
jar xvf mynested.jar test-jars/myjar.jar
jar tvf test-jars/myjar.jar  
jar xvf test-jars/myjar.jar target/dependency/log4j-api-2.15.0.jar
jar tvf ./target/dependency/log4j-api-2.15.0.jar | grep MAN
jar tvf ./target/dependency/log4j-api-2.15.0.jar | grep Async

jar xvf test-jars/myjar.jar target/dependency/log4j-1.2.17.jar
jar tvf ./target/dependency/log4j-1.2.17.jar | grep Async
jar tvf ./target/dependency/log4j-1.2.17.jar | grep Async
