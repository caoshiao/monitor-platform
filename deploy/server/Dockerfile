FROM szh-java11
MAINTAINER csa
ARG JAR_FILE=monitor-server-1.0.0-SNAPSHOT.jar
RUN mkdir -p /app && chmod 777 /app
WORKDIR /app
COPY ${JAR_FILE} /app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Duser.timezone=GMT+08","-jar","/app.jar","--spring.config.location=./config/"]
