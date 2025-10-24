FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the JAR file
COPY target/mage-client-headless-*.jar /app/mage-client-headless.jar

# Set headless mode (just in case)
ENV JAVA_OPTS="-Djava.awt.headless=true"

# Default args (can be overridden)
ENV XMAGE_SERVER=localhost
ENV XMAGE_PORT=17171
ENV USERNAME=ClaudeBot

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/mage-client-headless.jar $XMAGE_SERVER $XMAGE_PORT $USERNAME"]
