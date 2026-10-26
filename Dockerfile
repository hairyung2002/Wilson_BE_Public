# Wilson BE 이미지 (api-spec 17장). K8s 매니페스트는 k8s/ 참고.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /app.jar

FROM eclipse-temurin:21-jre
# 일정·일별 집계·LocalDateTime이 KST 기준이다
ENV TZ=Asia/Seoul
RUN useradd --system --uid 10001 wilson
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
