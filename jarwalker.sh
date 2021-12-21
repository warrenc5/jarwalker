#!/bin/bash -x 
cd `dirname $0`
BASE=`pwd`
cd - 
#mvn compile

java -cp $BASE/target/classes/:$BASE/target/*  mofokom.jarwalker.JarWalker $@
