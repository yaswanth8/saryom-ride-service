# Built for Render's free instances: 512 MB of memory and a tenth of a CPU.
#
# On that CPU a plain Spring Boot start took three to four minutes, which is
# what a visitor waited after the service had been idle. Two changes bring it
# to under a minute (measured locally at 10% of one CPU):
#   - A start-up cache: Java 25's AOT cache, recorded in this build by
#     docker/train.sh, so classes are loaded and linked from a ready-made file.
#   - JVM settings for one slow CPU: the serial collector, and only the quick
#     first-tier compiler, so compilation does not compete with start-up.
#
# JAVA_OPTS is fixed here, for training and every start alike: the cache only
# works under the settings it was recorded with. MaxRAM pins the memory the JVM
# plans for; raise it and -Xmx together if the instance gets more memory.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests \
 && cp "$(ls target/*.jar | head -n 1)" app.jar \
 && java -Djarmode=tools -jar app.jar extract --destination extracted
COPY docker/TrainingCredentials.java docker/
RUN javac -d docker docker/TrainingCredentials.java

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV JAVA_OPTS="-XX:MaxRAM=512m -Xms64m -Xmx300m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
COPY --from=build /app/extracted/ ./
COPY --from=build /app/docker/TrainingCredentials.class /tmp/training/
COPY docker/train.sh docker/start.sh ./
# One step, so the throwaway training key never lands in an image layer.
RUN java -cp /tmp/training TrainingCredentials > /tmp/training-sa.json \
 && TRAINING_PROFILES=prod,rabbit sh train.sh \
 && rm -rf /tmp/training /tmp/training-sa.json train.sh
EXPOSE 8087
ENTRYPOINT ["sh", "start.sh"]
