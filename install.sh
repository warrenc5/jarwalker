#!/bin/sh
JAVA_HOME=/usr/local/java/graalvm mvn clean install -Dmaven.test.skip
JAVA_HOME=/usr/local/java/graalvm mvn -Pnative
sudo cp target/jarwalker /usr/local/bin/jarwalker
