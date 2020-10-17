FROM jhapy/base-image-slim

ENV JAVA_OPTS=""
ENV APP_OPTS=""

ADD target/utils-registry-server.jar /app/

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Xverify:none -Djava.security.egd=file:/dev/./urandom -Dpinpoint.agentId=$(date | md5sum | head -c 24) -jar /app/utils-registry-server.jar $APP_OPTS"]

HEALTHCHECK --interval=30s --timeout=30s --retries=10 CMD curl -f http://localhost:8186/management/health || exit 1

EXPOSE 8888 8186