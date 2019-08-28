#!/bin/bash -e

echo "MAVEN_OPTS='-Xmx1024m'" > ~/.mavenrc
mvn clean package

