#############################################
# PowerSaveModeLauncher Robolectric test target.      #
#############################################
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := PowerSaveModeLauncherRoboTests
LOCAL_SDK_VERSION := system_current
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_STATIC_JAVA_LIBRARIES := \
    androidx.test.runner \
    androidx.test.rules \
    androidx.test.core \
    hamcrest-library \
    mockito-robolectric-prebuilt \
    truth-prebuilt

LOCAL_JAVA_LIBRARIES := \
    platform-robolectric-3.6.2-prebuilt

LOCAL_JAVA_RESOURCE_DIRS := config

LOCAL_INSTRUMENTATION_FOR := PowerSaveModeLauncher
LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

############################################
# Target to run the previous target.       #
############################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunPowerSaveModeLauncherRoboTests
LOCAL_SDK_VERSION := system_current
LOCAL_JAVA_LIBRARIES := \
    PowerSaveModeLauncherRoboTests

LOCAL_TEST_PACKAGE := PowerSaveModeLauncher

LOCAL_INSTRUMENT_SOURCE_DIRS := $(dir $(LOCAL_PATH))../src \

LOCAL_ROBOTEST_TIMEOUT := 36000

include prebuilts/misc/common/robolectric/3.6.2/run_robotests.mk
