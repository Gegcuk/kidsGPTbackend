# ----- Stage 1: build -----
FROM maven:3.9.7-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

# ----- Stage 2: runtime -----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/kidsGPTbackend-*.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
