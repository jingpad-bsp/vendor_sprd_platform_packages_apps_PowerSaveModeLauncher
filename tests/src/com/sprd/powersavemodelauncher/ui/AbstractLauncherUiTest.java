package com.sprd.powersavemodelauncher.ui;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import com.sprd.powersavemodelauncher.LauncherAppState;
import com.sprd.powersavemodelauncher.LauncherModel;
import com.sprd.powersavemodelauncher.MainThreadExecutor;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;
import com.sprd.powersavemodelauncher.util.ComponentKey;
import com.sprd.powersavemodelauncher.util.rule.LauncherActivityRule;

import org.junit.Before;
import org.junit.Rule;

import java.util.HashMap;

import static org.junit.Assert.assertTrue;

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public abstract class AbstractLauncherUiTest {

    public static final int LONG_CLICK_TIME = 2000;
    public static final long SHORT_UI_TIMEOUT= 300;
    public static final long DEFAULT_UI_TIMEOUT = 3000;

    protected MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();
    protected UiDevice mDevice;
    protected Context mTargetContext;
    protected String mTargetPackage;

    @Rule
    public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
    }

    protected Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected void openAppsView() {
        assertTrue("Workspace is full : ",
                mDevice.wait(Until.hasObject(By.text("Add")), DEFAULT_UI_TIMEOUT));
        mDevice.wait(Until.findObject(By.text("Add")), DEFAULT_UI_TIMEOUT).click();
    }

    /**
     * Scrolls the {@param container} until it finds an object matching {@param condition}.
     * @return the matching object.
     */
    protected UiObject2 scrollAndFind(UiObject2 container, BySelector condition) {
        do {
            // findObject can only execute after spring settles.
            mDevice.wait(Until.findObject(condition), SHORT_UI_TIMEOUT);
            UiObject2 widget = container.findObject(condition);
            if (widget != null) {
                return widget;
            }
        } while (container.scroll(Direction.DOWN, 1f));
        return container.findObject(condition);
    }

    /**
     * Reset workspace with default items.
     */
    public void resetWorkspaceToDefault() {
        final UserHandleCompat user =  UserHandleCompat.myUserHandle();
        HashMap<Integer, ComponentKey> apps = new HashMap<>();
        apps.put(0, new ComponentKey(new ComponentName("com.android.dialer", "com.android.dialer.app.DialtactsActivity"), user));
        apps.put(1, new ComponentKey(new ComponentName("com.android.messaging", "com.android.messaging.ui.conversationlist.ConversationListActivity"), user));
        apps.put(2, new ComponentKey(new ComponentName("com.android.contacts", "com.android.contacts.activities.PeopleActivity"), user));
        LauncherModel.setDefaultWorkspaceItems(apps);
        resetLoaderState();
    }

    protected void resetLoaderState() {
        try {
            mMainThreadExecutor.execute(() ->
                    LauncherAppState.getInstance(mTargetContext).getModel().forceReload());
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
    }

    protected UiObject2 findViewById(int id) {
        return mDevice.wait(Until.findObject(getSelectorForId(id)), DEFAULT_UI_TIMEOUT);
    }

    protected BySelector getSelectorForId(int id) {
        String name = mTargetContext.getResources().getResourceEntryName(id);
        return By.res(mTargetPackage, name);
    }

}
