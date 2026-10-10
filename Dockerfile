FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system onno && useradd --system --gid onno --home-dir /app onno
WORKDIR /app
COPY --from=build /workspace/build/libs/onno-subscriptions-1.0.0.jar app.jar
COPY deploy/render-start.sh /app/start.sh
RUN chmod +x /app/start.sh && mkdir /app/data && chown -R onno:onno /app
USER onno
ENV PORT=10000 SERVER_ADDRESS=0.0.0.0 SERVER_FORWARD_HEADERS_STRATEGY=framework
EXPOSE 10000
ENTRYPOINT ["/app/start.sh"]
