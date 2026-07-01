# Tomcat image for Al-Khwarizmi MDM with aapt installed so the panel can
# parse uploaded APK files (package id, version, icon).
FROM tomcat:9.0-jdk11-temurin

RUN apt-get update \
    && apt-get install -y --no-install-recommends aapt \
    && rm -rf /var/lib/apt/lists/*
