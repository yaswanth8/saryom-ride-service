#!/bin/sh
# Starts the service with the start-up cache when it loads, and without it otherwise.
#
# The cache only works under the exact JVM settings it was recorded with
# (JAVA_OPTS, fixed in the Dockerfile). If it ever cannot be used, the JVM
# refuses to start rather than ignoring it, so it is checked first: a few
# seconds, against a service that would otherwise never come up.
if [ -f app.aot ] && java $JAVA_OPTS -XX:AOTCache=app.aot -cp app.jar -version > /dev/null 2>&1; then
    exec java $JAVA_OPTS -XX:AOTCache=app.aot -jar app.jar "$@"
fi
echo "Starting without the start-up cache" >&2
exec java $JAVA_OPTS -jar app.jar "$@"
