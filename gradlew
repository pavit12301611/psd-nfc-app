#!/usr/bin/env sh
# Minimal Gradle wrapper launcher (gradle-wrapper.jar is fetched by CI / or present in gradle/wrapper).
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  mkdir -p "$APP_HOME/gradle/wrapper"
  curl -fsSL -o "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar
fi
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
