#!/bin/bash
echo $@
java -jar target/jarwalker-1.0-SNAPSHOT-shade.jar mofokom.jarwalker.JarWalker $@
