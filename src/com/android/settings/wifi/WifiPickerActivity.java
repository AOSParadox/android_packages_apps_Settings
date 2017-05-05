/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.settings.wifi;

import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.view.WindowManager;

import com.android.settings.ButtonBarHandler;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.wifi.p2p.WifiP2pSettings;


public class WifiPickerActivity extends SettingsActivity implements ButtonBarHandler {
    private StatusBarManager mStatusBarManager;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (WifiSettings.isDeviceSubsidyLocked(this)) {
            WindowManager.LayoutParams lp = this
                .getWindow().getAttributes();
            lp.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

            mStatusBarManager = (StatusBarManager)
                this.getSystemService(Context.STATUS_BAR_SERVICE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (null != mStatusBarManager && WifiSettings.isDeviceSubsidyLocked(this)) {
            mStatusBarManager.disable(StatusBarManager.DISABLE_EXPAND);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (null != mStatusBarManager && WifiSettings.isDeviceSubsidyLocked(this)) {
            mStatusBarManager.disable(StatusBarManager.DISABLE_NONE);
        }
    }

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        if (!modIntent.hasExtra(EXTRA_SHOW_FRAGMENT)) {
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT, getWifiSettingsClass().getName());
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE_RESID, R.string.wifi_select_network);
        }
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (WifiSettings.class.getName().equals(fragmentName)
                || WifiP2pSettings.class.getName().equals(fragmentName)
                || SavedAccessPointsWifiSettings.class.getName().equals(fragmentName)
                || AdvancedWifiSettings.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends PreferenceFragment> getWifiSettingsClass() {
        return WifiSettings.class;
    }
}
