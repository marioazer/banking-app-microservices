# multi stage build, same shape as the rest of the services in this project, the build stage
# below never ships on its own, only the runtime stage further down becomes the real image
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
# workdir is the docker equivalent of cd, everything from here on runs out of /app
WORKDIR /app
# dependency resolution happens against just the pom first, before any source is even copied in,
# so this expensive step gets cached as its own layer and skipped if the pom did not change
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# source comes in after that, keeping it separate means editing code does not force a redownload
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage, small alpine jre image, only the built jar makes it in from the build stage above
FROM eclipse-temurin:17-jre-alpine
# jre only here, no jdk, no compiler needed just to run a jar, alpine keeps the base image tiny too
WORKDIR /app
# copy --from reaches into the build stage above and pulls out just the jar, the jdk and the
# maven dependency cache from that stage stay behind and never end up in this final image
COPY --from=build /app/target/*.jar app.jar
# expose is just documentation, it does not actually publish the port, still need -p 8080:8080
# at run time or the containerPort value set in the k8s manifest
EXPOSE 8080
# json array exec form here instead of a plain string, so java becomes pid 1 in the container
# and actually receives real shutdown signals instead of getting hard killed
ENTRYPOINT ["java", "-jar", "app.jar"]
