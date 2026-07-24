# learning docker while building this, so a few extra notes here explaining the why not just the what

# this is a multi stage build, first from block builds the jar, second one further down is
# the actual image that ships, this is the service moving real money so keeping the runtime
# surface small feels especially worth understanding properly, not just copy pasting
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
# workdir sets the working directory inside the container, like running cd /app once up front
WORKDIR /app
# wrapper and pom copied in alone first, docker treats this as its own cached layer, so
# dependency:go-offline below only reruns when the pom itself actually changes
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# source copied in after the dependency layer so a plain code change does not bust that cache
COPY src ./src
# skipping tests here, ci already runs the full suite in its own step, this stage just builds the jar
RUN ./mvnw package -DskipTests -B

# Runtime stage, jre only, the jdk and the whole maven local repo from the build stage never leave it
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# copy --from reaches back into the build stage and grabs just the finished jar, nothing else
# from that stage makes the trip into this final image
COPY --from=build /app/target/*.jar app.jar
# expose documents the port for anyone reading the file, does not publish it by itself,
# still need -p 8080:8080 at run time or containerPort set in the k8s manifest
EXPOSE 8080
# exec form, the json array, instead of a plain string, so java runs as pid 1 and actually
# gets a real shutdown signal instead of being forcefully killed when the container stops
ENTRYPOINT ["java", "-jar", "app.jar"]
