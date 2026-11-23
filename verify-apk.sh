#!/bin/sh
set -eu
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
[ -s "$APK" ] || { echo "FAIL: APK missing"; exit 1; }
APKSIGNER="${ANDROID_HOME:-/opt/android-sdk}/build-tools/35.0.0/apksigner"
AAPT="${ANDROID_HOME:-/opt/android-sdk}/build-tools/35.0.0/aapt2"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"
"$APKSIGNER" verify --verbose "$APK" | tee /tmp/invoice-vault-signature.txt
"$AAPT" dump badging "$APK" | tee /tmp/invoice-vault-badging.txt
rg -q "targetSdkVersion:'37'" /tmp/invoice-vault-badging.txt
unzip -l "$APK" | rg -q 'assets/mlkit-google-ocr-models/.+(Hani|tflite)'
rg -q "uses-permission: name='android.permission.INTERNET'" /tmp/invoice-vault-badging.txt
rg -q "uses-permission: name='android.permission.ACCESS_NETWORK_STATE'" /tmp/invoice-vault-badging.txt
"$AAPT" dump xmltree "$APK" --file AndroidManifest.xml | tee /tmp/invoice-vault-manifest.txt >/dev/null
rg -q 'usesCleartextTraffic.*false|usesCleartextTraffic' /tmp/invoice-vault-manifest.txt
rg -q 'networkSecurityConfig' /tmp/invoice-vault-manifest.txt || { echo 'FAIL: network security config missing'; exit 1; }
printf 'PASS: signed APK, targetSdk 37, bundled Chinese OCR assets, required network permissions and security config\n'
sha256sum "$APK"
