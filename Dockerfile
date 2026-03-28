FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy only pom.xml first (dependency layer)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

RUN ./mvnw dependency:go-offline

# Now copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/payment-system-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]