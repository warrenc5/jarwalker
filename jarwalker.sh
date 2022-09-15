#!/bin/bash -x 
cd `dirname $0`
BASE=`pwd`
cd - 
#mvn compile

#JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native"
if [ -f $BASE/target/jarwalker ] && [ $BASE/target/jarwalker -nt $BASE/target/jarwalker-1.0-SNAPSHOT-shade.jar ] ; then 

jarwalker $@

elif [ -d $BASE/target/classes ] ; then 

java $JAVA_OPTS -cp $BASE/target/classes/:$BASE/target/*  mofokom.jarwalker.JarWalker $@

else 

java $JAVA_OPTS -jar $BASE/target/jarwalker-1.0-SNAPSHOT-shade.jar $@

fi
