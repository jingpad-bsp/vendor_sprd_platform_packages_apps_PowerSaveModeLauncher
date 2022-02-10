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

import android.content.Context;
import android.content.Intent;

import com.sprd.powersavemodelauncher.compat.LauncherActivityInfoCompat;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;
import com.sprd.powersavemodelauncher.compat.UserManagerCompat;
import com.sprd.powersavemodelauncher.util.ComponentKey;
import com.sprd.powersavemodelauncher.util.PackageManagerHelper;

/**
 * Represents an app in AllAppsView.
 */
public class AppInfo extends ItemInfo {
    private static final String TAG = "AppInfo";
    static final int DOWNLOADED_FLAG = 1;
    static final int UPDATED_SYSTEM_APP_FLAG = 2;
    /**
     * Indicates that the icon is disabled as the app is suspended
     */
    static final int FLAG_DISABLED_SUSPENDED = 4;

    /**
     * Indicates that the icon is disabled as the user is in quiet mode.
     */
    static final int FLAG_DISABLED_QUIET_USER = 8;
    /**
     * The intent used to start the application.
     */
    public Intent intent;

    int flags = 0;

    public static final int DEFAULT = 0;
    int isDisabled = DEFAULT;

    AppInfo() {}

    public Intent getIntent() {
        return intent;
    }

    /**
     * Must not hold the Context.
     */
    public AppInfo(Context context, LauncherActivityInfoCompat info, UserHandleCompat user,
                   IconCache iconCache) {
        this(context, info, user, iconCache,
                UserManagerCompat.getInstance(context).isQuietModeEnabled(user));
    }

    public AppInfo(Context context, LauncherActivityInfoCompat info, UserHandleCompat user,
            IconCache iconCache, boolean quietModeEnabled) {
        this.componentName = info.getComponentName();
        flags = initFlags(info);
        if (PackageManagerHelper.isAppSuspended(info.getApplicationInfo())) {
            isDisabled |= FLAG_DISABLED_SUSPENDED;
        }
        if (quietModeEnabled) {
            isDisabled |= FLAG_DISABLED_QUIET_USER;
        }

        iconCache.getTitleAndIcon(this, info, true /* useLowResIcon */);
        intent = makeLaunchIntent(context, info, user);
        this.user = user;
    }

    public static int initFlags(LauncherActivityInfoCompat info) {
        int appFlags = info.getApplicationInfo().flags;
        int flags = 0;
        if ((appFlags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
            flags |= DOWNLOADED_FLAG;

            if ((appFlags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                flags |= UPDATED_SYSTEM_APP_FLAG;
            }
        }
        return flags;
    }

    public AppInfo(AppInfo info) {
        super(info);
        componentName = info.componentName;
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
        flags = info.flags;
        isDisabled = info.isDisabled;
        iconBitmap = info.iconBitmap;
    }

    @Override
    public String toString() {
        return "ApplicationInfo(title=" + title +" user=" + user + ")";
    }

    public ComponentKey toComponentKey() {
        return new ComponentKey(componentName, user);
    }

    ItemInfo toItemInfo() {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.componentName = componentName;
        itemInfo.title = Utilities.trim(title);
        itemInfo.iconBitmap = iconBitmap;
        itemInfo.user = user;
        return itemInfo;
    }

    public static Intent makeLaunchIntent(Context context, LauncherActivityInfoCompat info,
            UserHandleCompat user) {
        long serialNumber = UserManagerCompat.getInstance(context).getSerialNumberForUser(user);
        return new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(info.getComponentName())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            .putExtra(EXTRA_PROFILE, serialNumber);
    }

    @Override
    public boolean isDisabled() {
        return isDisabled != 0;
    }
}
