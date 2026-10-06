FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -DskipTests \
    -Daether.dependencyCollector.impl=bf \
    -Daether.dependencyCollector.bf.threads=16 \
    -Daether.connector.basic.threads=16 package

FROM eclipse-temurin:21-jre-jammy
RUN command -v curl \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/sre-copilot-backend-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
