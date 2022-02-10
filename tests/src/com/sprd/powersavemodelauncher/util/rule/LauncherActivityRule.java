package com.sprd.powersavemodelauncher.util.rule;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Intent;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;

import com.sprd.powersavemodelauncher.PowerSaveLauncher;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Test rule to get the current Launcher activity.
 */
public class LauncherActivityRule implements TestRule {

    private PowerSaveLauncher mActivity;

    @Override
    public Statement apply(Statement base, Description description) {
        return new MyStatement(base);
    }

    /**
     * Starts the launcher activity in the target package.
     */
    public void startLauncher() {
        InstrumentationRegistry.getInstrumentation().startActivitySync(getHomeIntent());
    }

    public void returnToHome() {
        InstrumentationRegistry.getTargetContext().startActivity(getHomeIntent());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    public static Intent getHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(InstrumentationRegistry.getTargetContext().getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private class MyStatement extends Statement implements ActivityLifecycleCallbacks {

        private final Statement mBase;

        public MyStatement(Statement base) {
            mBase = base;
        }

        @Override
        public void evaluate() throws Throwable {
            Application app = (Application)
                    InstrumentationRegistry.getTargetContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(this);
            try {
                mBase.evaluate();
            } finally {
                app.unregisterActivityLifecycleCallbacks(this);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            if (activity instanceof PowerSaveLauncher) {
                mActivity = (PowerSaveLauncher) activity;
            }
        }

        @Override
        public void onActivityStarted(Activity activity) { }

        @Override
        public void onActivityResumed(Activity activity) { }

        @Override
        public void onActivityPaused(Activity activity) { }

        @Override
        public void onActivityStopped(Activity activity) { }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (activity == mActivity) {
                mActivity = null;
            }
        }
    }
}
