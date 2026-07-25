# multi stage build, the build stage below compiles everything but never ships, only the
# runtime stage further down actually becomes the image that gets pushed and deployed
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
# workdir sets the working directory inside the image, same thing as running cd /app
WORKDIR /app
# copying the wrapper and pom by themselves first, before any real code, so this run line
# below gets cached as its own layer and only reruns when the pom itself actually changes
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# source has to come after the dependency step, otherwise every code edit would bust that
# cached layer above and force a full dependency redownload every single build
COPY src ./src
# tests skipped here on purpose, ci already runs the full suite separately so this step just
# focuses on producing the jar as fast as possible
RUN ./mvnw package -DskipTests -B

# Runtime stage, a brand new smaller image, not built on top of the maven stage above
FROM eclipse-temurin:17-jre-alpine
# jre instead of the full jdk since running a compiled jar does not need a compiler at all,
# and alpine keeps the base os layer itself as small as possible
WORKDIR /app
# copy --from grabs just the built jar out of the build stage, the jdk, maven cache and
# raw source from that stage all get left behind and never reach this final image
COPY --from=build /app/target/*.jar app.jar
# expose just documents the port for humans reading this file, it does not publish anything
# by itself, still need -p 8080:8080 or the containerPort set in the k8s manifest
EXPOSE 8080
# exec form (the json array) instead of a plain string command, so java runs as pid 1 and
# actually gets real shutdown signals instead of being force killed on container stop
ENTRYPOINT ["java", "-jar", "app.jar"]
