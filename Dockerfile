FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xmx180m", "-Xms64m", \
  "-XX:MaxMetaspaceSize=120m", \
  "-XX:ReservedCodeCacheSize=64m", \
  "-XX:+UseSerialGC", \
  "-Xss256k", \
  "-jar", "app.jar"]
