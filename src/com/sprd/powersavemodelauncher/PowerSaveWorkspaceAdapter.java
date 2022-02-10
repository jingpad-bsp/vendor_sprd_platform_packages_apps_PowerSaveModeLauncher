package com.sprd.powersavemodelauncher;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by SPRD on 7/18/17.
 */

public class PowerSaveWorkspaceAdapter extends BaseAdapter{
    private PowerSaveLauncher mPowerSaveLauncher;
    private ArrayList<ItemInfo> mItemInfos = new ArrayList<>();
    CharSequence defaultLabel;
    Drawable defaultAddIcon;
    Drawable defaultAddOffIcon;

    PowerSaveWorkspaceAdapter(PowerSaveLauncher powerSaveLauncher, ArrayList<ItemInfo> workspaceItems) {
        mPowerSaveLauncher = powerSaveLauncher;
        mItemInfos = workspaceItems;
        defaultLabel = mPowerSaveLauncher.getString(R.string.default_app_title);
        defaultAddIcon = mPowerSaveLauncher.getDrawable(R.drawable.default_add_icon);
        defaultAddOffIcon = mPowerSaveLauncher.getDrawable(R.drawable.add_off);

        mPowerSaveLauncher.resizeIconDrawable(defaultAddIcon);
        mPowerSaveLauncher.resizeIconDrawable(defaultAddOffIcon);
    }

    @Override
    public int getCount() {
        return mItemInfos.size();
    }

    @Override
    public String getItem(int position) {
        return mItemInfos.get(position).toString();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemHolder itemHolder;
        if(convertView == null || convertView.getTag() == null) {
            LayoutInflater inflater = mPowerSaveLauncher.getLayoutInflater();
            convertView = inflater.inflate(R.layout.power_save_workspace_item, null);
            itemHolder = new ItemHolder();
            itemHolder.appLabel = (TextView)convertView.findViewById(R.id.app_info);
            itemHolder.deleteImage = (ImageView)convertView.findViewById(R.id.delete_image);
            convertView.setTag(itemHolder);
        } else {
            itemHolder = (ItemHolder) convertView.getTag();
        }

        ItemInfo itemInfo = mItemInfos.get(position);
        boolean isSelected = itemInfo != null;
        if(isSelected) {
            CharSequence appTitle = itemInfo.title;
            Drawable appIcon = mPowerSaveLauncher.createIconDrawable(itemInfo.iconBitmap);
            itemHolder.appLabel.setText(appTitle);
            appIcon.setBounds(0, 0, appIcon.getMinimumWidth(), appIcon.getMinimumHeight());
            itemHolder.appLabel.setCompoundDrawables(null, appIcon, null, null);
        } else {
            itemHolder.appLabel.setText(defaultLabel);
            itemHolder.appLabel.setCompoundDrawables(null, defaultAddIcon, null, null);
        }
        itemHolder.deleteImage.setVisibility((PowerSaveWorkspace.isInEditMode && isSelected) ? View.VISIBLE : View.INVISIBLE);
        convertView.setVisibility((PowerSaveWorkspace.isInEditMode && !isSelected) ? View.INVISIBLE : View.VISIBLE);
        return convertView;
    }

    static class ItemHolder {
        TextView appLabel;
        ImageView deleteImage;
    }
}
