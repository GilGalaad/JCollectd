FROM node:20 AS build-fe
WORKDIR /app
COPY ["web", "."]
RUN ["npm", "install"]
RUN ["npm", "run", "build"]

FROM maven:3.9-eclipse-temurin-21 AS build-be
WORKDIR /app
COPY ["pom.xml", "lombok.config", "."]
COPY ["src", "src/"]
COPY --from=build-fe ["/app/dist/jcollectd/browser", "src/main/resources/web/"]
RUN ["mvn", "clean", "package", "-DskipTests"]

FROM scratch AS out
COPY --from=build-be ["/app/target/jcollectd.jar", "/"]
