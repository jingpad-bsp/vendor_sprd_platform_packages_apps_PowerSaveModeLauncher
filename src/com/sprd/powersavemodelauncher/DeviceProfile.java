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
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;

public class DeviceProfile {

    private static final String TAG = "DeviceProfile";

    public final InvariantDeviceProfile inv;

    public final int widthPx;
    public final int heightPx;
    public final int availableWidthPx;
    public final int availableHeightPx;
    /**
     * The maximum amount of left/right workspace padding as a percentage of the screen width.
     * To be clear, this means that up to 7% of the screen width can be used as left padding, and
     * 7% of the screen width can be used as right padding.
     */

    private static final int DEFAULT_ALLAPPS_COLUMNS = 3;

    // Workspace icons
    public int iconSizePx;

    // All apps
    public int allAppsNumCols;
    public int allAppsNumPredictiveCols;
    public final int allAppsIconSizePx;
    public final float allAppsIconTextSizeSp;

    public DeviceProfile(Context context, InvariantDeviceProfile inv,
            Point minSize, Point maxSize,
            int width, int height) {

        this.inv = inv;

        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        // AllApps uses the original non-scaled icon text size
        allAppsIconTextSizeSp = inv.iconTextSize;

        // AllApps uses the original non-scaled icon size
        allAppsIconSizePx = Utilities.pxFromDp(inv.iconSize, dm);

        // Determine sizes.
        widthPx = width;
        heightPx = height;
        availableWidthPx = minSize.x;
        availableHeightPx = maxSize.y;

        // Calculate the remaining vars
        float scale = 1f;
        iconSizePx = (int) (Utilities.pxFromDp(inv.iconSize, dm) * scale);
    }

    /**
     * @param recyclerViewWidth the available width of the AllAppsRecyclerView
     */
    public void updateAppsViewNumCols(Resources res, int recyclerViewWidth) {
        int appsViewLeftMarginPx =
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin);
        int allAppsCellWidthGap =
                res.getDimensionPixelSize(R.dimen.all_apps_icon_width_gap);
        int availableAppsWidthPx = (recyclerViewWidth > 0) ? recyclerViewWidth : availableWidthPx;
        int numAppsCols = (availableAppsWidthPx + allAppsCellWidthGap - appsViewLeftMarginPx) /
                (allAppsIconSizePx + allAppsCellWidthGap);
        Log.d(TAG,"recyclerViewWidth = "+ recyclerViewWidth
                               + "  availableAppsWidthPx = "+ availableAppsWidthPx
                               + "  numAppsCols = "+numAppsCols
                               + "  inv.minAllAppsPredictionColumns = "+inv.minAllAppsPredictionColumns);
        if(numAppsCols <= 0){
            numAppsCols = DEFAULT_ALLAPPS_COLUMNS;
        }
        int numPredictiveAppCols = Math.max(inv.minAllAppsPredictionColumns, numAppsCols);
        allAppsNumCols = numAppsCols;
        allAppsNumPredictiveCols = numPredictiveAppCols;
    }
}
