# Stage 1: build with Maven + JDK 24
FROM maven:3.9.5-jdk-24 AS builder
WORKDIR /workspace

# copy only what we need for dependency resolution first
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw

# download dependencies (speeds up CI)
RUN mvn -B -DskipTests dependency:go-offline

# copy sources and build
COPY src ./src
RUN mvn -B -DskipTests clean package

# Stage 2: runtime image with JRE 24
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
