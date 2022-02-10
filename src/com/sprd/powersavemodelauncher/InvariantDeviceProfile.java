/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.sprd.powersavemodelauncher.util.PackageManagerHelper.getPackageFilter;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.sprd.powersavemodelauncher.util.Thunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class InvariantDeviceProfile {
    private static final String TAG = "InvariantDeviceProfile";
    private static final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";
    private static final int CONFIG_ICON_MASK_RES_ID = Resources.getSystem().getIdentifier(
            "config_icon_mask", "string", "android");
    public static final String KEY_ICON_PATH_REF = "pref_icon_shape_path";

    // This is a static that we use for the default icon size on a 4/5-inch phone
    private static float DEFAULT_ICON_SIZE_DP = 54;

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static float KNEARESTNEIGHBOR = 3;
    private static float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static float WEIGHT_EFFICIENT = 100000f;

    // Profile-defining invariant properties
    String name;
    float minWidthDps;
    float minHeightDps;

    /**
     * The minimum number of predicted apps in all apps.
     */
    int minAllAppsPredictionColumns;

    public float iconSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public float iconTextSize;

    DeviceProfile profile;

    public InvariantDeviceProfile() {
    }

    public InvariantDeviceProfile(InvariantDeviceProfile p) {
        this(p.name, p.minWidthDps, p.minHeightDps,p.minAllAppsPredictionColumns,
                p.iconSize, p.iconTextSize);
    }

    InvariantDeviceProfile(String n, float w, float h, int maapc, float is, float its) {
        name = n;
        minWidthDps = w;
        minHeightDps = h;
        minAllAppsPredictionColumns = maapc;
        iconSize = is;
        iconTextSize = its;
    }

    @TargetApi(23)
    InvariantDeviceProfile(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        Point smallestSize = new Point();
        Point largestSize = new Point();
        display.getCurrentSizeRange(smallestSize, largestSize);

        // This guarantees that width < height
        minWidthDps = Utilities.dpiFromPx(Math.min(smallestSize.x, smallestSize.y), dm);
        minHeightDps = Utilities.dpiFromPx(Math.min(largestSize.x, largestSize.y), dm);

        ArrayList<InvariantDeviceProfile> closestProfiles =
                findClosestDeviceProfiles(minWidthDps, minHeightDps, getPredefinedDeviceProfiles());
        InvariantDeviceProfile interpolatedDeviceProfileOut =
                invDistWeightedInterpolate(minWidthDps,  minHeightDps, closestProfiles);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String savedIconMaskPath = Utilities.getDevicePrefs(context).getString(KEY_ICON_PATH_REF, "");
                if (!savedIconMaskPath.equals(getIconShapePath(context))) {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        }, getPackageFilter("android", ACTION_OVERLAY_CHANGED));

        InvariantDeviceProfile closestProfile = closestProfiles.get(0);
        minAllAppsPredictionColumns = closestProfile.minAllAppsPredictionColumns;

        iconSize = interpolatedDeviceProfileOut.iconSize;
        iconBitmapSize = Utilities.pxFromDp(iconSize, dm);
        iconTextSize = interpolatedDeviceProfileOut.iconTextSize;
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        Point realSize = new Point();
        display.getRealSize(realSize);
        // The real size never changes. smallSide and largeSide will remain the
        // same in any orientation.
        int smallSide = Math.min(realSize.x, realSize.y);
        int largeSide = Math.max(realSize.x, realSize.y);

        profile = new DeviceProfile(context, this, smallestSize, largestSize,
                smallSide, largeSide);
    }

    public void verifyConfigChangedInBackground(Context context) {
        String savedIconMaskPath = Utilities.getDevicePrefs(context).getString(KEY_ICON_PATH_REF, "");
        // Good place to check if grid size changed in themepicker when launcher was dead.
        if (savedIconMaskPath.isEmpty()) {
            Utilities.getDevicePrefs(context).edit().putString(KEY_ICON_PATH_REF, getIconShapePath(context))
                    .apply();
        } else if (!savedIconMaskPath.equals(getIconShapePath(context))) {
            Utilities.getDevicePrefs(context).edit().putString(KEY_ICON_PATH_REF, getIconShapePath(context))
                    .apply();
            LauncherAppState.getInstance(context).getIconCache().mIconDb.clearDbIfNeed();
            LauncherAppState.getInstance(context).mModel.forceReload();
        }
    }

    /**
     * Retrieve system defined or RRO overriden icon shape.
     */
    private static String getIconShapePath(Context context) {
        if (CONFIG_ICON_MASK_RES_ID == 0) {
            Log.e(TAG, "Icon mask res identifier failed to retrieve.");
            return "";
        }
        return context.getResources().getString(CONFIG_ICON_MASK_RES_ID);
    }

    ArrayList<InvariantDeviceProfile> getPredefinedDeviceProfiles() {
        ArrayList<InvariantDeviceProfile> predefinedDeviceProfiles = new ArrayList<>();
        // width, height,
        // #iconSize, #iconTextSize
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Super Short Stubby", 255, 300, 3, 48, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Shorter Stubby", 255, 400, 3, 48, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Short Stubby",275, 420,4, 48, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Stubby", 255, 450,4, 48, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus S", 296, 491.33f,4, 48, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 4", 359, 567,4, DEFAULT_ICON_SIZE_DP, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 5", 335, 567,4, DEFAULT_ICON_SIZE_DP, 13));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Large Phone", 406, 694,4, 56, 14.4f));
        // The tablet profile is odd in that the landscape orientation
        // also includes the nav bar on the side
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 7", 575,904, 4,64, 14.4f));
        // Larger tablet profiles always have system bars on the top & bottom
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("Nexus 10", 727,1207, 4,76, 14.4f));
        predefinedDeviceProfiles.add(new InvariantDeviceProfile("20-inch Tablet", 1527, 2527,4, 100, 20));
        return predefinedDeviceProfiles;
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[] {
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    @Thunk float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    /**
     * Returns the closest device profiles ordered by closeness to the specified width and height
     */
    // Package private visibility for testing.
    ArrayList<InvariantDeviceProfile> findClosestDeviceProfiles(
            final float width, final float height, ArrayList<InvariantDeviceProfile> points) {

        // Sort the profiles by their closeness to the dimensions
        ArrayList<InvariantDeviceProfile> pointsByNearness = points;
        Collections.sort(pointsByNearness, new Comparator<InvariantDeviceProfile>() {
            public int compare(InvariantDeviceProfile a, InvariantDeviceProfile b) {
                return Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                        dist(width, height, b.minWidthDps, b.minHeightDps));
            }
        });

        return pointsByNearness;
    }

    // Package private visibility for testing.
    InvariantDeviceProfile invDistWeightedInterpolate(float width, float height,
                ArrayList<InvariantDeviceProfile> points) {
        float weights = 0;

        InvariantDeviceProfile p = points.get(0);
        if (dist(width, height, p.minWidthDps, p.minHeightDps) == 0) {
            return p;
        }

        InvariantDeviceProfile out = new InvariantDeviceProfile();
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            p = new InvariantDeviceProfile(points.get(i));
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(p.multiply(w));
        }
        return out.multiply(1.0f/weights);
    }

    private void add(InvariantDeviceProfile p) {
        iconSize += p.iconSize;
        iconTextSize += p.iconTextSize;
    }

    private InvariantDeviceProfile multiply(float w) {
        iconSize *= w;
        iconTextSize *= w;
        return this;
    }

    private float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }
}
