FROM tomcat:8.5-jdk11-openjdk-slim
WORKDIR /usr/local/tomcat
COPY build/libs/io-file-1.0-SNAPSHOT.war webapps/ROOT.war
CMD ["catalina.sh", "run"]
EXPOSE 8080