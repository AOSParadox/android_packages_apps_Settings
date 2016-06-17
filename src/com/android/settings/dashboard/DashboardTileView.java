/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.dashboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.ProfileSelectDialog;
import com.android.settings.R;
import com.android.settings.Utils;

public class DashboardTileView extends FrameLayout implements View.OnClickListener {

    private static final String SYSTEM_UPDATE_SETTINGS = "System updates";
    private static final int DEFAULT_COL_SPAN = 1;

    private ImageView mImageView;
    private TextView mTitleTextView;
    private TextView mStatusTextView;
    private Switch mSwitch;
    private View mDivider;

    private int mColSpan = DEFAULT_COL_SPAN;

    private DashboardTile mTile;

    public DashboardTileView(Context context, boolean hasSwitch) {
        this(context, hasSwitch, null);
    }

    public DashboardTileView(Context context, boolean hasSwitch, AttributeSet attrs) {
        super(context, attrs);
        if (hasSwitch) {
            final View view = LayoutInflater.from(context).inflate(
                    R.layout.dashboard_tile_switch, this);

            mImageView = (ImageView) view.findViewById(R.id.icon);
            mTitleTextView = (TextView) view.findViewById(R.id.title);
            mSwitch = (Switch) view.findViewById(R.id.switchWidget);
            mDivider = view.findViewById(R.id.tile_divider);

        } else {
            final View view = LayoutInflater.from(context).inflate(R.layout.dashboard_tile, this);

            mImageView = (ImageView) view.findViewById(R.id.icon);
            mTitleTextView = (TextView) view.findViewById(R.id.title);
            mStatusTextView = (TextView) view.findViewById(R.id.status);
            mDivider = view.findViewById(R.id.tile_divider);
        }

        setOnClickListener(this);
        setBackgroundResource(R.drawable.dashboard_tile_background);
        setFocusable(true);
    }

    public Switch getSwitch() {
        return mSwitch;
    }

    public TextView getTitleTextView() {
        return mTitleTextView;
    }

    public TextView getStatusTextView() {
        return mStatusTextView;
    }

    public ImageView getImageView() {
        return mImageView;
    }

    public void setTile(DashboardTile tile) {
        mTile = tile;
    }

    public void setDividerVisibility(boolean visible) {
        mDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void setColumnSpan(int span) {
        mColSpan = span;
    }

    int getColumnSpan() {
        return mColSpan;
    }

    @Override
    public void onClick(View v) {
        if (mTile.fragment != null) {
            Utils.startWithFragment(getContext(), mTile.fragment, mTile.fragmentArguments, null, 0,
                    mTile.titleRes, mTile.getTitle(getResources()));
        } else if (mTile.intent != null) {
            int numUserHandles = mTile.userHandle.size();
            if (numUserHandles > 1) {
                ProfileSelectDialog.show(((Activity) getContext()).getFragmentManager(), mTile);
            } else if (numUserHandles == 1) {
                getContext().startActivityAsUser(mTile.intent, mTile.userHandle.get(0));
            } else if (mTile.getTitle(getResources()).toString().equals(SYSTEM_UPDATE_SETTINGS)){
                CarrierConfigManager configManager =
                        (CarrierConfigManager) getContext().getSystemService(
                            Context.CARRIER_CONFIG_SERVICE);
                PersistableBundle b = configManager.getConfig();
                if (b.getBoolean(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL)) {
                    Utils.ciActionOnSysUpdate(getContext(),b);
                }
                getContext().startActivity(mTile.intent);
            } else {
                getContext().startActivity(mTile.intent);
            }
        } else if (mTile.getTitle(getResources()).equals(
                getResources().getString(R.string.lte_4g_settings_title))) {
            if (null != mSwitch) {
                mSwitch.setChecked(!mSwitch.isChecked());
            }
        } else if (mTile.getTitle(getResources()).equals(
                getResources().getString(R.string.profiles_settings_title))) {
            Intent i = new Intent();
            i.setAction("com.codeaurora.STARTPROFILE");
            i.setPackage("com.android.profile");
            getContext().startActivity(i);
        }
    }
}
