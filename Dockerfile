FROM alpine:latest as packager

RUN apk --no-cache add openjdk11-jdk openjdk11-jmods

ENV JAVA_MINIMAL="/opt/java-minimal"

# build minimal JRE
RUN /usr/lib/jvm/java-11-openjdk/bin/jlink \
    --verbose \
    --add-modules \
        java.base,java.sql,java.naming,java.desktop,java.management,java.security.jgss,java.instrument \
    --compress 2 --no-header-files --strip-debug --no-man-pages \
    --output "$JAVA_MINIMAL"

FROM alpine:latest

# Required dependency for Eclipse Trace Compass Server
RUN apk --no-cache add libc6-compat

ENV JAVA_HOME=/opt/java-minimal
ENV PATH="$PATH:$JAVA_HOME/bin"

COPY --from=packager "$JAVA_HOME" "$JAVA_HOME"
COPY target/products/traceserver/linux/gtk/x86_64/trace-compass-server/ /usr/src/myapp/
COPY entrypoint.sh /usr/src/myapp/entrypoint.sh

WORKDIR /usr/src/myapp/
EXPOSE 8080
CMD ["./tracecompass-server"]
