# multi stage build, same pattern used across every service in this project, the build stage
# below compiles the jar but never ships, the runtime stage further down is the real image
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
# workdir sets the working directory inside the container, basically the same as cd /app
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
# resolving dependencies against just the pom first, before any source, means docker caches
# this whole step as one layer and only redoes it when the pom itself changes
RUN ./mvnw dependency:go-offline -B
# copying source in after dependencies are already resolved and cached, so a normal code
# change does not force redownloading every dependency all over again
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, ships as just the jre and the jar, nothing extra from the maven build stage
FROM eclipse-temurin:17-jre-alpine
# jre only, not the full jdk, running an already built jar does not need a compiler,
# and alpine keeps this base image about as small as it can get
WORKDIR /app
# copy --from pulls just the finished jar out of the build stage, the jdk and maven cache
# from that earlier stage are left behind entirely
COPY --from=build /app/target/*.jar app.jar
# expose only documents the port, it does not publish anything on its own, still need
# -p 8080:8080 at run time or containerPort set in the k8s manifest
EXPOSE 8080
# exec form (json array) rather than a plain string, so java runs as pid 1 and actually
# gets a real shutdown signal instead of getting forcefully killed on container stop
ENTRYPOINT ["java", "-jar", "app.jar"]
