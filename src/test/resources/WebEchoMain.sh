#!/bin/bash
set -e

CP=./target/classes:./target/test-classes:~/.m2/repository/org/jetlang/jetlang/0.2.17/jetlang-0.2.17.jar:./*

JVM_ARGS='-server -XX:+UseConcMarkSweepGC -XX:+PrintGC -XX:+PrintGCDetails -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCApplicationConcurrentTime -XX:+PrintGCDateStamps -XX:+TieredCompilation'

exec java -Xms1024m -Xmx1024m -cp $CP $JVM_ARGS org.jetlang.remote.example.ws.WebSocketEchoMain $*