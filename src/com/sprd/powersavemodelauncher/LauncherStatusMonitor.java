package com.sprd.powersavemodelauncher;

import static android.os.BatteryManager.EXTRA_LEVEL;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by SPRD on 7/21/17.
 */

class LauncherStatusMonitor {
    private static final String TAG = "LauncherStatusMonitor";

    private final ArrayList<WeakReference<LauncherStatusMonitorCallback>>
            mCallbacks = new ArrayList<>();

    private static final int MSG_TIME_UPDATE = 1001;
    private static final int MSG_BATTERY_UPDATE = 1002;

    LauncherStatusMonitor(Context context) {
        // Monitor interesting updates
    }

    void register(Context context) {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    void unRegister(Context context) {
        context.unregisterReceiver(mBroadcastReceiver);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TIME_UPDATE:
                    handleTimeUpdate();
                    break;
                case MSG_BATTERY_UPDATE:
                    handleBatteryUpdate((int)msg.obj);
                    break;
            }
        }
    };

    private void handleTimeUpdate() {
        Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherStatusMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTimeChanged();
            }
        }
    }

    private void handleBatteryUpdate(int batteryLevel) {
        Log.d(TAG, "handleBatteryUpdate");
        for (int i = 0; i < mCallbacks.size(); i++) {
            LauncherStatusMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUpdateBatteryLevel(batteryLevel);
            }
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                final int level = intent.getIntExtra(EXTRA_LEVEL, 0);

                final Message msg = mHandler.obtainMessage(
                        MSG_BATTERY_UPDATE, level);
                mHandler.sendMessage(msg);
            }
        }
    };

    public void registerCallback(LauncherStatusMonitorCallback callback) {
        Log.v(TAG, "* register callback for " + callback);
        // Prevent adding duplicate callbacks
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                Log.e(TAG, "Object tried to add another callback",
                        new Exception("Called by"));
                return;
            }
        }
        mCallbacks.add(new WeakReference<LauncherStatusMonitorCallback>(callback));
        removeCallback(null); // remove unused references
    }

    public void removeCallback(LauncherStatusMonitorCallback callback) {
        Log.v(TAG, "* unregister callback for " + callback);
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }
}
