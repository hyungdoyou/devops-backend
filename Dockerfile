FROM openjdk:11-jdk-slim-stretch
COPY ./target/lonua-0.0.1-SNAPSHOT.jar lonua-0.0.1-SNAPSHOT.jar
CMD ["java", "-jar", "/lonua-0.0.1-SNAPSHOT.jar"]