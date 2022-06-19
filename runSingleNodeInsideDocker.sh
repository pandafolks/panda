#!/bin/sh

sbt assembly

docker run -p 8080:8080 -it $(docker build -q -f dockerFiles/Dockerfile .)
