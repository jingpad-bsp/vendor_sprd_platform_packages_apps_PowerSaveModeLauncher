/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.sprd.powersavemodelauncher.graphics;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Build;

import com.sprd.powersavemodelauncher.AppCloneUtils;
import com.sprd.powersavemodelauncher.LauncherAppState;
import com.sprd.powersavemodelauncher.R;
import com.sprd.powersavemodelauncher.Utilities;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;

/**
 * Helper methods for generating various launcher icons
 */
public class LauncherIcons {


    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();


    static int sColors[] = { 0xffff0000, 0xff00ff00, 0xff0000ff };
    static int sColorIndex = 0;

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }

    public static Bitmap createIconBitmap(Cursor c, int iconIndex, Context context) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return createIconBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), context);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a bitmap suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    public static Bitmap createIconBitmap(String packageName, String resourceName,
                                          Context context) {
        PackageManager packageManager = context.getPackageManager();
        // the resource
        try {
            Resources resources = packageManager.getResourcesForApplication(packageName);
            if (resources != null) {
                final int id = resources.getIdentifier(resourceName, null, null);
                return createIconBitmap(
                        resources.getDrawableForDensity(id, LauncherAppState.getInstance(context)
                                .getInvariantDeviceProfile().fillResIconDpi), context);
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            // Icon not found.
        }
        return null;
    }

    private static int getIconBitmapSize(Context context) {
        return LauncherAppState.getIDP(context).iconBitmapSize;
    }

    /**
     * If the platform is running O but the app is not providing AdaptiveIconDrawable, then
     * shrink the legacy icon and set it as foreground. Use color drawable as background to
     * create AdaptiveIconDrawable.
     */
    static Drawable wrapToAdaptiveIconDrawable(Context context, Drawable drawable, float scale) {
        if (!(Utilities.LEGACY_ICON_TREATMENT && Utilities.ATLEAST_OREO)) {
            return drawable;
        }

        try {
            if (!(drawable instanceof AdaptiveIconDrawable)) {
                AdaptiveIconDrawable iconWrapper = (AdaptiveIconDrawable)
                        context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
                FixedScaleDrawable fsd = ((FixedScaleDrawable) iconWrapper.getForeground());
                fsd.setDrawable(drawable);
                fsd.setScale(scale);
                return (Drawable) iconWrapper;
            }
        } catch (Exception e) {
            return drawable;
        }
        return drawable;
    }
    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    public static Bitmap createIconBitmap(Bitmap icon, Context context) {
        final int iconBitmapSize = getIconBitmapSize(context);
        if (iconBitmapSize == icon.getWidth() && iconBitmapSize == icon.getHeight()) {
            return icon;
        }
        return createIconBitmap(new BitmapDrawable(context.getResources(), icon), context);
    }

    /**
     * Returns a bitmap suitable for the all apps view. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bitmap createBadgedIconBitmap(
            Drawable icon, UserHandleCompat user, Context context,int iconAppTargetSdk) {

        IconNormalizer normalizer;

        float scale = 1;

        if (!Utilities.LAUNCHER3_DISABLE_ICON_NORMALIZATION) {
            normalizer = IconNormalizer.getInstance(context);
            if (Utilities.ATLEAST_OREO && iconAppTargetSdk >= Build.VERSION_CODES.O) {
                boolean[] outShape = new boolean[1];
                AdaptiveIconDrawable dr = (AdaptiveIconDrawable)
                        context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
                dr.setBounds(0, 0, 1, 1);
                scale = normalizer.getScale(icon, null, dr.getIconMask(), outShape);
                if (Utilities.LEGACY_ICON_TREATMENT &&
                        !outShape[0]){
                    Drawable wrappedIcon = wrapToAdaptiveIconDrawable(context, icon, scale);
                    if (wrappedIcon != icon) {
                        icon = wrappedIcon;
                        scale = normalizer.getScale(icon, null, null, null);
                    }
                }
            } else {
                scale = normalizer.getScale(icon, null, null, null);
            }
        }

        Bitmap bitmap = createIconBitmap(icon, context, scale);
        if (Utilities.ATLEAST_LOLLIPOP && user != null
                && !UserHandleCompat.myUserHandle().equals(user)) {
            BitmapDrawable drawable = new FixedSizeBitmapDrawable(bitmap);
            Bitmap cloneAppBitmap = null;
            boolean isAppClone = AppCloneUtils.isAppCloneUser(user.getUser());
            if (isAppClone) {
                cloneAppBitmap = getVisibleAreaOfIcon(bitmap, context);
            }
            Drawable badged = context.getPackageManager().getUserBadgedIcon(
                    cloneAppBitmap == null ? drawable : new FixedSizeBitmapDrawable(cloneAppBitmap), user.getUser());
            if (isAppClone && cloneAppBitmap != null) {
                normalizer = IconNormalizer.getInstance(context);
                scale = normalizer.getScale(badged, null, null, null);
                return createIconBitmap(badged, context, scale);
            } else {
                if (badged instanceof BitmapDrawable) {
                    return ((BitmapDrawable) badged).getBitmap();
                } else {
                    return createIconBitmap(badged, context);
                }
            }
        } else {
            return bitmap;
        }
    }

    private static Bitmap getVisibleAreaOfIcon(Bitmap icon, Context context) {
        Drawable drawable = new FixedSizeBitmapDrawable(icon);
        Rect outBounds = new Rect();
        IconNormalizer normalizer = IconNormalizer.getInstance(context);
        normalizer.getVisibleAreaBounds(drawable, outBounds);
        int width = outBounds.width();
        int height = outBounds.height();

        if (width > 0 && height > 0) {
            int textureWidth = Math.max(width, height);
            int textureHeight = textureWidth;
            synchronized (sCanvas) {
                Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureWidth,
                        Bitmap.Config.ARGB_8888);
                final Canvas canvas = sCanvas;
                canvas.setBitmap(bitmap);
                int offsetX = (textureWidth - width) / 2;
                int offsetY = (textureHeight - height) / 2;
                canvas.drawBitmap(icon, outBounds, new Rect(offsetX, offsetY, offsetX + width, offsetY + height), null);
                canvas.setBitmap(null);
                return bitmap;
            }
        }
        return null;
    }


    /**
     * Returns a bitmap suitable for the all apps view.
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context) {
        return createIconBitmap(icon, context, 1.0f /* scale */);
    }

    /**
     * @param scale the scale to apply before drawing {@param icon} on the canvas
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context, float scale) {
        synchronized (sCanvas) {
            final int iconBitmapSize = getIconBitmapSize(context);

            int width = iconBitmapSize;
            int height = iconBitmapSize;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null && bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();
            if (sourceWidth > 0 && sourceHeight > 0) {
                // Scale the icon proportionally to the icon dimensions
                final float ratio = (float) sourceWidth / sourceHeight;
                if (sourceWidth > sourceHeight) {
                    height = (int) (width / ratio);
                } else if (sourceHeight > sourceWidth) {
                    width = (int) (height * ratio);
                }
            }

            // no intrinsic size --> use default size
            int textureWidth = iconBitmapSize;
            int textureHeight = iconBitmapSize;

            final Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;

            @SuppressWarnings("all") // suppress dead code warning
            final boolean debug = false;
            if (debug) {
                // draw a big box for the icon for debugging
                canvas.drawColor(sColors[sColorIndex]);
                if (++sColorIndex >= sColors.length) sColorIndex = 0;
                Paint debugPaint = new Paint();
                debugPaint.setColor(0xffcccc00);
                canvas.drawRect(left, top, left+width, top+height, debugPaint);
            }

            sOldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            canvas.save();
            canvas.scale(scale, scale, textureWidth / 2, textureHeight / 2);
            icon.draw(canvas);
            canvas.restore();
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    /**
     * An extension of {@link BitmapDrawable} which returns the bitmap pixel size as intrinsic size.
     * This allows the badging to be done based on the action bitmap size rather than
     * the scaled bitmap size.
     */
    private static class FixedSizeBitmapDrawable extends BitmapDrawable {

        public FixedSizeBitmapDrawable(Bitmap bitmap) {
            super(null, bitmap);
        }

        @Override
        public int getIntrinsicHeight() {
            return getBitmap().getWidth();
        }

        @Override
        public int getIntrinsicWidth() {
            return getBitmap().getWidth();
        }
    }

}
