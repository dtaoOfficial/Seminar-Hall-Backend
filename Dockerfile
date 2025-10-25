# Stage 1: build using Eclipse Temurin JDK 24 and install Maven manually
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /workspace

# install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# copy pom and download dependencies
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# copy sources and build
COPY src ./src
RUN mvn -B -DskipTests clean package

# Stage 2: lightweight runtime with JRE 24
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
