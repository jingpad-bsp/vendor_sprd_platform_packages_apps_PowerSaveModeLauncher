package com.sprd.powersavemodelauncher.ui;

import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;

import com.sprd.powersavemodelauncher.R;
import com.sprd.powersavemodelauncher.util.rule.LauncherActivityRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNull;

/**
 * Created by unisoc on 2019/11/14
 */
@RunWith(AndroidJUnit4.class)
public class AllAppsTest extends AbstractLauncherUiTest {
    @Rule
    public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mDevice.wakeUp();
        mActivityMonitor.startLauncher();
        mDevice.waitForIdle();
        resetWorkspaceToDefault();
    }

    @Test
    public void testSettingsApp() {
        openAppsView();
        UiObject2 setting = scrollAndFind(findViewById(R.id.apps_list_view), By.text("Settings"));
        assertNull(setting);
    }
}
