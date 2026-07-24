# multi stage build for account-service, same pattern as the other services in this project
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# copy wrapper and pom in first, letting docker cache the dependency download separately from source changes
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# real source comes after, no tests here, they run in their own ci step instead
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, lean jre alpine image, just the fat jar and nothing from the maven build layer
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
