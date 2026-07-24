# multi stage build for auth-service so the final image does not carry the full maven toolchain around
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# copying just the wrapper and pom first so this dependency download layer gets cached between builds,
# only rebuilds if the pom itself actually changes, not on every single source code edit
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# now bring in the real source and build the jar, skipping tests here since ci runs them separately
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, this is the actual image that ships, jre only, no jdk or maven cache bloating it
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
