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
import android.content.Intent;
import android.graphics.Bitmap;

import com.sprd.powersavemodelauncher.compat.UserHandleCompat;

/**
 * Represents an item in the launcher.
 */
public class ItemInfo {
    /**
     * Intent extra to store the profile. Format: UserHandle
     */
    static final String EXTRA_PROFILE = "profile";
    /**
     * Title of the item
     */
    public CharSequence title;

    /**
     * A bitmap version of the item.
     */
    public Bitmap iconBitmap;

    /**
     * ComponentName of the item.
     */
    public ComponentName componentName;

    /**
     * Indicates whether we're using a low res icon
     */
    boolean usingLowResIcon;
    /**
     * Content description of the item.
     */
    public CharSequence contentDescription;

    public UserHandleCompat user;

    public ItemInfo() {
        user = UserHandleCompat.myUserHandle();
    }

    ItemInfo(ItemInfo info) {
        copyFrom(info);
    }

    public void copyFrom(ItemInfo info) {
        user = info.user;
        contentDescription = info.contentDescription;
    }

    public void updateIcon(IconCache iconCache) {
        iconCache.getTitleAndIcon(this, componentName, user);
    }

    public Intent getIntent() {
        throw new RuntimeException("Unexpected Intent");
    }

    @Override
    public String toString() {
        return "ItemInfo(component= "+componentName+" user= " + user + ")";
    }

    /**
     * Whether this item is disabled.
     */
    public boolean isDisabled() {
        return false;
    }
}
