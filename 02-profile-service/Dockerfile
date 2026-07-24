# multi stage build for profile-service, keeps the shipped image small by dropping the build tools
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# wrapper and pom copied in before the source so docker can cache this dependency layer
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# source comes in after, tests are skipped here since ci already runs the real test suite
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, just the jre and the jar, nothing else from the build stage makes it into this image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
