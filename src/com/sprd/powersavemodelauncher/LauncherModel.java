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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.sprd.PlatformHelper;
import com.sprd.powersavemodelauncher.compat.LauncherActivityInfoCompat;
import com.sprd.powersavemodelauncher.compat.LauncherAppsCompat;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;
import com.sprd.powersavemodelauncher.compat.UserManagerCompat;
import com.sprd.powersavemodelauncher.util.ComponentKey;
import com.sprd.powersavemodelauncher.util.PackageManagerHelper;
import com.sprd.powersavemodelauncher.util.StringFilter;
import com.sprd.powersavemodelauncher.util.Thunk;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Maintains in-memory state of the PowerSaveLauncher. It is expected that there should be only one
 * LauncherModel object held in a static.
 */
public class LauncherModel extends BroadcastReceiver
        implements LauncherAppsCompat.OnAppsChangedCallbackCompat {
    static final boolean DEBUG_LOADERS = false;
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "PowerSaveLauncher.Model";

    static final ArrayList<ItemInfo> sBgWorkspaceItems = new ArrayList<>();

    /**
     * For Workspace test
     */
    private static HashMap<Integer, ComponentKey> sAppsForWorkspaceTest = new HashMap<>();

    final static int ITEM_COUNT = 6;

    @Thunk final LauncherAppState mApp;
    @Thunk final Object mLock = new Object();
    @Thunk DeferredHandler mHandler = new DeferredHandler();
    @Thunk LoaderTask mLoaderTask;
    @Thunk boolean mHasLoaderCompletedOnce;

    @Thunk static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    static {
        sWorkerThread.start();
    }
    @Thunk static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    @Thunk WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    private final AllAppsList mBgAllAppsList;

    // The lock that must be acquired before referencing any static bg data structures.  Unlike
    // other locks, this one can generally be held long-term because we never expect any of these
    // static data structures to be referenced outside of the worker thread except on the first
    // load after configuration change.
    static final Object sBgLock = new Object();

    // sPendingPackages is a set of packages which could be on sdcard and are not available yet
    static final HashMap<UserHandleCompat, HashSet<String>> sPendingPackages =
            new HashMap<UserHandleCompat, HashSet<String>>();

    public IconCache mIconCache;

    protected float mFontScale;
    protected int mDensity;

    public static boolean mIsLocalChanged = false;

    public final LauncherAppsCompat mLauncherApps;
    @Thunk final UserManagerCompat mUserManager;

    public static List<String> mRemovedPackageList = new ArrayList<>();

    public interface Callbacks {
        boolean setLoadOnResume();
        void bindAllApplications(ArrayList<AppInfo> apps);
        void bindAppsAdded(ArrayList<AppInfo> addedApps);
        void bindAppsUpdated(ArrayList<AppInfo> apps);
        void bindAppInfosRemoved(ArrayList<AppInfo> appInfos);

        void bindWorkspaceItems(ArrayList<ItemInfo> bgItemInfos);
        void bindWorkspaceItemsRemoved(HashMap<Integer, ComponentKey> removeData);
    }

    LauncherModel(LauncherAppState app, IconCache iconCache) {
        Context context = app.getContext();
        mApp = app;

        mBgAllAppsList = new AllAppsList(iconCache);
        mIconCache = iconCache;

        Configuration config = context.getResources().getConfiguration();
        mFontScale = config.fontScale;
        mDensity = config.densityDpi;

        mLauncherApps = LauncherAppsCompat.getInstance(context);
        mUserManager = UserManagerCompat.getInstance(context);

        //Add for removed package list
        String[] hidePackages = context.getResources().getStringArray(R.array.remove_package_name);
        mRemovedPackageList = Arrays.asList(hidePackages);
    }

    @Thunk void runOnMainThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            // If we are on the worker thread, post onto the main handler
            mHandler.post(r);
        } else {
            r.run();
        }
    }

    @Thunk static void runOnWorkerThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            r.run();
        } else {
            // If we are not on the worker thread, then post to the worker handler
            sWorker.post(r);
        }
    }

    public void addAppsToAllApps(final Context ctx, final ArrayList<AppInfo> allAppsApps) {
        final Callbacks callbacks = getCallback();

        if (allAppsApps == null) {
            throw new RuntimeException("allAppsApps must not be null");
        }
        if (allAppsApps.isEmpty()) {
            return;
        }

        // Process the newly added applications and add them to the database first
        Runnable r = new Runnable() {
            public void run() {
                runOnMainThread(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsAdded( allAppsApps);
                        }
                    }
                });
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Set this as the current PowerSaveLauncher activity object for the loader.
     */
    public void initialize(PowerSaveLauncher powerSaveLauncher) {
        synchronized (mLock) {
            mHandler.cancelAll();
            Callbacks callbacks = (Callbacks) powerSaveLauncher;
            mCallbacks = new WeakReference<Callbacks>(callbacks);
        }
    }

    @Override
    public void onPackageChanged(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_UPDATE;
        enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_REMOVE;
        enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackageAdded(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_ADD;
        enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandleCompat user,
            boolean replacing) {
        enqueuePackageUpdated(
                new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, packageNames, user));
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandleCompat user,
            boolean replacing) {
        if (!replacing) {
            enqueuePackageUpdated(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_UNAVAILABLE, packageNames,
                    user));
        }
    }

    @Override
    public void onPackagesSuspended(String[] packageNames, UserHandleCompat user) {
        enqueuePackageUpdated(new PackageUpdatedTask(
                PackageUpdatedTask.OP_SUSPEND, packageNames,
                user));
    }

    @Override
    public void onPackagesUnsuspended(String[] packageNames, UserHandleCompat user) {
        enqueuePackageUpdated(new PackageUpdatedTask(
                PackageUpdatedTask.OP_UNSUSPEND, packageNames,
                user));
    }

    public void  startLoaderData() {
        // Don't bother to start the thread if we know it's not going to do anything
        if (mCallbacks != null && mCallbacks.get() != null) {
            // If there is already one running, tell it to stop.
            stopLoaderLocked();
            mLoaderTask = new LoaderTask(mApp.getContext());
            sWorkerThread.setPriority(Thread.NORM_PRIORITY);
            sWorker.post(mLoaderTask);
        }
    }

    /**
     * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED and
     * ACTION_PACKAGE_CHANGED.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG_RECEIVER) Log.d(TAG, "onReceive intent=" + intent);
        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            mIsLocalChanged = true;
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            UserManagerCompat.getInstance(context).enableAndResetCache();
            forceReload();
        } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
            // Check if configuration change was an mcc/mnc change which would affect app resources
            // and we would need to clear out the labels in all apps/workspace. Same handling as
            // above for ACTION_LOCALE_CHANGED
            Configuration currentConfig = context.getResources().getConfiguration();
            if (mFontScale != currentConfig.fontScale || mDensity != currentConfig.densityDpi) {
                Log.d(TAG, "Configuration changed, restarting launcher");
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        } else if (LauncherAppsCompat.ACTION_MANAGED_PROFILE_ADDED.equals(action)
                || LauncherAppsCompat.ACTION_MANAGED_PROFILE_REMOVED.equals(action)) {
            UserManagerCompat.getInstance(context).enableAndResetCache();
            forceReload();
        } else if (LauncherAppsCompat.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                LauncherAppsCompat.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)) {
            UserHandleCompat user = UserHandleCompat.fromIntent(intent);
            if (user != null) {
                enqueuePackageUpdated(new PackageUpdatedTask(
                        PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE,
                        new String[0], user));
            }
        }
    }

    public void forceReload() {
        synchronized (mLock) {
            // Stop any existing loaders first
            stopLoaderLocked();
        }
        startLoaderFromBackground();
    }

    public void startLoaderFromBackground() {
        boolean runLoader = false;
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            // Only actually run the loader if they're not paused.
            if (!callbacks.setLoadOnResume()) {
                runLoader = true;
            }
        }
        if (runLoader) {
            startLoaderData();
        }
    }

    /**
     * If there is already a loader task running, tell it to stop.
     */
    private void stopLoaderLocked() {
        LoaderTask oldTask = mLoaderTask;
        if (oldTask != null) {
            oldTask.stopLocked();
        }
    }

    public boolean isCurrentCallbacks(Callbacks callbacks) {
        return (mCallbacks != null && mCallbacks.get() == callbacks);
    }

    public void stopLoader() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                mLoaderTask.stopLocked();
            }
        }
    }

    public HashMap<Integer, ComponentKey> getAllowedAppMap(boolean isInitial) {
        HashMap<Integer, ComponentKey> allowedAppMap = new HashMap<>();
        if (mApp == null || mApp.getContext() == null) {
            return allowedAppMap;
        }
        Context context = mApp.getContext();
        List<String> allowedAppList = PlatformHelper.getAllowedAppListInUltraSavingMode(context);
        for(String value:allowedAppList) {
            String[] sArrays = value.split("#");
            if(sArrays.length >= 2){
                String componentStr = sArrays[0];
                int position = Integer.valueOf(sArrays[1]);
                UserHandle user;
                if(sArrays.length >= 3) {
                    long userSerialNumber = Integer.valueOf(sArrays[2]);
                    user = Utilities.getUserHandleFromSerialNumber(context, userSerialNumber);
                } else {
                    user = UserHandleCompat.myUserHandle().getUser();
                    if (PlatformHelper.delAllowedAppInUltraSavingMode(context, value)) {
                        UserHandleCompat myUser = UserHandleCompat.myUserHandle();
                        String addValue = value + "#" + Utilities.getUserSerialNumber(context, myUser);
                        PlatformHelper.addAllowedAppInUltraSavingMode(context, addValue);
                    }
                }
                if (user == null) continue;
                ComponentName componentName = ComponentName.unflattenFromString(componentStr);
                ComponentKey componentKey = new ComponentKey(componentName,UserHandleCompat.fromUser(user));

                if(isInitial) {
                    if(isValidPackageActivity(context, componentName, UserHandleCompat.fromUser(user))
                            && !mRemovedPackageList.contains(componentName.getPackageName())) {
                        allowedAppMap.put(position, componentKey);
                    }
                } else {
                    allowedAppMap.put(position, componentKey);
                }
            }
        }
        return allowedAppMap;
    }

    public ItemInfo getWorkspaceItemInfo(ComponentName componentName, UserHandleCompat user, int position) {
        if (user == null || componentName == null) {
            Log.d(TAG, "Null user or componentName in getWorkspaceItemInfo");
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(componentName);
        LauncherActivityInfoCompat lai = mLauncherApps.resolveActivity(intent, user);
        if (lai == null) {
            Log.d(TAG, "Missing activity found in getAppShortcutInfo: " + componentName);
            return null;
        }
        final ItemInfo info = new ItemInfo();
        mIconCache.getTitleAndIcon(info, componentName, lai, user, false, false);

        // fall back to the class name of the activity
        if (info.title == null) {
            info.title = componentName.getClassName();
        }

        info.user = user;
        info.contentDescription = mUserManager.getBadgedLabelForUser(info.title, info.user);
        return info;
    }

    /**
     * Runnable for the thread that loads the contents of the launcher:
     *   - workspace icons
     *   - all apps icons
     */
    private class LoaderTask implements Runnable {
        private Context mContext;
        private boolean mStopped;

        LoaderTask(Context context) {
            mContext = context;
        }

        public void run() {
            synchronized (mLock) {
                if (mStopped) {
                    return;
                }
            }

            //check if local has changed.
            mIsLocalChanged = mIconCache.hasLocalChanged(UserHandleCompat.myUserHandle());

            keep_running: {
                loadAndBindWorkspace();
                if (mStopped) {
                    break keep_running;
                }
                loadAndBindAllApps();
            }


            // Clear out this reference, otherwise we end up holding it until all of the
            // callback runnables are done.
            mContext = null;
            mIsLocalChanged = false;

            synchronized (mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask == this) {
                    mLoaderTask = null;
                }
                mHasLoaderCompletedOnce = true;
            }
        }

        public void stopLocked() {
            synchronized (LoaderTask.this) {
                mStopped = true;
                this.notify();
            }
        }

        /**
         * Gets the callbacks object.  If we've been stopped, or if the launcher object
         * has somehow been garbage collected, return null instead.  Pass in the Callbacks
         * object that was around when the deferred message was scheduled, and if there's
         * a new Callbacks object around then also return null.  This will save us from
         * calling onto it with data that will be ignored.
         */
        Callbacks tryGetCallbacks(Callbacks oldCallbacks) {
            synchronized (mLock) {
                if (mStopped) {
                    return null;
                }

                if (mCallbacks == null) {
                    return null;
                }

                final Callbacks callbacks = mCallbacks.get();
                if (callbacks != oldCallbacks) {
                    return null;
                }
                if (callbacks == null) {
                    Log.w(TAG, "no mCallbacks");
                    return null;
                }

                return callbacks;
            }
        }

        private void loadAndBindWorkspace() {
            //1. load workspace data
            sBgWorkspaceItems.clear();

            //get allowed app list
            HashMap<Integer, ComponentKey> allowedAppMap;
            if (sAppsForWorkspaceTest != null && sAppsForWorkspaceTest.size() > 0) {
                allowedAppMap = (HashMap<Integer, ComponentKey>) sAppsForWorkspaceTest.clone();
                sAppsForWorkspaceTest = null;
            } else {
                allowedAppMap = getAllowedAppMap(true);
            }
            for(int i =0;i<ITEM_COUNT; i++) {
                ComponentName componentName = null;
                UserHandleCompat user = null;
                if(allowedAppMap.containsKey(i)) {
                    componentName = allowedAppMap.get(i).componentName;
                    user = allowedAppMap.get(i).user;
                }
                ItemInfo itemInfo = getWorkspaceItemInfo(componentName, user, i);
                if (itemInfo != null && Utilities.isSafeMode(mContext)
                        && !Utilities.isSystemApp(mContext, itemInfo)) {
                    itemInfo = null;
                }
                sBgWorkspaceItems.add(itemInfo);
            }

            //2. bind workspace item
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher");
                return;
            }
            final ArrayList<ItemInfo> bgItemInfo = new ArrayList<>(sBgWorkspaceItems);

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindWorkspaceItems(bgItemInfo);
                    }
                }
            };
            runOnMainThread(r);
        }

        private void loadAndBindAllApps() {
            //1. load all apps data
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (loadAllApps)");
                return;
            }

            final List<UserHandleCompat> profiles = mUserManager.getUserProfiles();

            // Clear the list of apps
            mBgAllAppsList.clear();
            HashMap<Integer, ComponentKey> allowedApp=getAllowedAppMap(true);
            for (UserHandleCompat user : profiles) {
                // Query for the set of apps
                final List<LauncherActivityInfoCompat> apps = mLauncherApps.getActivityList(null, user);

                // Fail if we don't have any apps
                // TODO: Fix this. Only fail for the current user.
                if (apps == null || apps.isEmpty()) {
                    continue;
                }
                boolean quietMode = mUserManager.isQuietModeEnabled(user);
                // Create the ApplicationInfos
                for (int i = 0; i < apps.size(); i++) {
                    LauncherActivityInfoCompat app = apps.get(i);

                    boolean added=true;
                    Set<Integer> set=allowedApp.keySet();
                    for(int j=0;j<set.size();j++){
                        ComponentKey key=allowedApp.get(set.toArray()[j]);
                        if(app.getComponentName().getPackageName().equals(key.componentName.getPackageName())){
                            added=false;
                            break;
                        }
                    }
                    if(added){
                        // This builds the icon bitmaps.
                        mBgAllAppsList.add(new AppInfo(mContext, app, user, mIconCache, quietMode));
                    }


                }
            }
            mIconCache.updateDbIcons();

            //2. bind all apps items
            final ArrayList<AppInfo> list = new ArrayList<>(mBgAllAppsList.data);
            Runnable r = new Runnable() {
                public void run() {
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindAllApplications(list);
                    }
                }
            };
            boolean isRunningOnMainThread = !(sWorkerThread.getThreadId() == Process.myTid());
            if (isRunningOnMainThread) {
                r.run();
            } else {
                mHandler.post(r);
            }
        }
    }

    /**
     * Called when the icons for packages have been updated in the icon cache.
     */
    public void onPackageIconsUpdated(HashSet<String> updatedPackages, UserHandleCompat user) {
        final Callbacks callbacks = getCallback();
        final ArrayList<AppInfo> updatedApps = new ArrayList<>();
        final ArrayList<ItemInfo> updateItems = new ArrayList<>();

        // If any package icon has changed, update workspace app icons and allApps list
        synchronized (sBgLock) {
            for(ItemInfo itemInfo:sBgWorkspaceItems) {
                if(itemInfo != null) {
                    ComponentName cn = itemInfo.componentName;
                    if(cn != null && updatedPackages.contains(cn.getPackageName()) && user.equals(itemInfo.user)) {
                        itemInfo.updateIcon(mIconCache);
                    }
                }
                updateItems.add(itemInfo);
            }

            mBgAllAppsList.updateIconsAndLabels(updatedPackages, user, updatedApps);
        }

        if (!updatedApps.isEmpty()) {
            mHandler.post(new Runnable() {
                public void run() {
                    Callbacks cb = getCallback();
                    if (cb != null && callbacks == cb) {
                        cb.bindAppsUpdated(updatedApps);
                    }
                }
            });
        }

        if(!updateItems.isEmpty()) {
            mHandler.post(new Runnable() {
                public void run() {
                    Callbacks cb = getCallback();
                    if (cb != null && callbacks == cb) {
                        cb.bindWorkspaceItems(updateItems);
                    }
                }
            });
        }
    }

    void enqueuePackageUpdated(PackageUpdatedTask task) {
        sWorker.post(task);
    }

    @Thunk class AppsAvailabilityCheck extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (sBgLock) {
                final LauncherAppsCompat launcherApps = LauncherAppsCompat
                        .getInstance(mApp.getContext());
                final PackageManager manager = context.getPackageManager();
                final ArrayList<String> packagesRemoved = new ArrayList<String>();
                final ArrayList<String> packagesUnavailable = new ArrayList<String>();
                for (Entry<UserHandleCompat, HashSet<String>> entry : sPendingPackages.entrySet()) {
                    UserHandleCompat user = entry.getKey();
                    packagesRemoved.clear();
                    packagesUnavailable.clear();
                    for (String pkg : entry.getValue()) {
                        if (!launcherApps.isPackageEnabledForProfile(pkg, user)) {
                            if (PackageManagerHelper.isAppOnSdcard(manager, pkg)) {
                                packagesUnavailable.add(pkg);
                            } else {
                                packagesRemoved.add(pkg);
                            }
                        }
                    }
                    if (!packagesRemoved.isEmpty()) {
                        enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_REMOVE,
                                packagesRemoved.toArray(new String[packagesRemoved.size()]), user));
                    }
                    if (!packagesUnavailable.isEmpty()) {
                        enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_UNAVAILABLE,
                                packagesUnavailable.toArray(new String[packagesUnavailable.size()]), user));
                    }
                }
                sPendingPackages.clear();
            }
        }
    }

    private class PackageUpdatedTask implements Runnable {
        int mOp;
        String[] mPackages;
        UserHandleCompat mUser;

        public static final int OP_ADD = 1;
        public static final int OP_UPDATE = 2;
        public static final int OP_REMOVE = 3; // uninstlled
        public static final int OP_UNAVAILABLE = 4; // external media unmounted
        public static final int OP_SUSPEND = 5; // package suspended
        public static final int OP_UNSUSPEND = 6; // package unsuspended
        public static final int OP_USER_AVAILABILITY_CHANGE = 7; // user available/unavailable

        public PackageUpdatedTask(int op, String[] packages, UserHandleCompat user) {
            mOp = op;
            mPackages = packages;
            mUser = user;
        }

        public void run() {
            if (!mHasLoaderCompletedOnce) {
                // Loader has not yet run.
                return;
            }
            final Context context = mApp.getContext();

            final String[] packages = mPackages;
            final int N = packages.length;
            StringFilter pkgFilter = StringFilter.of(new HashSet<>(Arrays.asList(packages)));
            boolean isDisabled;

            switch (mOp) {
                case OP_ADD: {
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]+" mUser:"+mUser.getUser());
                        mIconCache.updateIconsForPkg(packages[i], mUser);
                        mBgAllAppsList.addPackage(context, packages[i], mUser);
                    }
                    break;
                }
                case OP_UPDATE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                        mIconCache.updateIconsForPkg(packages[i], mUser);
                        mBgAllAppsList.updatePackage(context, packages[i], mUser);
                    }
                    break;
                case OP_REMOVE: {
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                        mIconCache.removeIconsForPkg(packages[i], mUser);
                    }
                    // Fall through
                }
                case OP_UNAVAILABLE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                        mBgAllAppsList.removePackage(packages[i], mUser);
                    }
                    break;
                case OP_SUSPEND:
                case OP_UNSUSPEND:
                    isDisabled = mOp == OP_SUSPEND;
                    if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.(un)suspend " + N);
                    mBgAllAppsList.updatePackageFlags(pkgFilter, mUser, isDisabled);
                    break;
                case OP_USER_AVAILABILITY_CHANGE:
                    isDisabled = UserManagerCompat.getInstance(context).isQuietModeEnabled(mUser);
                    // We want to update all packages for this user.
                    pkgFilter = StringFilter.matchesAll();
                    mBgAllAppsList.updatePackageFlags(pkgFilter, mUser, isDisabled);
                    break;
            }

            ArrayList<AppInfo> added = null;
            ArrayList<AppInfo> modified = null;
            final ArrayList<AppInfo> removedApps = new ArrayList<AppInfo>();

            if (mBgAllAppsList.added.size() > 0) {
                added = new ArrayList<>(mBgAllAppsList.added);
                mBgAllAppsList.added.clear();
            }
            if (mBgAllAppsList.modified.size() > 0) {
                modified = new ArrayList<>(mBgAllAppsList.modified);
                mBgAllAppsList.modified.clear();
            }
            if (mBgAllAppsList.removed.size() > 0) {
                removedApps.addAll(mBgAllAppsList.removed);
                mBgAllAppsList.removed.clear();
            }

            if (added != null) {
                addAppsToAllApps(context, added);
            }

            if (modified != null) {
                final Callbacks callbacks = getCallback();
                final ArrayList<AppInfo> modifiedFinal = modified;
                //when add apps, only update all apps, ignore workspace
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsUpdated(modifiedFinal);
                        }
                    }
                });
            }

            //remove
            final HashSet<String> removedPackages = new HashSet<>();
            final HashSet<ComponentName> removedComponents = new HashSet<>();
            if (mOp == OP_REMOVE) {
                // Mark all packages in the broadcast to be removed
                Collections.addAll(removedPackages, packages);

                // No need to update the removedComponents as
                // removedPackages is a super-set of removedComponents
            } else if (mOp == OP_UPDATE) {
                // Mark disabled packages in the broadcast to be removed
                for (int i=0; i<N; i++) {
                    if (isPackageDisabled(context, packages[i], mUser)) {
                        removedPackages.add(packages[i]);
                    }
                }

                // Update removedComponents as some components can get removed during package update
                for (AppInfo info : removedApps) {
                    removedComponents.add(info.componentName);
                }
            }
            Log.d(TAG, "bindWorkspaceItemsRemoved removedPackages.size: "+removedPackages.size());
            //Remove apps from Workspace
            if(!removedPackages.isEmpty()) {
                final HashMap<Integer, ComponentKey> removeData = new HashMap<>();
                HashMap<Integer, ComponentKey> allowedAppMap = getAllowedAppMap(false);
                for(String pkgName:removedPackages) {
                    for(Map.Entry<Integer, ComponentKey> entry : allowedAppMap.entrySet()) {
                        ComponentKey componentKey = entry.getValue();
                        if(componentKey.componentName.getPackageName().equals(pkgName)
                                && componentKey.user.equals(mUser)) {
                            int position = entry.getKey();
                            removeData.put(position, componentKey);

                            //update the bg workspace data list
                            updateWorkspaceBgDataList(position, null);
                        }
                    }
                }

                final Callbacks callbacks = getCallback();
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindWorkspaceItemsRemoved(removeData);
                        }
                    }
                });
            }

            if(!removedComponents.isEmpty()) {
                final HashMap<Integer, ComponentKey> removeData = new HashMap<>();
                HashMap<Integer, ComponentKey> allowedAppMap = getAllowedAppMap(false);
                for(ComponentName cName:removedComponents) {
                    for(Map.Entry<Integer, ComponentKey> entry : allowedAppMap.entrySet()) {
                        ComponentKey componentKey = entry.getValue();
                        if(componentKey.componentName.equals(cName)
                                && componentKey.user.equals(mUser)) {
                            int position = entry.getKey();
                            removeData.put(position, componentKey);

                            //update the bg workspace data list
                            updateWorkspaceBgDataList(position, null);
                        }
                    }
                }

                final Callbacks callbacks = getCallback();
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindWorkspaceItemsRemoved(removeData);
                        }
                    }
                });
            }

            // Remove corresponding apps from All-Apps
            if (!removedApps.isEmpty()) {
                final Callbacks callbacks = getCallback();
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppInfosRemoved(removedApps);
                        }
                    }
                });
            }
        }
    }

    public void stopUpdated() {
        mHasLoaderCompletedOnce =false;
    }

    static void updateWorkspaceBgDataList(int position, AppInfo appInfo) {
        synchronized (sBgLock) {
            sBgWorkspaceItems.remove(position);
            ItemInfo newItemInfo = null;
            if(appInfo != null) {
                newItemInfo = appInfo.toItemInfo();
            }
            sBgWorkspaceItems.add(position, newItemInfo);
        }
    }

    public static boolean isValidPackageActivity(Context context, ComponentName cn,
                                                 UserHandleCompat user) {
        if (cn == null) {
            return false;
        }
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        if (!launcherApps.isPackageEnabledForProfile(cn.getPackageName(), user)) {
            return false;
        }
        return launcherApps.isActivityEnabledForProfile(cn, user)
                || (context.getPackageManager().getComponentEnabledSetting(cn)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    public static boolean isValidPackage(Context context, String packageName,
                                         UserHandleCompat user) {
        if (packageName == null) {
            return false;
        }
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        return launcherApps.isPackageEnabledForProfile(packageName, user);
    }

    @Thunk static boolean isPackageDisabled(Context context, String packageName,
            UserHandleCompat user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        return !launcherApps.isPackageEnabledForProfile(packageName, user);
    }

    public Callbacks getCallback() {
        return mCallbacks != null ? mCallbacks.get() : null;
    }

    /**
     * @return the looper for the worker thread which can be used to start background tasks.
     */
    public static Looper getWorkerLooper() {
        return sWorkerThread.getLooper();
    }

    @VisibleForTesting
    public static void setDefaultWorkspaceItems(HashMap<Integer, ComponentKey> apps) {
        sAppsForWorkspaceTest = apps;
    }
}
