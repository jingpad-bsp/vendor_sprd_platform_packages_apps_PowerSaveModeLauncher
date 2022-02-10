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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.widget.TextView;

import com.sprd.powersavemodelauncher.IconCache.IconLoadRequest;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView
        implements BaseRecyclerViewFastScrollBar.FastScrollFocusableView {
    private static final float SHADOW_LARGE_RADIUS = 4.0f;
    private static final float SHADOW_SMALL_RADIUS = 1.75f;
    private static final float SHADOW_Y_OFFSET = 2.0f;
    private static final int SHADOW_LARGE_COLOUR = 0xDD000000;
    private static final int SHADOW_SMALL_COLOUR = 0xCC000000;

    private final PowerSaveLauncher mPowerSaveLauncher;
    private Drawable mIcon;
    private final Drawable mBackground;

    private boolean mBackgroundSizeChanged;

    private final boolean mCustomShadowsEnabled;
    private final boolean mLayoutHorizontal;
    protected final int mIconSize;
    private int mTextColor;
    private boolean mIgnorePressedStateChange;
    private boolean mDisableRelayout = false;

    private IconLoadRequest mIconLoadRequest;

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mPowerSaveLauncher = (PowerSaveLauncher) context;
        DeviceProfile grid = mPowerSaveLauncher.getDeviceProfile();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mCustomShadowsEnabled = a.getBoolean(R.styleable.BubbleTextView_customShadows, true);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);

        setTextSize(TypedValue.COMPLEX_UNIT_SP, grid.allAppsIconTextSizeSp);
        int  defaultIconSize = grid.allAppsIconSizePx;

        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);

        a.recycle();

        if (mCustomShadowsEnabled) {
            // Draw the background itself as the parent is drawn twice.
            mBackground = getBackground();
            setBackground(null);
        } else {
            mBackground = null;
        }

        if (mCustomShadowsEnabled) {
            setShadowLayer(SHADOW_LARGE_RADIUS, 0.0f, SHADOW_Y_OFFSET, SHADOW_LARGE_COLOUR);
        }
    }

    public void applyFromApplicationInfo(AppInfo info) {
        FastBitmapDrawable iconDrawable = mPowerSaveLauncher.createIconDrawable(info.iconBitmap);
        if (info.isDisabled()) {
            iconDrawable.setState(FastBitmapDrawable.State.DISABLED);
        }
        setIcon(iconDrawable, mIconSize);
        setText(info.title);
        if (info.contentDescription != null) {
            setContentDescription(info.contentDescription);
        }
        super.setTag(info);

        // Verify high res immediately
        verifyHighRes();
    }

    /**
     * Used for measurement only, sets some dummy values on this view.
     */
    public void applyDummyInfo() {
        ColorDrawable d = new ColorDrawable();
        setIcon(mPowerSaveLauncher.resizeIconDrawable(d), mIconSize);
        setText("");
    }

    @Override
    protected boolean setFrame(int left, int top, int right, int bottom) {
        if (getLeft() != left || getRight() != right || getTop() != top || getBottom() != bottom) {
            mBackgroundSizeChanged = true;
        }
        return super.setFrame(left, top, right, bottom);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mBackground || super.verifyDrawable(who);
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);

        if (!mIgnorePressedStateChange) {
            updateIconState();
        }
    }

    /** Returns the icon for this view. */
    public Drawable getIcon() {
        return mIcon;
    }

    private void updateIconState() {
        if (mIcon instanceof FastBitmapDrawable) {
            FastBitmapDrawable d = (FastBitmapDrawable) mIcon;
            if (getTag() instanceof ItemInfo
                    && ((ItemInfo) getTag()).isDisabled()) {
                d.animateState(FastBitmapDrawable.State.DISABLED);
            } else if (isPressed()) {
                d.animateState(FastBitmapDrawable.State.PRESSED);
            } else {
                d.animateState(FastBitmapDrawable.State.NORMAL);
            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);

        mIgnorePressedStateChange = false;
        updateIconState();
        return result;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!mCustomShadowsEnabled) {
            super.draw(canvas);
            return;
        }

        final Drawable background = mBackground;
        if (background != null) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();

            if (mBackgroundSizeChanged) {
                background.setBounds(0, 0,  getRight() - getLeft(), getBottom() - getTop());
                mBackgroundSizeChanged = false;
            }

            if ((scrollX | scrollY) == 0) {
                background.draw(canvas);
            } else {
                canvas.translate(scrollX, scrollY);
                background.draw(canvas);
                canvas.translate(-scrollX, -scrollY);
            }
        }

        // If text is transparent, don't draw any shadow
        if (getCurrentTextColor() == getResources().getColor(android.R.color.transparent)) {
            getPaint().clearShadowLayer();
            super.draw(canvas);
            return;
        }

        // We enhance the shadow by drawing the shadow twice
        getPaint().setShadowLayer(SHADOW_LARGE_RADIUS, 0.0f, SHADOW_Y_OFFSET, SHADOW_LARGE_COLOUR);
        super.draw(canvas);
        canvas.save();
        canvas.clipRect(getScrollX(), getScrollY() + getExtendedPaddingTop(),
                getScrollX() + getWidth(),
                getScrollY() + getHeight(), Region.Op.INTERSECT);
        getPaint().setShadowLayer(SHADOW_SMALL_RADIUS, 0.0f, 0.0f, SHADOW_SMALL_COLOUR);
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBackground != null) mBackground.setCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackground != null) mBackground.setCallback(null);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        super.setTextColor(color);
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        mTextColor = colors.getDefaultColor();
        super.setTextColor(colors);
    }

    public void setTextVisibility(boolean visible) {
        Resources res = getResources();
        if (visible) {
            super.setTextColor(mTextColor);
        } else {
            super.setTextColor(res.getColor(android.R.color.transparent));
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private Drawable setIcon(Drawable icon, int iconSize) {
        mIcon = icon;
        if (iconSize != -1) {
            mIcon.setBounds(0, 0, iconSize, iconSize);
        }
        if (mLayoutHorizontal) {
            if (Utilities.ATLEAST_JB_MR1) {
                setCompoundDrawablesRelative(mIcon, null, null, null);
            } else {
                setCompoundDrawables(mIcon, null, null, null);
            }
        } else {
            setCompoundDrawables(null, mIcon, null, null);
        }
        return icon;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    public void reapplyItemInfo(final ItemInfo info) {
        if (getTag() == info) {
            FastBitmapDrawable.State prevState = FastBitmapDrawable.State.NORMAL;
            if (mIcon instanceof FastBitmapDrawable) {
                prevState = ((FastBitmapDrawable) mIcon).getCurrentState();
            }
            mIconLoadRequest = null;
            mDisableRelayout = true;

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            }
            // If we are reapplying over an old icon, then we should update the new icon to the same
            // state as the old icon
            if (mIcon instanceof FastBitmapDrawable) {
                ((FastBitmapDrawable) mIcon).setState(prevState);
            }

            mDisableRelayout = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }

        IconCache iconCache = LauncherAppState.getInstance(mPowerSaveLauncher).getIconCache();
        synchronized (iconCache) {
            if (getTag() instanceof AppInfo) {
                AppInfo info = (AppInfo) getTag();
                if (info.usingLowResIcon) {
                    mIconLoadRequest = iconCache.updateIconInBackground(BubbleTextView.this, info);
                }
            }
        }
    }

    @Override
    public void setFastScrollFocusState(final FastBitmapDrawable.State focusState, boolean animated) {
        // We can only set the fast scroll focus state on a FastBitmapDrawable
        if (!(mIcon instanceof FastBitmapDrawable)) {
            return;
        }

        FastBitmapDrawable d = (FastBitmapDrawable) mIcon;
        if (animated) {
            FastBitmapDrawable.State prevState = d.getCurrentState();
            if (d.animateState(focusState)) {
                // If the state was updated, then update the view accordingly
                animate().scaleX(focusState.viewScale)
                        .scaleY(focusState.viewScale)
                        .setStartDelay(getStartDelayForStateChange(prevState, focusState))
                        .setDuration(d.getDurationForStateChange(prevState, focusState))
                        .start();
            }
        } else {
            if (d.setState(focusState)) {
                // If the state was updated, then update the view accordingly
                animate().cancel();
                setScaleX(focusState.viewScale);
                setScaleY(focusState.viewScale);
            }
        }
    }

    /**
     * Returns the start delay when animating between certain {@link FastBitmapDrawable} states.
     */
    private static int getStartDelayForStateChange(final FastBitmapDrawable.State fromState,
            final FastBitmapDrawable.State toState) {
        switch (toState) {
            case NORMAL:
                switch (fromState) {
                    case FAST_SCROLL_HIGHLIGHTED:
                        return FastBitmapDrawable.FAST_SCROLL_INACTIVE_DURATION / 4;
                }
        }
        return 0;
    }
}
