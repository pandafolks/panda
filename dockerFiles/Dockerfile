FROM adoptopenjdk/openjdk11:latest

ADD target/scala-**/panda.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app.jar"]
