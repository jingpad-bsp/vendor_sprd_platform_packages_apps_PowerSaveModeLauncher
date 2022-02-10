#
# Build rule for PowerSaveModeLauncherTests
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := platform

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    ub-uiautomator \
    mockito-target-minus-junit4 \
    platform-test-annotations \
    truth-prebuilt \

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := PowerSaveModeLauncherTests
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_COMPATIBILITY_SUITE := device-tests

LOCAL_INSTRUMENTATION_FOR := PowerSaveModeLauncher

include $(BUILD_PACKAGE)