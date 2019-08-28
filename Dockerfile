FROM openjdk:8-jdk-alpine
COPY target/zero-downtime-rollingupdate-*.jar /application.jar
ENTRYPOINT ["java","-jar","/application.jar"]
