#!/bin/bash -x 
cd `dirname $0`
BASE=`pwd`
cd - 
#mvn compile

if [ -f $BASE/target/jarwalker ] && [ $BASE/target/jarwalker -nt $BASE/target/jarwalker-1.0-SNAPSHOT-shade.jar ] ; then 

jarwalker $@

elif [ -d $BASE/target/classes ] ; then 

java -cp $BASE/target/classes/:$BASE/target/*  mofokom.jarwalker.JarWalker $@

else 

java -jar $BASE/target/jarwalker-1.0-SNAPSHOT-shade.jar $@

fi
