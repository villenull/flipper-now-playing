#!/usr/bin/env bash
NP_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${NP_ROOT}/.cache/jdk-17.0.16+8"
export ANDROID_HOME="${NP_ROOT}/.cache/android-sdk"
export GRADLE_USER_HOME="${NP_ROOT}/.cache/gradle-home"
export PATH="${JAVA_HOME}/bin:${NP_ROOT}/.cache/gradle-8.11.1/bin:${PATH}"
