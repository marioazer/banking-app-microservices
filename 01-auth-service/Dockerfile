# still learning docker so leaving myself extra notes here while it all sinks in

# this is a multi stage build, two from lines instead of one, first stage just builds the jar
# and never actually ships, only the second stage below becomes the real image
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
# workdir is basically cd, everything after this runs from /app inside the container from now on
WORKDIR /app
# copying just the wrapper and the pom before any real source code, learned docker caches every
# instruction as its own layer, so as long as the pom does not change this dependency download
# layer gets reused on the next build instead of redownloading the whole internet again
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
# the real source comes in after the dependency step on purpose, if I copied src first every
# little code change would invalidate the cache above and redo the dependency download for nothing
COPY src ./src
# skipping tests here since ci runs the actual test suite in its own separate step, no point
# slowing down the image build by running everything twice
RUN ./mvnw package -DskipTests -B

# Runtime stage, this one starts completely fresh from a smaller base image, not from the build stage
FROM eclipse-temurin:17-jre-alpine
# jre only, not the full jdk, do not need a compiler just to run a jar that is already built
# alpine on top of that keeps the base image itself small too
WORKDIR /app
# copy --from pulls just the finished jar out of the build stage above, none of the maven cache,
# the jdk, or the raw source code ever makes it into this final image
COPY --from=build /app/target/*.jar app.jar
# expose is really just documentation for whoever reads this file later, it does not actually
# publish the port by itself, still need -p 8080:8080 or the containerPort in the k8s manifest
EXPOSE 8080
# using the exec form here, the json array, instead of a plain string so java actually becomes
# pid 1 in the container and receives real shutdown signals instead of getting hard killed
ENTRYPOINT ["java", "-jar", "app.jar"]
