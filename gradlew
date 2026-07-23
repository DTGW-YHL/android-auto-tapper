#!/bin/sh
APP_HOME=$(cd "${0%/*}" && pwd -P)
JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVA_CMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"

