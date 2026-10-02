#!/bin/bash
# Manual Android build pipeline (Gradle is unusable in this sandbox).
# javac -> aapt2 compile/link -> d8 -> zipalign -> apksigner
set -e
source ~/workspace/android-toolchain/ENV.sh

APP=~/workspace/adblock-test-lab/app
BUILD=$APP/build
SDK=$HOME/workspace/android-toolchain/android-sdk
ANDROID_JAR=$SDK/platforms/android-36/android.jar
AAPT2=$SDK/build-tools/36.1.0/aapt2
D8=$SDK/cmdline-tools/latest/bin/d8
ZIPALIGN=$SDK/build-tools/36.1.0/zipalign
APKSIGNER=$SDK/build-tools/36.1.0/apksigner

VERSION_CODE=1
VERSION_NAME="1.0"

mkdir -p $BUILD/classes $BUILD/dex $BUILD/stage-libs

echo "==> generating BuildConfig"
mkdir -p $APP/src/com/dips/adblocktest
cat > $APP/src/com/dips/adblocktest/BuildConfig.java <<EOF
package com.dips.adblocktest;
public final class BuildConfig {
    public static final String APPLICATION_ID = "com.dips.adblocktest";
    public static final int VERSION_CODE = $VERSION_CODE;
    public static final String VERSION_NAME = "$VERSION_NAME";
    public static final boolean DEBUG = true;
}
EOF

echo "==> staging dependency jars"
# Extract classes.jar (+ nested jars) from every AAR, once.
for aar in $APP/libs/*.aar; do
    base=$(basename $aar .aar)
    dest=$BUILD/stage-libs/$base
    if [ ! -d "$dest" ]; then
        mkdir -p $dest
        unzip -o -q $aar 'classes.jar' -d $dest 2>/dev/null || true
        unzip -o -q $aar 'jars/libs/*.jar' -d $dest 2>/dev/null || true
    fi
done
# Drop the standalone listenablefuture jar: guava bundles the same classes
# and d8 rejects duplicate class definitions.
rm -f $APP/libs/listenablefuture-1.0.jar

CP="$ANDROID_JAR"
for j in $(find $BUILD/stage-libs -name '*.jar') $APP/libs/*.jar; do
    CP="$CP:$j"
done

echo "==> javac ($(find $APP/src -name '*.java' | wc -l) sources)"
find $APP/src -name '*.java' > $BUILD/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options \
    -cp "$CP" -d $BUILD/classes @$BUILD/sources.txt

echo "==> aapt2 compile + link"
$AAPT2 compile --dir $APP/res -o $BUILD/res.zip
# Compile AAR resources and collect library packages for R class generation.
# (Without this, SDK code referencing its own R class crashes with
# NoClassDefFoundError at runtime, e.g. androidx.startup.R$string.)
mkdir -p $BUILD/aar-res $BUILD/gen
> $BUILD/aar-res-args.txt
EXTRA_PKGS=""
for aar in $APP/libs/*.aar; do
    base=$(basename $aar .aar)
    pkg=$(unzip -p "$aar" AndroidManifest.xml 2>/dev/null | grep -oE 'package="[^"]+"' | head -1 | cut -d'"' -f2)
    if [ -n "$pkg" ] && [ "$pkg" != "com.dips.adblocktest" ]; then
        EXTRA_PKGS="$EXTRA_PKGS:$pkg"
    fi
    if unzip -l "$aar" 2>/dev/null | grep -q " res/"; then
        dest=$BUILD/aar-res/$base
        rm -rf "$dest" && mkdir -p "$dest"
        unzip -o -q "$aar" 'res/*' -d "$dest" 2>/dev/null || true
        if [ -d "$dest/res" ]; then
            if $AAPT2 compile --dir "$dest/res" -o "$dest/res.zip" 2>/dev/null; then
                echo "-R $dest/res.zip" >> $BUILD/aar-res-args.txt
            fi
        fi
    fi
done
EXTRA_PKGS=$(echo "$EXTRA_PKGS" | sed 's/^://')
$AAPT2 link -o $BUILD/base.apk \
    -I $ANDROID_JAR \
    --manifest $APP/AndroidManifest.xml \
    --auto-add-overlay \
    --min-sdk-version 24 --target-sdk-version 36 \
    --version-code $VERSION_CODE --version-name $VERSION_NAME \
    --java $BUILD/gen \
    --extra-packages "$EXTRA_PKGS" \
    -R $BUILD/res.zip $(cat $BUILD/aar-res-args.txt | tr '\n' ' ')
echo "==> compiling generated R classes"
find $BUILD/gen -name '*.java' > $BUILD/r-sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options \
    -cp "$ANDROID_JAR" -d $BUILD/classes @$BUILD/r-sources.txt

echo "==> d8"
cd $BUILD/classes && zip -q -r $BUILD/app-classes.jar . && cd $APP
# Dedupe: when a class is defined in multiple jars, keep the highest version's
# copy and filter the duplicates out of the losers (d8 rejects duplicates).
{ echo "$BUILD/app-classes.jar"; find $BUILD/stage-libs -name '*.jar'; ls $APP/libs/*.jar; } > $BUILD/dex-inputs.txt
DEX_INPUTS=$(python3 $APP/tools/dedupe_jars.py $BUILD/dex-inputs.txt $BUILD/filtered-libs)
$D8 --release --min-api 24 --lib $ANDROID_JAR \
    --output $BUILD/dex $DEX_INPUTS

echo "==> add dex, native libs, zipalign, sign"
for dex in $BUILD/dex/*.dex; do
    zip -j -q $BUILD/base.apk $dex
done
# Package native .so files from AARs (e.g. Unity's coherencelib) under lib/<abi>/
rm -rf $BUILD/apk-lib && mkdir -p $BUILD/apk-lib
for aar in $APP/libs/*.aar; do
    if unzip -l "$aar" 2>/dev/null | grep -q "jni/"; then
        dest=$BUILD/aar-res/$(basename $aar .aar)
        unzip -o -q "$aar" 'jni/*' -d "$dest" 2>/dev/null || true
        if [ -d "$dest/jni" ]; then
            for abi in "$dest/jni"/*; do
                [ -d "$abi" ] || continue
                abiname=$(basename "$abi")
                mkdir -p "$BUILD/apk-lib/lib/$abiname"
                cp -f "$abi"/*.so "$BUILD/apk-lib/lib/$abiname/" 2>/dev/null || true
            done
        fi
    fi
done
if [ -d "$BUILD/apk-lib/lib" ]; then
    (cd $BUILD/apk-lib && zip -q -r $BUILD/base.apk lib)
fi
$ZIPALIGN -f 4 $BUILD/base.apk $BUILD/aligned.apk

KS=$APP/debug.keystore
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore $KS -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" -noprompt
fi
$APKSIGNER sign --ks $KS --ks-pass pass:android --key-pass pass:android \
    --out $APP/app-debug.apk $BUILD/aligned.apk

echo "==> verify"
$APKSIGNER verify --print-certs $APP/app-debug.apk | head -4
ls -la $APP/app-debug.apk
