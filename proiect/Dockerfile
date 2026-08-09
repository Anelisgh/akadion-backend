FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.30.0/opentelemetry-javaagent.jar /otel.jar
EXPOSE 8081
ENTRYPOINT ["java", "-javaagent:/otel.jar", "-jar", "app.jar"]
