#!/bin/bash -x

rm -rf test-jars
mkdir test-jars
#mvn dependency:copy -Dartifact=org.apache.logging.log4j:log4j-api:2.15.0
#mvn dependency:copy -Dartifact=log4j:log4j:1.2.17
mvn -Dmaven.test.skip -DskipTests package 

rm -rf tmp/*
echo `date` > test-jars/test-file.txt

jar cvf test-jars/myjar.jar ./target/dependency/log4j-api-2.15.0.jar target/dependency/log4j-1.2.17.jar test-jars/test-file.txt
#jar cvf test-jars/myjar.jar test-jars/test-file.txt
jar cvf test-jars/mynested.jar test-jars/myjar.jar ./target/dependency/log4j-api-2.15.0.jar test-jars/test-file.txt

cp test-jars/test-file.txt test-jars/test-file-new.txt
echo 'updated' >> test-jars/test-file-new.txt

#cat test-jars/test-file-new.txt | tee -a /dev/stdout | java -jar target/jarwalker-1.0-SNAPSHOT-shade.jar -cp target/classes/ mofokom.jarwalker.JarWalker -v -u -m test-file.txt test-jars/mynested.jar  

java -cp target/classes/ -jar target/jarwalker-1.0-SNAPSHOT-shade.jar  mofokom.jarwalker.JarWalker -v -v -w -u -m test-file.txt test-jars/mynested.jar  < test-jars/test-file-new.txt

java -cp target/classes/ -jar target/jarwalker-1.0-SNAPSHOT-shade.jar  mofokom.jarwalker.JarWalker -v -c -m test-file.txt -r test-jars/

cd tmp
find . 
jar tvf mynested.jar
jar xvf mynested.jar
cat test-jars/test-file.txt
jar xvf test-jars/myjar.jar
cat test-jars/test-file.txt
vdir

