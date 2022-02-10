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

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.sprd.powersavemodelauncher.graphics.LauncherIcons;

import com.sprd.powersavemodelauncher.compat.LauncherActivityInfoCompat;
import com.sprd.powersavemodelauncher.compat.LauncherAppsCompat;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;
import com.sprd.powersavemodelauncher.compat.UserManagerCompat;
import com.sprd.powersavemodelauncher.util.ComponentKey;
import com.sprd.powersavemodelauncher.util.SQLiteCacheHelper;
import com.sprd.powersavemodelauncher.util.Thunk;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {

    private static final String TAG = "PowerSaveLauncher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    // Empty class name is used for storing package default entry.
    private static final String EMPTY_CLASS_NAME = ".";

    private static final boolean DEBUG = false;

    private static final int LOW_RES_SCALE_FACTOR = 5;

    @Thunk static final Object ICON_UPDATE_TOKEN = new Object();

    @Thunk static class CacheEntry {
        public Bitmap icon;
        public CharSequence title = "";
        public CharSequence contentDescription = "";
        public boolean isLowResIcon;
    }

    private final HashMap<UserHandleCompat, Bitmap> mDefaultIcons = new HashMap<>();
    @Thunk final MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();

    private final Context mContext;
    private final PackageManager mPackageManager;
    @Thunk final UserManagerCompat mUserManager;
    private final LauncherAppsCompat mLauncherApps;
    private final HashMap<ComponentKey, CacheEntry> mCache =
            new HashMap<ComponentKey, CacheEntry>(INITIAL_ICON_CACHE_CAPACITY);
    private final int mIconDpi;
    @Thunk final IconDB mIconDb;

    @Thunk final Handler mWorkerHandler;

    // The background color used for activity icons. Since these icons are displayed in all-apps
    // , this would be same as the light quantum panel background. This color
    // is used to convert icons to RGB_565.
    private final int mActivityBgColor;
    // The background color used for package icons. These are displayed in widget tray, which
    // has a dark quantum panel background.
    private final int mPackageBgColor;
    private final BitmapFactory.Options mLowResOptions;

    private String mSystemState;
    private Bitmap mLowResBitmap;
    private Canvas mLowResCanvas;
    private Paint mLowResPaint;

    public IconCache(Context context, InvariantDeviceProfile inv) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mUserManager = UserManagerCompat.getInstance(mContext);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mIconDpi = inv.fillResIconDpi;
        mIconDb = new IconDB(context, inv.iconBitmapSize);

        mWorkerHandler = new Handler(LauncherModel.getWorkerLooper());

        mActivityBgColor = context.getResources().getColor(R.color.quantum_panel_bg_color);
        mPackageBgColor = context.getResources().getColor(R.color.quantum_panel_bg_color_dark);
        mLowResOptions = new BitmapFactory.Options();
        // Always prefer RGB_565 config for low res. If the bitmap has transparency, it will
        // automatically be loaded as ALPHA_8888.
        mLowResOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        updateSystemStateString();
    }

    private Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(), android.R.mipmap.sym_def_app_icon);
    }

    private Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        return (d != null) ? d : getFullResDefaultActivityIcon();
    }

    private Bitmap makeDefaultIcon(UserHandleCompat user) {
        Drawable unbadged = getFullResDefaultActivityIcon();
        return LauncherIcons.createBadgedIconBitmap(unbadged, user, mContext, Build.VERSION_CODES.O);
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public synchronized void remove(ComponentName componentName, UserHandleCompat user) {
        mCache.remove(new ComponentKey(componentName, user));
    }

    /**
     * Remove any records for the supplied package name from memory.
     */
    private void removeFromMemCacheLocked(String packageName, UserHandleCompat user) {
        HashSet<ComponentKey> forDeletion = new HashSet<ComponentKey>();
        for (ComponentKey key: mCache.keySet()) {
            if (key.componentName.getPackageName().equals(packageName)
                    && key.user.equals(user)) {
                forDeletion.add(key);
            }
        }
        for (ComponentKey condemned: forDeletion) {
            mCache.remove(condemned);
        }
    }

    /**
     * Updates the entries related to the given package in memory and persistent DB.
     */
    public synchronized void updateIconsForPkg(String packageName, UserHandleCompat user) {
        removeIconsForPkg(packageName, user);
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            long userSerial = mUserManager.getSerialNumberForUser(user);
            for (LauncherActivityInfoCompat app : mLauncherApps.getActivityList(packageName, user)) {
                addIconToDBAndMemCache(app, info, userSerial);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
            return;
        }
    }

    /**
     * Removes the entries related to the given package in memory and persistent DB.
     */
    public synchronized void removeIconsForPkg(String packageName, UserHandleCompat user) {
        removeFromMemCacheLocked(packageName, user);
        long userSerial = mUserManager.getSerialNumberForUser(user);
        mIconDb.delete(
                IconDB.COLUMN_COMPONENT + " LIKE ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[]{packageName + "/%", Long.toString(userSerial)});
    }

    public void updateDbIcons() {
        // Remove all active icon update tasks.
        mWorkerHandler.removeCallbacksAndMessages(ICON_UPDATE_TOKEN);

        updateSystemStateString();

        UserHandleCompat user = UserHandleCompat.myUserHandle();
        // Query for the set of apps
        final List<LauncherActivityInfoCompat> apps = mLauncherApps.getActivityList(null, user);
        // Fail if we don't have any apps
        // TODO: Fix this. Only fail for the current user.
        if (apps == null || apps.isEmpty()) {
            return;
        }

        // Update icon cache.
        updateDBIcons(user, apps, Collections.<String>emptySet());
    }

    /**
     * Updates the persistent DB, such that only entries corresponding to {@param apps} remain in
     * the DB and are updated.
     * @return The set of packages for which icons have updated.
     */
    private void updateDBIcons(UserHandleCompat user, List<LauncherActivityInfoCompat> apps,
            Set<String> ignorePackages) {
        long userSerial = mUserManager.getSerialNumberForUser(user);
        PackageManager pm = mContext.getPackageManager();
        HashMap<String, PackageInfo> pkgInfoMap = new HashMap<String, PackageInfo>();
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)) {
            pkgInfoMap.put(info.packageName, info);
        }

        HashMap<ComponentName, LauncherActivityInfoCompat> componentMap = new HashMap<>();
        for (LauncherActivityInfoCompat app : apps) {
            componentMap.put(app.getComponentName(), app);
        }

        HashSet<Integer> itemsToRemove = new HashSet<Integer>();
        Stack<LauncherActivityInfoCompat> appsToUpdate = new Stack<>();

        Cursor c = null;
        try {
            c = mIconDb.query(
                    new String[]{IconDB.COLUMN_ROWID, IconDB.COLUMN_COMPONENT,
                            IconDB.COLUMN_LAST_UPDATED, IconDB.COLUMN_VERSION,
                            IconDB.COLUMN_SYSTEM_STATE},
                    IconDB.COLUMN_USER + " = ? ",
                    new String[]{Long.toString(userSerial)});

            final int indexComponent = c.getColumnIndex(IconDB.COLUMN_COMPONENT);
            final int indexLastUpdate = c.getColumnIndex(IconDB.COLUMN_LAST_UPDATED);
            final int indexVersion = c.getColumnIndex(IconDB.COLUMN_VERSION);
            final int rowIndex = c.getColumnIndex(IconDB.COLUMN_ROWID);
            final int systemStateIndex = c.getColumnIndex(IconDB.COLUMN_SYSTEM_STATE);

            while (c.moveToNext()) {
                String cn = c.getString(indexComponent);
                ComponentName component = ComponentName.unflattenFromString(cn);
                PackageInfo info = pkgInfoMap.get(component.getPackageName());
                if (info == null) {
                    if (!ignorePackages.contains(component.getPackageName())) {
                        remove(component, user);
                        itemsToRemove.add(c.getInt(rowIndex));
                    }
                    continue;
                }
                if ((info.applicationInfo.flags & ApplicationInfo.FLAG_IS_DATA_ONLY) != 0) {
                    // Application is not present
                    continue;
                }

                long updateTime = c.getLong(indexLastUpdate);
                int version = c.getInt(indexVersion);
                LauncherActivityInfoCompat app = componentMap.remove(component);
                if (version == info.versionCode && updateTime == info.lastUpdateTime &&
                        TextUtils.equals(mSystemState, c.getString(systemStateIndex))) {
                    continue;
                }
                if (app == null) {
                    remove(component, user);
                    itemsToRemove.add(c.getInt(rowIndex));
                } else {
                    appsToUpdate.add(app);
                }
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
            // Continue updating whatever we have read so far
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (!itemsToRemove.isEmpty()) {
            mIconDb.delete(
                    Utilities.createDbSelectionQuery(IconDB.COLUMN_ROWID, itemsToRemove), null);
        }

        // Insert remaining apps.
        if (!componentMap.isEmpty() || !appsToUpdate.isEmpty()) {
            Stack<LauncherActivityInfoCompat> appsToAdd = new Stack<>();
            appsToAdd.addAll(componentMap.values());
            new SerializedIconUpdateTask(userSerial, pkgInfoMap,
                    appsToAdd, appsToUpdate).scheduleNext();
        }
    }

    @Thunk void addIconToDBAndMemCache(LauncherActivityInfoCompat app, PackageInfo info,
            long userSerial) {
        // Reuse the existing entry if it already exists in the DB. This ensures that we do not
        // create bitmap if it was already created during loader.
        ContentValues values = updateCacheAndGetContentValues(app, false);
        addIconToDB(values, app.getComponentName(), info, userSerial);
    }

    /**
     * Updates {@param values} to contain versoning information and adds it to the DB.
     * @param values {@link ContentValues} containing icon & title
     */
    private void addIconToDB(ContentValues values, ComponentName key,
            PackageInfo info, long userSerial) {
        values.put(IconDB.COLUMN_COMPONENT, key.flattenToString());
        values.put(IconDB.COLUMN_USER, userSerial);
        values.put(IconDB.COLUMN_LAST_UPDATED, info.lastUpdateTime);
        values.put(IconDB.COLUMN_VERSION, info.versionCode);
        mIconDb.insertOrReplace(values);
    }

    @Thunk ContentValues updateCacheAndGetContentValues(LauncherActivityInfoCompat app,
            boolean replaceExisting) {
        final ComponentKey key = new ComponentKey(app.getComponentName(), app.getUser());
        CacheEntry entry = null;
        if (!replaceExisting) {
            entry = mCache.get(key);
            // We can't reuse the entry if the high-res icon is not present.
            if (entry == null || entry.isLowResIcon || entry.icon == null) {
                entry = null;
            }
        }
        if (entry == null) {
            entry = new CacheEntry();
            entry.icon = LauncherIcons.createBadgedIconBitmap(
                    app.getIcon(mIconDpi), app.getUser(), mContext,app.getApplicationInfo().targetSdkVersion);
        }
        entry.title = app.getLabel();
        entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, app.getUser());
        mCache.put(new ComponentKey(app.getComponentName(), app.getUser()), entry);

        return newContentValues(entry.icon, entry.title.toString(), mActivityBgColor);
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     * @return a request ID that can be used to cancel the request.
     */
    public IconLoadRequest updateIconInBackground(final BubbleTextView caller, final ItemInfo info) {
        Runnable request = new Runnable() {

            @Override
            public void run() {
                if (info instanceof AppInfo) {
                    getTitleAndIcon((AppInfo) info, null, false);
                }
                mMainThreadExecutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        caller.reapplyItemInfo(info);
                    }
                });
            }
        };
        mWorkerHandler.post(request);
        return new IconLoadRequest(request, mWorkerHandler);
    }

    private Bitmap getNonNullIcon(CacheEntry entry, UserHandleCompat user) {
        return entry.icon == null ? getDefaultIcon(user) : entry.icon;
    }

    public synchronized void getTitleAndIcon(ItemInfo itemInfo, ComponentName component, UserHandleCompat user) {
        if (component == null) {
            itemInfo.iconBitmap = getDefaultIcon(user);
            itemInfo.title = "";
            itemInfo.usingLowResIcon = false;
        } else {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(component);
            LauncherActivityInfoCompat info = mLauncherApps.resolveActivity(intent, user);
            if (info == null) {
                Log.d(TAG, "Missing activity found in getAppShortcutInfo ");
                return;
            }
            getTitleAndIcon(itemInfo, component, info, user, false, false);
        }
    }

    public synchronized void getTitleAndIcon(
            ItemInfo itemInfo, ComponentName component, LauncherActivityInfoCompat info,
            UserHandleCompat user, boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(component, info, user, usePkgIcon, useLowResIcon);
        itemInfo.iconBitmap = getNonNullIcon(entry, user);
        itemInfo.title = Utilities.trim(entry.title);
        itemInfo.componentName = component;
        itemInfo.usingLowResIcon = entry.isLowResIcon;
    }
    /**
     * Fill in "application" with the icon and label for "info."
     */
    public synchronized void getTitleAndIcon(AppInfo application,
            LauncherActivityInfoCompat info, boolean useLowResIcon) {
        UserHandleCompat user = info == null ? application.user : info.getUser();
        CacheEntry entry = cacheLocked(application.componentName, info, user,
                false, useLowResIcon);
        application.title = Utilities.trim(entry.title);
        application.iconBitmap = getNonNullIcon(entry, user);
        application.contentDescription = entry.contentDescription;
        application.usingLowResIcon = entry.isLowResIcon;
    }

    /**
     * Updates {@param application} only if a valid entry is found.
     */
    public synchronized void updateTitleAndIcon(AppInfo application) {
        CacheEntry entry = cacheLocked(application.componentName, null, application.user,
                false, application.usingLowResIcon);
        if (entry.icon != null && !isDefaultIcon(entry.icon, application.user)) {
            application.title = Utilities.trim(entry.title);
            application.iconBitmap = entry.icon;
            application.contentDescription = entry.contentDescription;
            application.usingLowResIcon = entry.isLowResIcon;
        }
    }

    public synchronized Bitmap getDefaultIcon(UserHandleCompat user) {
        if (!mDefaultIcons.containsKey(user)) {
            mDefaultIcons.put(user, makeDefaultIcon(user));
        }
        return mDefaultIcons.get(user);
    }

    public boolean isDefaultIcon(Bitmap icon, UserHandleCompat user) {
        return mDefaultIcons.get(user) == icon;
    }

    /**
     * Retrieves the entry from the cache. If the entry is not present, it creates a new entry.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry cacheLocked(ComponentName componentName, LauncherActivityInfoCompat info,
            UserHandleCompat user, boolean usePackageIcon, boolean useLowResIcon) {
        ComponentKey cacheKey = new ComponentKey(componentName, user);
        CacheEntry entry = mCache.get(cacheKey);
        if (entry == null || (entry.isLowResIcon && !useLowResIcon)) {
            entry = new CacheEntry();
            mCache.put(cacheKey, entry);

            // Check the DB first.
            if (!getEntryFromDB(cacheKey, entry, useLowResIcon)) {
                if (info != null) {
                    entry.icon = LauncherIcons.createBadgedIconBitmap(
                            info.getIcon(mIconDpi), info.getUser(), mContext,info.getApplicationInfo().targetSdkVersion);
                } else {
                    if (usePackageIcon) {
                        CacheEntry packageEntry = getEntryForPackageLocked(
                                componentName.getPackageName(), user, false);
                        if (packageEntry != null) {
                            if (DEBUG) Log.d(TAG, "using package default icon for " +
                                    componentName.toShortString());
                            entry.icon = packageEntry.icon;
                            entry.title = packageEntry.title;
                            entry.contentDescription = packageEntry.contentDescription;
                        }
                    }
                    if (entry.icon == null) {
                        if (DEBUG) Log.d(TAG, "using default icon for " +
                                componentName.toShortString());
                        entry.icon = getDefaultIcon(user);
                    }
                }
            }

            if ((TextUtils.isEmpty(entry.title) || LauncherModel.mIsLocalChanged) && info != null){
                entry.title = info.getLabel();
                entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
            }
        }
        return entry;
    }

    /**
     * Adds a default package entry in the cache. This entry is not persisted and will be removed
     * when the cache is flushed.
     */
    public synchronized void cachePackageInstallInfo(String packageName, UserHandleCompat user,
            Bitmap icon, CharSequence title) {
        removeFromMemCacheLocked(packageName, user);

        ComponentKey cacheKey = getPackageKey(packageName, user);
        CacheEntry entry = mCache.get(cacheKey);

        // For icon caching, do not go through DB. Just update the in-memory entry.
        if (entry == null) {
            entry = new CacheEntry();
            mCache.put(cacheKey, entry);
        }
        if (!TextUtils.isEmpty(title)) {
            entry.title = title;
        }
        if (icon != null) {
            entry.icon = LauncherIcons.createIconBitmap(icon, mContext);
        }
    }

    private static ComponentKey getPackageKey(String packageName, UserHandleCompat user) {
        ComponentName cn = new ComponentName(packageName, packageName + EMPTY_CLASS_NAME);
        return new ComponentKey(cn, user);
    }

    /**
     * Gets an entry for the package, which can be used as a fallback entry for various components.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry getEntryForPackageLocked(String packageName, UserHandleCompat user,
            boolean useLowResIcon) {
        ComponentKey cacheKey = getPackageKey(packageName, user);
        CacheEntry entry = mCache.get(cacheKey);

        if (entry == null || (entry.isLowResIcon && !useLowResIcon)) {
            entry = new CacheEntry();
            boolean entryUpdated = true;

            // Check the DB first.
            if (!getEntryFromDB(cacheKey, entry, useLowResIcon)) {
                try {
                    int flags = UserHandleCompat.myUserHandle().equals(user) ? 0 :
                        PackageManager.GET_UNINSTALLED_PACKAGES;
                    PackageInfo info = mPackageManager.getPackageInfo(packageName, flags);
                    ApplicationInfo appInfo = info.applicationInfo;
                    if (appInfo == null) {
                        throw new NameNotFoundException("ApplicationInfo is null");
                    }
                    entry.icon = LauncherIcons.createBadgedIconBitmap(
                            appInfo.loadIcon(mPackageManager), user, mContext,appInfo.targetSdkVersion);
                    entry.title = appInfo.loadLabel(mPackageManager);
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
                    entry.isLowResIcon = false;

                    // Add the icon in the DB here, since these do not get written during
                    // package updates.
                    ContentValues values =
                            newContentValues(entry.icon, entry.title.toString(), mPackageBgColor);
                    addIconToDB(values, cacheKey.componentName, info,
                            mUserManager.getSerialNumberForUser(user));

                } catch (NameNotFoundException e) {
                    if (DEBUG) Log.d(TAG, "Application not installed " + packageName);
                    entryUpdated = false;
                }
            }

            // Only add a filled-out entry to the cache
            if (entryUpdated) {
                mCache.put(cacheKey, entry);
            }
        }
        return entry;
    }

    public boolean hasLocalChanged(UserHandleCompat user) {
        updateSystemStateString();
        long userSerial = mUserManager.getSerialNumberForUser(user);
        Cursor c = null;
        try {
            c = mIconDb.query(
                    new String[]{IconDB.COLUMN_SYSTEM_STATE},
                    IconDB.COLUMN_USER + " = ? ",
                    new String[]{Long.toString(userSerial)});

            final int systemStateIndex = c.getColumnIndex(IconDB.COLUMN_SYSTEM_STATE);
            if (c.moveToFirst()) {
                return !TextUtils.equals(mSystemState, c.getString(systemStateIndex));
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return false;
    }

    private boolean getEntryFromDB(ComponentKey cacheKey, CacheEntry entry, boolean lowRes) {
        Cursor c = null;
        try {
            c = mIconDb.query(
                new String[]{lowRes ? IconDB.COLUMN_ICON_LOW_RES : IconDB.COLUMN_ICON,
                        IconDB.COLUMN_LABEL},
                IconDB.COLUMN_COMPONENT + " = ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[]{cacheKey.componentName.flattenToString(),
                        Long.toString(mUserManager.getSerialNumberForUser(cacheKey.user))});
            if (c.moveToNext()) {
                entry.icon = loadIconNoResize(c, 0, lowRes ? mLowResOptions : null);
                entry.isLowResIcon = lowRes;
                entry.title = c.getString(1);
                if (entry.title == null) {
                    entry.title = "";
                    entry.contentDescription = "";
                } else {
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(
                            entry.title, cacheKey.user);
                }
                return true;
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return false;
    }

    public static class IconLoadRequest {
        private final Runnable mRunnable;
        private final Handler mHandler;

        IconLoadRequest(Runnable runnable, Handler handler) {
            mRunnable = runnable;
            mHandler = handler;
        }

        public void cancel() {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    /**
     * A runnable that updates invalid icons and adds missing icons in the DB for the provided
     * LauncherActivityInfoCompat list. Items are updated/added one at a time, so that the
     * worker thread doesn't get blocked.
     */
    @Thunk class SerializedIconUpdateTask implements Runnable {
        private final long mUserSerial;
        private final HashMap<String, PackageInfo> mPkgInfoMap;
        private final Stack<LauncherActivityInfoCompat> mAppsToAdd;
        private final Stack<LauncherActivityInfoCompat> mAppsToUpdate;
        private final HashSet<String> mUpdatedPackages = new HashSet<String>();

        @Thunk SerializedIconUpdateTask(long userSerial, HashMap<String, PackageInfo> pkgInfoMap,
                Stack<LauncherActivityInfoCompat> appsToAdd,
                Stack<LauncherActivityInfoCompat> appsToUpdate) {
            mUserSerial = userSerial;
            mPkgInfoMap = pkgInfoMap;
            mAppsToAdd = appsToAdd;
            mAppsToUpdate = appsToUpdate;
        }

        @Override
        public void run() {
            if (!mAppsToUpdate.isEmpty()) {
                LauncherActivityInfoCompat app = mAppsToUpdate.pop();
                String cn = app.getComponentName().flattenToString();
                ContentValues values = updateCacheAndGetContentValues(app, true);
                mIconDb.update(values,
                        IconDB.COLUMN_COMPONENT + " = ? AND " + IconDB.COLUMN_USER + " = ?",
                        new String[]{cn, Long.toString(mUserSerial)});
                mUpdatedPackages.add(app.getComponentName().getPackageName());

                if (mAppsToUpdate.isEmpty() && !mUpdatedPackages.isEmpty()) {
                    // No more app to update. Notify model.
                    LauncherAppState.getInstance(mContext).getModel().onPackageIconsUpdated(
                            mUpdatedPackages, mUserManager.getUserForSerialNumber(mUserSerial));
                }

                // Let it run one more time.
                scheduleNext();
            } else if (!mAppsToAdd.isEmpty()) {
                LauncherActivityInfoCompat app = mAppsToAdd.pop();
                PackageInfo info = mPkgInfoMap.get(app.getComponentName().getPackageName());
                if (info != null) {
                    synchronized (IconCache.this) {
                        addIconToDBAndMemCache(app, info, mUserSerial);
                    }
                }

                if (!mAppsToAdd.isEmpty()) {
                    scheduleNext();
                }
            }
        }

        public void scheduleNext() {
            mWorkerHandler.postAtTime(this, ICON_UPDATE_TOKEN, SystemClock.uptimeMillis() + 1);
        }
    }

    private void updateSystemStateString() {
        //mSystemState = Locale.getDefault().toString();
        mSystemState = mContext.getResources().getConfiguration().getLocales().toString();
    }

    static final class IconDB extends SQLiteCacheHelper {
        private final static int DB_VERSION = 7;

        private final static int RELEASE_VERSION = DB_VERSION;

        static final String APP_ICONS_DB = "app_icons.db";

        private final static String TABLE_NAME = "icons";
        private final static String COLUMN_ROWID = "rowid";
        private final static String COLUMN_COMPONENT = "componentName";
        private final static String COLUMN_USER = "profileId";
        private final static String COLUMN_LAST_UPDATED = "lastUpdated";
        private final static String COLUMN_VERSION = "version";
        private final static String COLUMN_ICON = "icon";
        private final static String COLUMN_ICON_LOW_RES = "icon_low_res";
        private final static String COLUMN_LABEL = "label";
        private final static String COLUMN_SYSTEM_STATE = "system_state";

        public IconDB(Context context, int iconPixelSize) {
            super(context, APP_ICONS_DB,
                    (RELEASE_VERSION << 16) + iconPixelSize,
                    TABLE_NAME);
        }

        @Override
        protected void onCreateTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_COMPONENT + " TEXT NOT NULL, " +
                    COLUMN_USER + " INTEGER NOT NULL, " +
                    COLUMN_LAST_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_VERSION + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ICON + " BLOB, " +
                    COLUMN_ICON_LOW_RES + " BLOB, " +
                    COLUMN_LABEL + " TEXT, " +
                    COLUMN_SYSTEM_STATE + " TEXT, " +
                    "PRIMARY KEY (" + COLUMN_COMPONENT + ", " + COLUMN_USER + ") " +
                    ");");
        }
    }

    private ContentValues newContentValues(Bitmap icon, String label, int lowResBackgroundColor) {
        ContentValues values = new ContentValues();
        values.put(IconDB.COLUMN_ICON, Utilities.flattenBitmap(icon));

        values.put(IconDB.COLUMN_LABEL, label);
        values.put(IconDB.COLUMN_SYSTEM_STATE, mSystemState);

        if (lowResBackgroundColor == Color.TRANSPARENT) {
          values.put(IconDB.COLUMN_ICON_LOW_RES, Utilities.flattenBitmap(
          Bitmap.createScaledBitmap(icon,
                  icon.getWidth() / LOW_RES_SCALE_FACTOR,
                  icon.getHeight() / LOW_RES_SCALE_FACTOR, true)));
        } else {
            synchronized (this) {
                if (mLowResBitmap == null) {
                    mLowResBitmap = Bitmap.createBitmap(icon.getWidth() / LOW_RES_SCALE_FACTOR,
                            icon.getHeight() / LOW_RES_SCALE_FACTOR, Bitmap.Config.RGB_565);
                    mLowResCanvas = new Canvas(mLowResBitmap);
                    mLowResPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                }
                mLowResCanvas.drawColor(lowResBackgroundColor);
                mLowResCanvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                        new Rect(0, 0, mLowResBitmap.getWidth(), mLowResBitmap.getHeight()),
                        mLowResPaint);
                values.put(IconDB.COLUMN_ICON_LOW_RES, Utilities.flattenBitmap(mLowResBitmap));
            }
        }
        return values;
    }

    private static Bitmap loadIconNoResize(Cursor c, int iconIndex, BitmapFactory.Options options) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception e) {
            return null;
        }
    }
}
