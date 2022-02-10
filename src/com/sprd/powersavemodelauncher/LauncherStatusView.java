package com.sprd.powersavemodelauncher;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.GridLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Created by SPRD on 7/17/17.
 */

public class LauncherStatusView extends GridLayout{
    private TextClock mDateView;
    private TextClock mClockView;
    private TextView mBatteryInfo;
    private Context mContext;
    LauncherStatusMonitor mMonitor;
    private int mBatteryLevel;

    public LauncherStatusView(Context context) {
        super(context, null);
    }

    public LauncherStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mMonitor = new LauncherStatusMonitor(context);
    }

    private LauncherStatusMonitorCallback mLauncherMonitorCallback = new LauncherStatusMonitorCallback() {
        @Override
        public void onTimeChanged() {
            refreshTime();
        }

        @Override
        public void onUpdateBatteryLevel(int level) {
            mBatteryLevel = level;
            refreshBatteryLevel(level);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        mMonitor.register(mContext);
        mMonitor.registerCallback(mLauncherMonitorCallback);
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        mMonitor.unRegister(mContext);
        mMonitor.removeCallback(mLauncherMonitorCallback);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDateView = (TextClock) findViewById(R.id.date_view);
        mClockView = (TextClock) findViewById(R.id.clock_view);
        mBatteryInfo = (TextView) findViewById(R.id.battery_info);

        refreshTime();
        refreshBatteryLevel(mBatteryLevel);

        mClockView.setElegantTextHeight(false);
    }

    private void refreshTime() {
        Patterns.update(mContext);

        mDateView.setFormat24Hour(Patterns.dateView);
        mDateView.setFormat12Hour(Patterns.dateView);

        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    private void refreshBatteryLevel(int level) {
        if(mBatteryInfo == null) return;
        String percentage = NumberFormat.getPercentInstance().format((double) level / 100.0);
        mBatteryInfo.setText(mContext.getString(R.string.battery_level, percentage));
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String dateView;
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String dateViewSkel = res.getString(R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);

            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

            cacheKey = key;
        }
    }

}
