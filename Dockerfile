FROM eclipse-temurin:21-jre-alpine AS capas
WORKDIR /build
COPY target/avianto-landing-1.0.0.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination /capas

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache fontconfig libwebp-tools
WORKDIR /app
COPY --from=capas /capas/dependencies/ ./
COPY --from=capas /capas/spring-boot-loader/ ./
COPY --from=capas /capas/snapshot-dependencies/ ./
COPY --from=capas /capas/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=15.0 -Djava.awt.headless=true"
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
