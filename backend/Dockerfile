# Build the jar first: ./gradlew :backend:buildFatJar   (CI does this)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY build/libs/bible-api.jar /app/bible-api.jar
USER app
# Sized for a 2 GB VPS shared with PostgreSQL and the admin site.
ENV JAVA_OPTS="-Xms96m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/bible-api.jar"]
