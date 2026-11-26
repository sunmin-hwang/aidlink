FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system aidlink && useradd --system --gid aidlink aidlink
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER aidlink
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]