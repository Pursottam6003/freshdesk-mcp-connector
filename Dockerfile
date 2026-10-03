# Stage 1: Build artifact
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN apt-get update && apt-get install -y maven \
    && mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r appgroup && useradd -r -g appgroup -m -d /home/appuser appuser
WORKDIR /app
COPY --from=builder /workspace/target/freshdesk-connector-1.0.0.jar app.jar

USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=mock
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]