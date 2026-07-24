# multi stage build for notification-service, same shape as the rest of the services here
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# dependency resolution happens against just the pom first so this layer stays cached across builds
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, small alpine jre image, only the built jar makes it in from the build stage above
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
