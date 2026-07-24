# still picking up docker as I build this, so extra notes for myself sprinkled through this file

# two from statements means this is a multi stage build, the first stage below is just for
# compiling, it never gets shipped, only the second from further down becomes the real image
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
# workdir is the container equivalent of cd, everything after runs from /app from here on out
WORKDIR /app
# copying just the wrapper and pom before the real source, this way docker can cache the
# dependency download as its own layer and skip it entirely if the pom has not changed
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# source comes in after that dependency step specifically so an ordinary code change does not
# invalidate the cached dependency layer above
COPY src ./src
# no tests here, those run in their own ci step, this stage is only responsible for the jar itself
RUN ./mvnw package -DskipTests -B

# Runtime stage, starts over from a fresh smaller base instead of building on the maven stage
FROM eclipse-temurin:17-jre-alpine
# jre, not the full jdk, no compiler needed to just run a jar that is already built,
# alpine also keeps the underlying os layer itself lightweight
WORKDIR /app
# copy --from pulls only the built jar out of the build stage above, everything else in that
# stage, the jdk, the maven cache, the raw source, never makes it into this final image
COPY --from=build /app/target/*.jar app.jar
# expose is documentation more than anything, it does not actually open the port on its own,
# still have to publish it with -p 8080:8080 or set containerPort in the k8s manifest
EXPOSE 8080
# using the json array exec form instead of a bare string so java ends up as pid 1 in the
# container and can actually receive a real shutdown signal instead of getting killed outright
ENTRYPOINT ["java", "-jar", "app.jar"]
