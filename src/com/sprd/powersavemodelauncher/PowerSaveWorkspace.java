package com.sprd.powersavemodelauncher;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.sprd.PlatformHelper;
import com.sprd.powersavemodelauncher.compat.LauncherAppsCompat;
import com.sprd.powersavemodelauncher.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SPRD on 7/17/17.
 */

public class PowerSaveWorkspace extends LinearLayout implements  View.OnClickListener,
        GridView.OnItemClickListener, GridView.OnItemLongClickListener{
    private static final String TAG = "PowerSaveWorkspace";
    private PowerSaveLauncher mPowerSaveLauncher;

    static boolean isInEditMode = false;

    private ArrayList<ItemInfo> mWorkspaceItem = new ArrayList<>();

    private Context mContext;

    private ImageView mEditView;
    private ImageView mQuitView;
    GridView mGridView;
    private PowerSaveWorkspaceAdapter mAdapter;

    private int mPositionClickToApps;
    private List<Integer> mReadyToRemoveAppPositions = new ArrayList<>();

    public PowerSaveWorkspace(Context context) {
        this(context, null, 0);
    }

    public PowerSaveWorkspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PowerSaveWorkspace(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        mPowerSaveLauncher = (PowerSaveLauncher)context;
        mAdapter = new PowerSaveWorkspaceAdapter(mPowerSaveLauncher, mWorkspaceItem);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEditView = (ImageView) findViewById(R.id.edit_image);
        mQuitView = (ImageView) findViewById(R.id.quit_image);
        mGridView = (GridView) findViewById(R.id.app_list_grid_view);
        if (Utilities.isRtl(mPowerSaveLauncher.getResources())) {
            mQuitView.setRotation(180);
        }

        mEditView.setOnClickListener(this);
        mQuitView.setOnClickListener(this);
        mGridView.setOnItemClickListener(this);
        mGridView.setOnItemLongClickListener(this);
        mGridView.setAdapter(mAdapter);
    }

    @Override
    public void onClick(View v) {
        if(v == mEditView) {
            if(isInEditMode) {
                quitEditModeAndRefreshView(true);
            } else {
                enterEditModeAndRefreshView();
            }
        } else if(v == mQuitView) {
            if(isInEditMode) {
                quitEditModeAndRefreshView(false);
            } else {
                showQuitPowerSaveModeDialog(false);
            }
        }
    }

    void quitEditModeAndRefreshView(boolean removeAppOfWorkspace) {
        //set edit mode to false
        isInEditMode = false;

        //refresh edit image
        mEditView.setImageResource(R.drawable.power_save_edit);
        mEditView.setContentDescription(mContext.getString(R.string.edit));
        mQuitView.setImageResource(R.drawable.power_save_quit);
        mQuitView.setContentDescription(mContext.getString(R.string.quit));

        if(removeAppOfWorkspace) {
            //remove apps from workspace
            List<Integer> list = new ArrayList<>(mReadyToRemoveAppPositions);
            mReadyToRemoveAppPositions.clear();

            synchronousUltraSavingIfNeeded();

            for(int position:list) {
                //remove data from workspace
                ItemInfo itemInfo = mWorkspaceItem.get(position);
                if(itemInfo != null) {
                    String delValue = itemInfo.componentName.flattenToShortString() + "#" + position
                            + "#" + Utilities.getUserSerialNumber(mPowerSaveLauncher, itemInfo.user);
                    if(PlatformHelper.delAllowedAppInUltraSavingMode(mPowerSaveLauncher, delValue)) {
                        Log.d(TAG, "delete " + delValue + " from allowed app list success!");

                        //clear the selected position
                        updateSelectedPositionItem(position, null);
                        //update the bg workspace data list
                        LauncherModel.updateWorkspaceBgDataList(position, null);
                    }
                }
            }
        } else {
            //just clear the list
            mReadyToRemoveAppPositions.clear();
        }
        mPowerSaveLauncher.forceReload();
        //update grid view.
        notifyAdapterDataChanged();
    }

    void enterEditModeAndRefreshView() {
        //set edit mode to true
        isInEditMode = true;

        //refresh exit button and quit button
        mEditView.setImageResource(R.drawable.power_save_confirm);
        mEditView.setContentDescription(mContext.getString(android.R.string.ok));
        mQuitView.setImageResource(R.drawable.power_save_cancel);
        mQuitView.setContentDescription(mContext.getString(R.string.quit));

        //refresh grid view.
        notifyAdapterDataChanged();
    }

    private void showQuitPowerSaveModeDialog(boolean showDialog) {
        if(showDialog) {
            new AlertDialog.Builder(mPowerSaveLauncher)
                    .setMessage(R.string.quit_power_save_mode_message)
                    .setNegativeButton(R.string.workspace_cancel_quit_power_save_mode, null)
                    .setPositiveButton(R.string.quit,
                            new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    PlatformHelper.quitUltraPowerSaveMode(mPowerSaveLauncher);
                                }
                            })
                    .show();
        } else {
            PlatformHelper.quitUltraPowerSaveMode(mPowerSaveLauncher);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        boolean isSelected = mWorkspaceItem.get(position) != null;

        if(isInEditMode) {
            //in Edit Mode
            if(isSelected) {
                view.setVisibility(View.INVISIBLE);
                mReadyToRemoveAppPositions.add(position);
            }
        } else {
            //not in Edit Mode
            if(isSelected) {
                //if the position is selected, start app
                ItemInfo itemInfo = mWorkspaceItem.get(position);
                if(itemInfo != null) {
                    ComponentName cn = itemInfo.componentName;
                    if(cn != null) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setComponent(cn);

                        /* SPRD : Add for bug 826072,adaptation for appclone feature. In some case ,ordinary app may want
                         * to start clone app while its intent looks like:Intent { act=android.intent.action.MAIN
                         * cat=[android.intent.category.LAUNCHER] pkg=com.whatsapp cmp=com.whatsapp/.Main } @{*/
                        intent.addFlags(0x00000400);
                        /* @} */


                        UserHandleCompat user = itemInfo.user;
                        try {
                            if (user == null || user.equals(UserHandleCompat.myUserHandle())) {
                                mPowerSaveLauncher.startActivity(intent);
                            } else {
                                //user is not null, have clone apps.
                                LauncherAppsCompat.getInstance(mContext).startActivityForProfile(
                                        intent.getComponent(), user, null, null);
                            }
                        } catch (ActivityNotFoundException|SecurityException e) {
                            Toast.makeText(mPowerSaveLauncher, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Unable to launch, " + " intent = " + intent, e);
                        }
                        mPowerSaveLauncher.overridePendingTransition(0,0);
                    }
                }
            } else {
                //if the position is empty, show all app view
                mPositionClickToApps = position;
                mPowerSaveLauncher.showAppsView();
            }
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        boolean isSelected = mWorkspaceItem.get(position) != null;

        if(!isInEditMode) {
            //not in Edit Mode
            if(isSelected) {
                enterEditModeAndRefreshView();
            }
        }
        return true;
    }

    public void updateWorkspaceItems(ArrayList<ItemInfo> itemInfos) {
        if(mWorkspaceItem == null) {
            return;
        }
        mWorkspaceItem.clear();
        mWorkspaceItem.addAll(itemInfos);
        //update the adapter data
        notifyAdapterDataChanged();
    }

    public void updateSelectedPositionItem(int position, AppInfo appInfo) {
        if(mWorkspaceItem == null || mWorkspaceItem.size() <= 0) {
            return;
        }

        mWorkspaceItem.remove(position);
        ItemInfo newItemInfo = null;
        if(appInfo != null) {
            newItemInfo = appInfo.toItemInfo();
        }
        mWorkspaceItem.add(position, newItemInfo);
    }

    public void addAppWorkspaceFromApps(AppInfo appInfo) {
        if(appInfo == null) {
            return;
        }

        synchronousUltraSavingIfNeeded();

        //add the selected app to allowed list
        int position = mPositionClickToApps;
        ComponentName cn = appInfo.componentName;
        if(cn != null) {
            String addValue = cn.flattenToShortString()+"#"+position+
                    "#"+ Utilities.getUserSerialNumber(mPowerSaveLauncher,appInfo.user);

            if(PlatformHelper.addAllowedAppInUltraSavingMode(mPowerSaveLauncher,addValue)) {
                Log.d(TAG, "add "+ addValue +" to allowed app list success!");
                updateSelectedPositionItem(position, appInfo);
                //update the bg workspace data list
                LauncherModel.updateWorkspaceBgDataList(position, appInfo);
                notifyAdapterDataChanged();
            }
        }
    }

    public List<Integer> getReadyToRemovePositions() {
        return mReadyToRemoveAppPositions;
    }

    public void notifyAdapterDataChanged() {
        if(mAdapter!= null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private int getWorkspaceSelectedItemCount() {
        int size = 0;
        for(ItemInfo itemInfo:mWorkspaceItem) {
            if(itemInfo != null) {
                size++;
            }
        }
        return size;
    }

    void synchronousUltraSavingIfNeeded() {
        List<String> allowedAppList = PlatformHelper.getAllowedAppListInUltraSavingMode(mPowerSaveLauncher);
        int count = getWorkspaceSelectedItemCount();
        if(allowedAppList.size() != count) {
            //clear the original list
            for(String value:allowedAppList) {
                if (PlatformHelper.delAllowedAppInUltraSavingMode(mPowerSaveLauncher, value)) {
                    Log.d(TAG, "synchronousUltraSaving, delAllowedApp "+value+ " success.");
                }
            }

            //add current selected workspace items
            for(int i = 0; i < mWorkspaceItem.size(); i++) {
                ItemInfo info = mWorkspaceItem.get(i);
                if(info != null) {
                    String addValue = info.componentName.flattenToShortString()+"#"+i
                            +"#"+Utilities.getUserSerialNumber(mPowerSaveLauncher,info.user);
                    if (PlatformHelper.addAllowedAppInUltraSavingMode(mPowerSaveLauncher, addValue)) {
                        Log.d(TAG, "synchronousUltraSaving, addAllowedApp " +addValue+" success.");
                    }
                }
            }

            List<String> list = PlatformHelper.getAllowedAppListInUltraSavingMode(mPowerSaveLauncher);
            Log.d(TAG, "allowedAppList after synchronous is "+ list.size() +
                    ", equal to workspace item size: "+count);
        }
    }
}
