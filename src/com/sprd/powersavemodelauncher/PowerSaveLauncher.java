/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sprd.powersavemodelauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.sprd.PlatformHelper;
import com.sprd.powersavemodelauncher.allapps.AllAppsContainerView;
import com.sprd.powersavemodelauncher.allapps.DefaultAppSearchController;
import com.sprd.powersavemodelauncher.compat.LauncherActivityInfoCompat;
import com.sprd.powersavemodelauncher.compat.LauncherAppsCompat;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;
import com.sprd.powersavemodelauncher.util.ComponentKey;
import com.sprd.powersavemodelauncher.util.Thunk;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Default launcher application.
 */
public class PowerSaveLauncher extends Activity implements View.OnClickListener, LauncherModel.Callbacks{
    static final String TAG = "PowerSaveLauncher";
    static final boolean LOGD = false;

    enum State { WORKSPACE, APPS}

    @Thunk
    State mState = State.WORKSPACE;

    PowerSaveWorkspace mPowerSaveWorkspace;
    @Thunk AllAppsContainerView mAppsView;

    private boolean mPaused = true;
    private boolean mOnResumeNeedsLoad;
    private ArrayList<Runnable> mBindOnResumeCallbacks = new ArrayList<Runnable>();

    private LauncherModel mModel;
    private DeviceProfile mDeviceProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherAppState app = LauncherAppState.getInstance(this);
        mDeviceProfile = app.getInvariantDeviceProfile().profile;
        //set launcher callbacks
        mModel = app.setLauncher(this);
        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;
        setContentView(R.layout.power_save_launcher);

        // Setup workspace
        mPowerSaveWorkspace = (PowerSaveWorkspace)findViewById(R.id.power_save_workspace);
        // Setup Apps
        mAppsView = (AllAppsContainerView) findViewById(R.id.apps_view);
        mAppsView.setSearchBarController(new DefaultAppSearchController());
        mPowerSaveWorkspace.setBackground(getDrawable(R.drawable.workspace_background));

