FROM eclipse-temurin:21-jre
RUN useradd --system app
COPY build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
