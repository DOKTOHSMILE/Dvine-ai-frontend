#!/bin/sh
# Scratch: compile and run CrewTest against Crew.java (JUnit 4.13.2). Delete after use.
set -e
LIBS=/data/data/com.termux/files/home/testlibs
OUT=/data/data/com.termux/files/home/crewtest
pwd
ls app/src/main/java/ai/dvine/agent/Crew.java app/src/test/java/ai/dvine/agent/CrewTest.java
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$LIBS/junit.jar:$LIBS/hamcrest.jar" -d "$OUT" \
  app/src/main/java/ai/dvine/agent/Crew.java \
  app/src/test/java/ai/dvine/agent/CrewTest.java
echo COMPILE_OK
java -cp "$OUT:$LIBS/junit.jar:$LIBS/hamcrest.jar" org.junit.runner.JUnitCore ai.dvine.agent.CrewTest