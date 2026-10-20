# Build the jar, unpack it, then run it on a plain JRE as a non-root user.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/app.jar extract --destination /extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app app
WORKDIR /app
COPY --from=build /extracted/ ./
COPY data/sample_campaigns.csv data/sample_campaigns.csv

# Class-data archive to shorten startup. The training run starts the application with lazy
# initialisation and exits once the context has refreshed, so it never needs a database. Classes
# that only load on the first real request are not in the archive.
RUN java -XX:ArchiveClassesAtExit=/app/app.jsa -XX:+UseSerialGC -Xss512k \
        -Dspring.context.exit=onRefresh -Dspring.main.lazy-initialization=true -Dserver.port=0 \
        -jar app.jar \
 && chown -R app:app /app

USER app
ENV SPRING_PROFILES_ACTIVE=demo
EXPOSE 8080

# Sized for a 512 MB container with a fraction of a CPU. Add flags at run time through JAVA_TOOL_OPTIONS.
ENTRYPOINT ["java", \
    "-XX:SharedArchiveFile=/app/app.jsa", \
    "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=62", "-Xss512k", \
    "-XX:TieredStopAtLevel=1", "-XX:ReservedCodeCacheSize=64m", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]
