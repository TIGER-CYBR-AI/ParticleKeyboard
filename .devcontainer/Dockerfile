FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

RUN apt-get update && apt-get install -y openjdk-17-jdk unzip wget && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# ثبّت نسخة Gradle محددة وحديثة (بدل النسخة القديمة يلي بيجيبها apt)
RUN cd /opt && \
    wget -q https://services.gradle.org/distributions/gradle-8.9-bin.zip -O gradle.zip && \
    unzip -q gradle.zip && \
    rm gradle.zip && \
    mv gradle-8.9 gradle

ENV PATH="/opt/gradle/bin:${PATH}"

ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm cmdline-tools.zip

ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
