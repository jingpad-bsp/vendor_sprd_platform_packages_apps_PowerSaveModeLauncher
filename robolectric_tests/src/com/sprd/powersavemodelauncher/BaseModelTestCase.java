package com.sprd.powersavemodelauncher;

import android.content.ComponentName;
import android.content.Context;

import com.sprd.powersavemodelauncher.compat.UserHandleCompat;

import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;

public class BaseModelTestCase {

    protected Context mTargetContext;

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        mTargetContext = RuntimeEnvironment.application;
    }

    protected AppInfo getInfo(String title) {
        AppInfo info = new AppInfo();
        info.title = title;
        info.componentName = new ComponentName(title, "");
        info.user = UserHandleCompat.myUserHandle();
        return info;
    }
}
