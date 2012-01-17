#!/bin/bash

if [ $# -lt 1 ]
then
   echo ""
   echo "Must pass in the version number"
   echo ""
   exit 1
fi

VERSION=$1

ARTIFACT=starling-server-$VERSION.gz

if [ $1 == "SNAPSHOT" ]; then
  ARTIFACT_URL=http://nexus.global.trafigura.com:8081/nexus/content/repositories/starling-test/starling-releases/starling-server/$VERSION/$ARTIFACT
else
  ARTIFACT_URL=http://nexus.global.trafigura.com:8081/nexus/content/repositories/starling-releases/starling-releases/starling-server/$VERSION/$ARTIFACT
fi

wget $ARTIFACT_URL

tar xfz $ARTIFACT

cd starling

tar xfz misc/ivy-preload-sbt-0.11.0.tgz -C $HOME

sbt/sbt "project launcher" update

