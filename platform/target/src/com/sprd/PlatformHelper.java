package com.sprd;

import android.content.Context;
import android.os.ServiceManager;
import android.os.sprdpower.IPowerManagerEx;
import android.os.sprdpower.PowerManagerEx;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SPRD on 9/19/17.
 */
public class PlatformHelper {

    private static final String TAG = "PlatformHelper";

    public static final int MODE_INVALID = PowerManagerEx.MODE_INVALID;
    public static final int MODE_PERFORMANCE = PowerManagerEx.MODE_PERFORMANCE;
    public static final int MODE_SMART = PowerManagerEx.MODE_SMART;
    public static final int MODE_POWERSAVING = PowerManagerEx.MODE_POWERSAVING;
    public static final int MODE_LOWPOWER = PowerManagerEx.MODE_LOWPOWER;
    public static final int MODE_ULTRASAVING = PowerManagerEx.MODE_ULTRASAVING;
    public static final int MODE_NONE = PowerManagerEx.MODE_NONE; //to close the power save mode
    public static final int MODE_MAX = PowerManagerEx.MODE_MAX;

    public static boolean addAllowedAppInUltraSavingMode(Context context, String value) {
        try {
            IPowerManagerEx powerManagerEx = IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
            if(powerManagerEx != null && powerManagerEx.addAllowedAppInUltraSavingMode(value)) {
                return true;
            }
        } catch(RemoteException e) {
            Log.e(TAG, "addAllowedAppInUltraSavingMode fail.");
        }
        return false;
    }

    public static boolean delAllowedAppInUltraSavingMode(Context context, String value) {
        try {
            IPowerManagerEx powerManagerEx = IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
            if(powerManagerEx != null && powerManagerEx.delAllowedAppInUltraSavingMode(value)) {
                return true;
            }
        } catch(RemoteException e) {
            Log.e(TAG, "delAllowedAppInUltraSavingMode fail.");
        }
        return false;
    }

    public static List<String> getAllowedAppListInUltraSavingMode(Context context) {
        List<String> allowAppList = new ArrayList<>();
        try {
            IPowerManagerEx powerManagerEx = IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
            if(powerManagerEx != null) {
                allowAppList = powerManagerEx.getAllowedAppListInUltraSavingMode();
            }
        } catch(RemoteException e) {
            Log.e(TAG, "getAllowedAppListInUltraSavingMode fail.");
        }
        return allowAppList;
    }

    public static void quitUltraPowerSaveMode(Context context) {
        try {
            //quit ultra power save mode
            IPowerManagerEx powerManagerEx = IPowerManagerEx.Stub.asInterface(ServiceManager.getService("power_ex"));
            int mode = powerManagerEx.getPrePowerSaveMode();
            Log.d(TAG, " getPrePowerSaveMode: "+mode);
            if(mode != PowerManagerEx.MODE_INVALID) {
                if(powerManagerEx.setPowerSaveMode(mode)) {
                    Log.d(TAG, " setPowerSaveMode: "+mode + " success!");
                }
            }
        } catch(Exception e) {
            Log.d(TAG, " setPowerSaveMode fail, "+e);
        }
    }
}