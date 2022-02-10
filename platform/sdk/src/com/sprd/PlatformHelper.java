package com.sprd;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by SPRD on 9/19/17.
 */
public class PlatformHelper {

    private static final String TAG = "PlatformHelper";

    private static final String ACTION_BATTERY_MANAGEMENT = "android.intent.action.POWER_USAGE_SUMMARY";
    private static final String CONFIG_FILES = "workspace.config";
    private static final String PREFS_KEY = "workspace_config";
    private static final String SEPARATOR = "@";
    private static final String DEFAULT_CONFIG = new StringBuilder()
            .append("com.android.dialer/.app.DialtactsActivity#0")
            .append(SEPARATOR)
            .append("com.android.messaging/.ui.conversationlist.ConversationListActivity#1")
            .append(SEPARATOR)
            .append("com.android.contacts/.activities.PeopleActivity#2")
            .toString();

    public static final int MODE_INVALID = -1;
    public static final int MODE_PERFORMANCE = 0;
    public static final int MODE_SMART = 1;
    public static final int MODE_POWERSAVING = 2;
    public static final int MODE_LOWPOWER = 3;
    public static final int MODE_ULTRASAVING = 4;
    public static final int MODE_NONE = 5; //to close the power save mode
    public static final int MODE_MAX = MODE_NONE;

    private static CopyOnWriteArrayList<String> sAppList = new CopyOnWriteArrayList<>();

    public static boolean addAllowedAppInUltraSavingMode(Context context, String value) {
        if (!TextUtils.isEmpty(value) && sAppList.add(value)) {
            updateConfig(context);
            Log.d(TAG, "addAllowedAppInUltraSavingMode value:" + value);
            return true;
        }
        return false;
    }

    public static boolean delAllowedAppInUltraSavingMode(Context context, String value) {
        if (!TextUtils.isEmpty(value) && sAppList.remove(value)) {
            updateConfig(context);
            Log.d(TAG, "delAllowedAppInUltraSavingMode value:" + value);
            return true;
        }
        return false;
    }

    public static List<String> getAllowedAppListInUltraSavingMode(Context context) {
        String allowApps = getPrefs(context).getString(PREFS_KEY, DEFAULT_CONFIG);
        String[] sArrays = allowApps.split(SEPARATOR);
        sAppList.clear();
        sAppList.addAll(Arrays.asList(sArrays));
        return sAppList;
    }

    public static void quitUltraPowerSaveMode(Context context) {
        Intent intent = new Intent(ACTION_BATTERY_MANAGEMENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        Log.d(TAG, " quitUltraPowerSaveMode");
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(CONFIG_FILES, Context.MODE_PRIVATE);
    }

    @SuppressLint("ApplySharedPref")
    private static void updateConfig(Context context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sAppList.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(sAppList.get(i));
        }
        getPrefs(context).edit().putString(PREFS_KEY, sb.toString()).commit();
    }
}