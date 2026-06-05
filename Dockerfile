# ─────────────────────────────────────────────────────────────────
# Stage 1 — Build: Maven + JDK 11 compiles the WAR
# ─────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy pom first — lets Docker cache the dependency download layer
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────
# Stage 2 — Runtime: Tomcat 10 + JRE 11 (slim)
# ─────────────────────────────────────────────────────────────────
FROM tomcat:10.1-jre11-temurin

LABEL maintainer="AnnaDaan"
LABEL description="AnnaDaan — Free Food India (v2.0)"

# Remove Tomcat's default sample webapps
RUN rm -rf /usr/local/tomcat/webapps/ROOT \
           /usr/local/tomcat/webapps/examples \
           /usr/local/tomcat/webapps/docs \
           /usr/local/tomcat/webapps/host-manager \
           /usr/local/tomcat/webapps/manager

# Deploy WAR as ROOT so the app is at  http://host:8080/
COPY --from=builder /build/target/freefood-app.war \
                    /usr/local/tomcat/webapps/ROOT.war

# Persistent data directory (SQLite DB + uploaded photos)
# Mount a volume here so data survives container restarts
RUN mkdir -p /data/freefood/uploads
ENV HOME=/data

# Tomcat tuning — UTF-8 URI encoding
COPY docker/server.xml /usr/local/tomcat/conf/server.xml

# Expose Tomcat's default HTTP port
EXPOSE 8080

# Healthcheck — Tomcat responds on / once the WAR is deployed
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1

CMD ["catalina.sh", "run"]
