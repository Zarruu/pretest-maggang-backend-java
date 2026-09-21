FROM eclipse-temurin:17-jre
ARG SERVICE_NAME
WORKDIR /app
COPY ${SERVICE_NAME}/target/${SERVICE_NAME}-1.0.0.jar /app/app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
