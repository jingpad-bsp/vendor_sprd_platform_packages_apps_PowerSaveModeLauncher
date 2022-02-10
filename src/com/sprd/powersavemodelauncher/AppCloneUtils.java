package com.sprd.powersavemodelauncher;

import android.os.UserHandle;

import java.lang.reflect.Method;

/**
 * Created on 5/15/18.
 */
public class AppCloneUtils {
    private static Class<?> mAppCloneUserInfoClass = null;
    private static Method mIsAppCloneUserMethod = null;

    private static Class<?> getAppCloneUserInfoClass() throws ClassNotFoundException {
        if (mAppCloneUserInfoClass == null) {
            mAppCloneUserInfoClass = Class.forName("android.content.pm.AppCloneUserInfo");
        }
        return mAppCloneUserInfoClass;
    }

    private static Method getIsAppCloneUserMethod() throws Exception {
        if (mIsAppCloneUserMethod == null) {
            Class clazz = getAppCloneUserInfoClass();
            mIsAppCloneUserMethod = clazz.getDeclaredMethod("isAppCloneUserId", int.class);
        }
        return mIsAppCloneUserMethod;
    }

    public static boolean isAppCloneUser(UserHandle user) {
        boolean ret = false;
        try {
            ret = (boolean) getIsAppCloneUserMethod().invoke(null, user.hashCode());
        } catch (Exception ignored) {
        }

        return ret;
    }
}
