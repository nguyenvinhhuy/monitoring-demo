# Build context = repo root (xem docker-compose.yml: context: . , dockerfile: product-service/Dockerfile)
# Dùng thẳng image Maven chính thức (đã pin sẵn version qua tag) thay vì copy Maven Wrapper vào
# build stage — image đã có đúng Maven pin theo tag rồi.
ARG MAVEN_VERSION=3.9.16
FROM maven:${MAVEN_VERSION}-eclipse-temurin-26 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
RUN mvn -q -pl product-service -am dependency:go-offline

COPY common/src common/src
COPY product-service/src product-service/src
RUN mvn -q -pl product-service -am package -DskipTests

FROM eclipse-temurin:26-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/product-service/target/product-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
