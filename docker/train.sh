#!/bin/sh
# Records the start-up cache (app.aot) during the Docker build.
#
# The service is started as it would be in production, then stops by itself
# once every bean is created and before it opens a port or touches the network
# (spring.context.exit=onRefresh). Nothing is reachable at build time, so the
# database steps are switched off and Firebase gets a throwaway key; none of
# that changes which classes load, which is all the cache records.
#
# Never fails the build: without a cache the service still starts, just slower.
set -u
log=$(mktemp)
SPRING_PROFILES_ACTIVE="$TRAINING_PROFILES" \
FIREBASE_SERVICE_ACCOUNT_JSON="$(cat /tmp/training-sa.json)" \
DB_URL="jdbc:postgresql://127.0.0.1:1/training" \
java $JAVA_OPTS -XX:AOTCacheOutput=app.aot \
    -Dspring.context.exit=onRefresh \
    -Dspring.flyway.enabled=false \
    -Dspring.jpa.hibernate.ddl-auto=none \
    -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
    -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
    -jar app.jar > "$log" 2>&1
if [ -f app.aot ] && java $JAVA_OPTS -XX:AOTCache=app.aot -cp app.jar -version > /dev/null 2>&1; then
    echo "Start-up cache recorded: $(du -h app.aot | cut -f1)"
else
    echo "No start-up cache: the service will start without one. Training output:"
    grep -v "\[aot\]" "$log" | tail -25
    rm -f app.aot
fi
rm -f "$log"
