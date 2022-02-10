package com.sprd.powersavemodelauncher.ui;

import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.widget.TextView;

import com.sprd.powersavemodelauncher.R;
import com.sprd.powersavemodelauncher.util.rule.LauncherActivityRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
/**
 * Created by unisoc on 2019/11/14
 */
@RunWith(AndroidJUnit4.class)
public class WorkspaceTest extends AbstractLauncherUiTest {

    private static final String PHONE_NAME = "Phone";
    private static final String CAMERA_NAME = "Camera";
    private static final String CONTACTS_NAME = "Contacts";

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
    public void testSwitchToAllApps() {
        openAppsView();

        UiObject2 allappsView = findViewById(R.id.apps_list_view);
        assertNotNull("Can not open all apps view :", allappsView);
    }

    @Test
    public void testAddIconToWorkspace() {
        openAppsView();
        assertNotNull("Can not open all apps view :", findViewById(R.id.apps_list_view));

        UiObject2 appsView = findViewById(R.id.apps_list_view);
        scrollAndFind(appsView, By.text(CAMERA_NAME)).click();
        UiObject2 object = findViewById(R.id.app_list_grid_view)
                .wait(Until.findObject(By.clazz(TextView.class).text(CAMERA_NAME)), DEFAULT_UI_TIMEOUT);
        assertNotNull("Can not add app workspace : ", object);
    }

    @Test
    public void testEnterEditMode() {
        findViewById(R.id.edit_image).click();
        assertNotNull("Can not enter edit mode : ", findViewById(R.id.delete_image));
    }

    @Test
    public void testWorkspaceIconLongClick() {
        findViewById(R.id.app_list_grid_view)
                .wait(Until.findObject(By.clazz(TextView.class).text(PHONE_NAME)), DEFAULT_UI_TIMEOUT)
                .click(LONG_CLICK_TIME);
        assertNotNull("Can not enter edit mode : ", findViewById(R.id.delete_image));
    }

    @Test
    public void testQuitEditMode() {
        findViewById(R.id.edit_image).click();
        assertNotNull("Can not enter edit mode : ", findViewById(R.id.delete_image));

        findViewById(R.id.quit_image).click();
        assertNull("Can not Exit edit mode : ", findViewById(R.id.delete_image));
    }

    @Test
    public void testCancelDelete() {
        findViewById(R.id.edit_image).click();
        assertNotNull("Can not enter edit mode : ", findViewById(R.id.delete_image));

        findViewById(R.id.app_list_grid_view)
                .wait(Until.findObject(By.clazz(TextView.class).text(PHONE_NAME)), DEFAULT_UI_TIMEOUT)
                .click();

        findViewById(R.id.quit_image).click();
        UiObject2 object = findViewById(R.id.app_list_grid_view)
                .wait(Until.findObject(By.clazz(TextView.class).text(PHONE_NAME)), DEFAULT_UI_TIMEOUT);
        assertNotNull("Fail to cancel delete icon from workspace : ", object);
    }

    @Test
    public void testDeleteIconFromWorkspace() {
        findViewById(R.id.edit_image).click();
        assertNotNull("Can not enter edit mode : ", findViewById(R.id.delete_image));

        findViewById(R.id.app_list_grid_view)
                .wait(Until.findObject(By.clazz(TextView.class).text(CONTACTS_NAME)), DEFAULT_UI_TIMEOUT)
                .click();

        findViewById(R.id.edit_image).click();
        UiObject2 object = findViewById(R.id.app_list_grid_view).
                wait(Until.findObject(By.clazz(TextView.class).text(CONTACTS_NAME)), DEFAULT_UI_TIMEOUT);
        assertNull("Fail to delete icon from workspace : ", object);
    }

    @After
    public void backToHome() {
        mDevice.pressBack();
        mDevice.pressHome();
    }
}
