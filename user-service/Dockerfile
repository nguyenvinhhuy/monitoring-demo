# Build context = repo root (xem docker-compose.yml: context: . , dockerfile: user-service/Dockerfile)
# vì đây là multi-module Maven reactor, cần thấy được toàn bộ pom.xml + module common.
# Dùng thẳng image Maven chính thức (đã pin sẵn version qua tag) thay vì Maven Wrapper — image đã
# có đúng Maven pin theo tag rồi, không cần copy thêm gì. Cùng image này cũng được Makefile dùng
# cho target build/test chạy trên host (xem MAVEN_IMAGE trong Makefile).
ARG MAVEN_VERSION=3.9.16
FROM maven:${MAVEN_VERSION}-eclipse-temurin-26 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
RUN mvn -q -pl user-service -am dependency:go-offline

COPY common/src common/src
COPY user-service/src user-service/src
RUN mvn -q -pl user-service -am package -DskipTests

FROM eclipse-temurin:26-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/user-service/target/user-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
