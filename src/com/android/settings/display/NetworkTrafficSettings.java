/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.display;

import android.content.ContentResolver;
import android.os.Bundle;
import android.support.v7.preference.DropDownPreference;
import android.support.v7.preference.Preference;

import com.android.settings.cyanogenmod.SecureSettingSwitchPreference;


public class NetworkTrafficSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "NetworkTrafficSettings";

    private DropDownPreference mNetTrafficMode;
    private SecureSettingSwitchPreference mNetTrafficAutohide;
    private NetworkTrafficThresholdSeekBarPreference mNetTrafficAutohideThreshold;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.network_traffic_settings);
        final ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficMode = (DropDownPreference)
                findPreference(Settings.Secure.NETWORK_TRAFFIC_MODE);
        mNetTrafficMode.setOnPreferenceChangeListener(this);
        int mode = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_MODE, 0);
        mNetTrafficMode.setValue(String.valueOf(mode));

        mNetTrafficAutohide = (SecureSettingSwitchPreference)
                findPreference(Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE);
        mNetTrafficAutohide.setOnPreferenceChangeListener(this);

        mNetTrafficAutohideThreshold = (NetworkTrafficThresholdSeekBarPreference)
                findPreference(Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD);
        int netTrafficAutohideThreshold = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 10);
        mNetTrafficAutohideThreshold.setThreshold(netTrafficAutohideThreshold);
        mNetTrafficAutohideThreshold.setOnPreferenceChangeListener(this);

        updateEnabledStates(null, null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mNetTrafficMode) {
            int intState = Integer.valueOf((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_MODE, intState);
            updateEnabledStates(intState, null);
            return true;
        } else if (preference == mNetTrafficAutohide) {
            updateEnabledStates(null, (Boolean) newValue);
            return true;
        } else if (preference == mNetTrafficAutohideThreshold) {
            int threshold = (Integer) newValue;
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, threshold);
            return true;
        }
        return false;
    }

    private void updateEnabledStates(Integer mode, Boolean autoHide) {
        boolean disabled = mode == null ? "0".equals(mNetTrafficMode.getValue()) : mode == 0;
        boolean autoHideEnabled = autoHide == null ? mNetTrafficAutohide.isChecked() : autoHide;

        mNetTrafficAutohide.setEnabled(!disabled);
        mNetTrafficAutohideThreshold.setEnabled(!disabled && autoHideEnabled);
    }
}
