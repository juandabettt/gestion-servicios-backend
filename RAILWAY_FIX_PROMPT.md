eemplaza el contenido del Dockerfile en la raíz del proyecto con este
FROM eclipse-temurin:21-maven-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/servicios-backend-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:InitialRAMPercentage=40.0", \
  "-XX:MaxMetaspaceSize=128m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]