        mModel.startLoaderData();
    }

    public void forceReload(){
        mModel.forceReload();
    }
    public void addAppToWorkspaceFromAllApps(View view) {
        Object tag = view.getTag();
        if(tag instanceof AppInfo) {
            AppInfo appInfo = (AppInfo)tag;
            mPowerSaveWorkspace.addAppWorkspaceFromApps(appInfo);

            //show workspace view
            showWorkspace();
        }
        mModel.forceReload();
    }


    public void showWorkspace() {
        if (mState != State.WORKSPACE) {
            mAppsView.reset();
            mAppsView.setVisibility(View.GONE);
            if (mPowerSaveWorkspace != null) {
                mPowerSaveWorkspace.setVisibility(View.VISIBLE);
                // Change the state after we've called the transition code
                mState = State.WORKSPACE;
            }
        }
    }

    /**
     * Shows the apps view.
     */
    void showAppsView() {
        if(mState != State.APPS) {
            //hide workspace
            if(mPowerSaveWorkspace != null) {
                mPowerSaveWorkspace.setVisibility(View.GONE);
            }

            //show AllApp View
            final View contentView = mAppsView.getContentView();
            mAppsView.setTranslationX(0.0f);
            mAppsView.setTranslationY(0.0f);
            mAppsView.setScaleX(1.0f);
            mAppsView.setScaleY(1.0f);
            mAppsView.scrollToTop();
            mAppsView.setVisibility(View.VISIBLE);
            mAppsView.bringToFront();
            // Show the content view
            contentView.setVisibility(View.VISIBLE);
        }

        mState = State.APPS;
    }

    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    /**
     * Updates the bounds of all the overlays to match the new fixed bounds.
     */
    public void updateOverlayBounds(Rect newBounds) {
        mAppsView.setSearchBarBounds(newBounds);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restore the previous launcher state
        if (mState == State.WORKSPACE) {
            showWorkspace();
        } else if (mState == State.APPS) {
            showAppsView();
        }

        mPaused = false;
        if (mOnResumeNeedsLoad) {
            // If we're starting binding all over again, clear any bind calls we'd postponed in
            // the past (see waitUntilResume) -- we don't need them since we're starting binding
            // from scratch again
            mBindOnResumeCallbacks.clear();
            mOnResumeNeedsLoad = false;
        }
        if (mBindOnResumeCallbacks.size() > 0) {

            for (int i = 0; i < mBindOnResumeCallbacks.size(); i++) {
                mBindOnResumeCallbacks.get(i).run();
            }
            mBindOnResumeCallbacks.clear();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        if (isActionMain) {
            if (mPowerSaveWorkspace == null) {
                return;
            }

            showWorkspace();

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            // Reset the apps view
            if (mAppsView != null) {
                mAppsView.scrollToTop();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop callbacks from LauncherModel
        LauncherAppState app = (LauncherAppState.getInstance(this));

        // It's possible to receive onDestroy after a new PowerSaveLauncher activity has
        // been created. In this case, don't interfere with the new PowerSaveLauncher.
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
            mModel.stopUpdated();
            app.setLauncher(null);
        }

        TextKeyListener.getInstance().release();

        mPowerSaveWorkspace = null;
    }

    @Override
    public void onBackPressed() {
        if (isAppsViewVisible()) {
            showWorkspace();
        } else if(isWorkspaceVisible()) {
            if(PowerSaveWorkspace.isInEditMode) {
                if(mPowerSaveWorkspace != null) {
                    int size = mPowerSaveWorkspace.getReadyToRemovePositions().size();
                    if(size > 0) {
                        showQuitEditModeDialog();
                    } else {
                        mPowerSaveWorkspace.quitEditModeAndRefreshView(false);
                    }
                }
            }
        }
    }

    private void showQuitEditModeDialog() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(R.string.save_edit_message)
                .setNegativeButton(R.string.workspace_cancel_save_edit,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //discard changes
                                mPowerSaveWorkspace.quitEditModeAndRefreshView(false);
                            }
                        })
                .setPositiveButton(R.string.workspace_confirm_save_edit,
                        new DialogInterface.OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //save changes
                                mPowerSaveWorkspace.quitEditModeAndRefreshView(true);
                            }
                        })
                .show();
    }
    /**
     * @param v The view representing the clicked app.
     */
    public void onClick(View v) {
        if (v.getWindowToken() == null) {
            return;
        }

        //appsContainerView
        addAppToWorkspaceFromAllApps(v);
    }

    public boolean isAppsViewVisible() {
        return mState == State.APPS;
    }

    public boolean isWorkspaceVisible() {
        return mState == State.WORKSPACE;
    }

    /**
     * If the activity is currently paused, signal that we need to run the passed Runnable
     * in onResume.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Thunk boolean waitUntilResume(Runnable run, boolean deletePreviousRunnables) {
        if (mPaused) {
            if (LOGD) Log.d(TAG, "Deferring update until onResume");
            if (deletePreviousRunnables) {
                while (mBindOnResumeCallbacks.remove(run)) {
                }
            }
            mBindOnResumeCallbacks.add(run);
            return true;
        } else {
            return false;
        }
    }

    private boolean waitUntilResume(Runnable run) {
        return waitUntilResume(run, false);
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            if (LOGD) Log.d(TAG, "setLoadOnResume");
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void bindWorkspaceItems(final ArrayList<ItemInfo> itemInfos) {
        Runnable r = new Runnable() {
            public void run() {
                bindWorkspaceItems(itemInfos);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if(mPowerSaveWorkspace != null) {
            mPowerSaveWorkspace.updateWorkspaceItems(itemInfos);
        }
    }

    @Override
    public void bindWorkspaceItemsRemoved(HashMap<Integer, ComponentKey> removeData) {
        Log.d(TAG, "bindWorkspaceItemsRemoved.  removeData.size: "+removeData.size());

        if(mPowerSaveWorkspace != null) {
            mPowerSaveWorkspace.synchronousUltraSavingIfNeeded();
        }
        for (HashMap.Entry<Integer, ComponentKey> entry : removeData.entrySet()) {
            int position = entry.getKey();
            ComponentKey componentKey = entry.getValue();
            ComponentName componentName = componentKey.componentName;
            String cnStr = componentName != null ? componentName.flattenToShortString() : "";
            UserHandleCompat user = componentKey.user;
            if (!TextUtils.isEmpty(cnStr)) {
                String delValue = cnStr + "#" + position +
                        "#" + Utilities.getUserSerialNumber(this, user);
                if (PlatformHelper.delAllowedAppInUltraSavingMode(this, delValue)) {
                    Log.d(TAG, "delete " + delValue + " from allowed app list success!");

                    //clear the selected position
                    if (mPowerSaveWorkspace != null) {
                        mPowerSaveWorkspace.updateSelectedPositionItem(position, null);
                    }
                }
            }
        }

        if(mPowerSaveWorkspace != null) {
            mPowerSaveWorkspace.notifyAdapterDataChanged();
        }
    }

    public void bindAppsAdded(final ArrayList<AppInfo> addedApps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsAdded(addedApps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if(mPowerSaveWorkspace != null) {
            HashMap<Integer, ComponentKey> workspaceMap = mModel.getAllowedAppMap(true);
            boolean isUpdated = false;
            for (AppInfo info : addedApps) {
                ComponentKey componentKey = new ComponentKey(info.componentName,info.user);
                for (HashMap.Entry<Integer, ComponentKey> entry : workspaceMap.entrySet()) {
                    if (entry.getValue().equals(componentKey)) {
                        mPowerSaveWorkspace.updateSelectedPositionItem(entry.getKey(), info);
                        isUpdated = true;
                    }
                }
            }
            if (isUpdated) {
                mPowerSaveWorkspace.notifyAdapterDataChanged();
            }
        }

        if (addedApps != null && mAppsView != null) {
            mAppsView.addApps(addedApps);
        }
    }

    /**
     * A runnable that we can dequeue and re-enqueue when all applications are bound (to prevent
     * multiple calls to bind the same list.)
     */
    @Thunk ArrayList<AppInfo> mTmpAppsList;
    private Runnable mBindAllApplicationsRunnable = new Runnable() {
        public void run() {
            bindAllApplications(mTmpAppsList);
            mTmpAppsList = null;
        }
    };

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<AppInfo> apps) {
        if (waitUntilResume(mBindAllApplicationsRunnable, true)) {
            mTmpAppsList = apps;
            return;
        }

        if (mAppsView != null) {
            mAppsView.setApps(apps);
        }
    }

    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(final ArrayList<AppInfo> apps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsUpdated(apps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mAppsView != null) {
            mAppsView.updateApps(apps);
        }
    }

    @Override
    public void bindAppInfosRemoved(final ArrayList<AppInfo> appInfos) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppInfosRemoved(appInfos);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Update AllApps
        if (mAppsView != null) {
            mAppsView.removeApps(appInfos);
        }
    }

    /**
     * Returns a FastBitmapDrawable with the icon, accurately sized.
     */
    public FastBitmapDrawable createIconDrawable(Bitmap icon) {
        FastBitmapDrawable d = new FastBitmapDrawable(icon);
        d.setFilterBitmap(true);
        resizeIconDrawable(d);
        return d;
    }

    /**
     * Resizes an icon drawable to the correct icon size.
     */
    public Drawable resizeIconDrawable(Drawable icon) {
        icon.setBounds(0, 0, mDeviceProfile.iconSizePx, mDeviceProfile.iconSizePx);
        return icon;
    }

}
