# multi stage build for audit-service, the last of the six services, all using the same simple pattern
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# copying source in after dependencies are already resolved and cached
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, ships as just the jre and the jar, nothing extra from the maven build stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
