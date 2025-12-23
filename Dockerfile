FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon clean standaloneJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/fitnesse-*-standalone.jar /app/fitnesse.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/fitnesse.jar", "-p", "8080", "-e", "0"]
