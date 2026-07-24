# multi stage build for transaction-service, this is the one moving actual money so image size and
# a minimal runtime surface matters just as much here as anywhere else in the project
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# source copied in after the dependency layer so a code change does not bust the dependency cache
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, jre only, the jdk and maven repo from the build stage never leave the build stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
