package com.sprd.powersavemodelauncher.allapps;

import com.sprd.powersavemodelauncher.AppInfo;
import com.sprd.powersavemodelauncher.BaseModelTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Created by unisoc on 2019/12/23
 */
@RunWith(RobolectricTestRunner.class)
public class AlphabeticalAppsListTest extends BaseModelTestCase {

    private AlphabeticalAppsList mAppsList;

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        mTargetContext = RuntimeEnvironment.application;
        mAppsList = new AlphabeticalAppsList(mTargetContext);
        initAllApps();

    }

    private void initAllApps() {
        List<AppInfo> apps = new ArrayList<>();
        apps.add(getInfo("app1"));
        apps.add(getInfo("app2"));
        apps.add(getInfo("app3"));
        apps.add(getInfo("app4"));
        apps.add(getInfo("app5"));
        mAppsList.setApps(apps);
    }

    @Test
    public void testAddApps() {
        List<AppInfo> appsToAdd = new ArrayList<>();
        appsToAdd.add(getInfo("title6"));
        mAppsList.addApps(appsToAdd);
        assertTrue(mAppsList.getApps().size() == 6);
    }

    @Test
    public void testRemoveApps() {
        List<AppInfo> appsToRemove = new ArrayList<>();
        appsToRemove.add(getInfo("app2"));
        mAppsList.removeApps(appsToRemove);
        assertTrue(mAppsList.getApps().size() == 4);
    }
}
