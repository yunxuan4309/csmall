FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 10010
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
