#!/bin/bash

# UWB-BLE Gateway — Safe APK Builder (No Android Studio Required)
# ✅ Detects Java, downloads gradle-wrapper.jar safely, builds release APK

set -e

echo "🔧 Building app-release.apk for Xiaomi 14 Pro..."
echo "========================================="

# 1. Check Java
if ! command -v java &> /dev/null; then
  echo "❌ Java not found. Install OpenJDK 21:"
  echo "   brew install openjdk@21 && brew link openjdk@21"
  exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1-2)
if [[ "$JAVA_VERSION" != "21" ]]; then
  echo "❌ Java 21 required. Found: $JAVA_VERSION"
  echo "   brew install openjdk@21 && brew link openjdk@21"
  exit 1
fi

echo "✅ Java 21 confirmed"

# 2. Setup Gradle Wrapper (minimal, safe)
GRADLE_WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
  echo "📥 Downloading gradle-wrapper.jar (official, 60KB)..."
  # Use curl if available, else fallback to manual instruction
  if command -v curl &> /dev/null; then
    mkdir -p gradle/wrapper
    curl -L https://services.gradle.org/distributions/gradle-8.4-bin.zip -o /tmp/gradle-8.4-bin.zip 2>/dev/null
    unzip -q /tmp/gradle-8.4-bin.zip "gradle-8.4/lib/gradle-wrapper.jar" -d /tmp/ 2>/dev/null
    mv /tmp/gradle-8.4/lib/gradle-wrapper.jar "$GRADLE_WRAPPER_JAR"
    rm -rf /tmp/gradle-8.4* /tmp/gradle-8.4-bin.zip
  else
    echo "❌ curl not found. Please run:"
    echo "   brew install curl"
    exit 1
  fi
fi

echo "✅ gradle-wrapper.jar ready"

# 3. Build APK
echo "🚀 Starting Gradle build (assembleRelease)..."
./gradlew assembleRelease --no-daemon

echo "🎉 Success! APK is at:"
echo "   $(pwd)/app/build/outputs/apk/release/app-release.apk"
echo ""
echo "💡 To install on Xiaomi 14 Pro:"
echo "   adb install -r \"$(pwd)/app/build/outputs/apk/release/app-release.apk\""
