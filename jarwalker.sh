#!/bin/bash 
mvn compile
java -cp target/classes/:target/*  mofokom.jarwalker.JarWalker $@
