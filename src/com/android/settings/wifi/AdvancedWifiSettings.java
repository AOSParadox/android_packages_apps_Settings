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

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.DhcpInfo;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppManager;
import android.net.NetworkScorerAppManager.NetworkScorerAppData;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.security.Credentials;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.AppListSwitchPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import java.util.Collection;

public class AdvancedWifiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AdvancedWifiSettings";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_FREQUENCY_BAND = "frequency_band";
    private static final String KEY_NOTIFY_OPEN_NETWORKS = "notify_open_networks";
    private static final String KEY_ENABLE_HS2 = "enable_hs2";
    private static final String KEY_ENABLE_HS2_REL1 = "enable_hs2_rel1";
    private static final String KEY_SLEEP_POLICY = "sleep_policy";
    private static final String KEY_INSTALL_CREDENTIALS = "install_credentials";
    private static final String KEY_WIFI_ASSISTANT = "wifi_assistant";
    private static final String KEY_WIFI_DIRECT = "wifi_direct";
    private static final String KEY_WPS_PUSH = "wps_push_button";
    private static final String KEY_WPS_PIN = "wps_pin_entry";

    private static final String KEY_CURRENT_GATEWAY = "current_gateway";
    private static final String KEY_CURRENT_NETMASK = "current_netmask";
    private static final int WIFI_HS2_ENABLED = 1;
    private static final int WIFI_HS2_DISABLED = 0;

    private static final String KEY_PRIORITY_SETTINGS = "wifi_priority_settings";

    private static final String KEY_AUTO_CONNECT_ENABLE = "auto_connect_type";
    private static final String WIFI_AUTO_CONNECT_TYPE = "wifi_auto_connect_type";
    private static final int AUTO_CONNECT_ENABLED = 0;
    private static final int AUTO_CONNECT_DISABLE = 1;
    private static final int AUTO_CONNECT_DEFAULT_VALUE = AUTO_CONNECT_ENABLED;

    private static final String KEY_CELLULAR_TO_WLAN = "cellular_to_wlan";
    private static final String CELLULAR_TO_WLAN_CONNECT_TYPE = "cellular_to_wlan_type";
    private static final int CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO = 0;
    private static final int CELLULAR_TO_WLAN_CONNECT_TYPE_MANUAL = 1;
    private static final int CELLULAR_TO_WLAN_CONNECT_TYPE_ASK = 2;
    private static final int CELLULAR_WLAN_DEFAULT_VALUE = CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO;

    private static final String KEY_CELLULAR_TO_WLAN_HINT = "cellular_to_wlan_hint";
    private static final String CELLULAR_TO_WLAN_HINT = "cellular_to_wlan_hint";

    private static final String KEY_WLAN_TO_CELLULAR_HINT = "wlan_to_cellular_hint";
    private static final String WLAN_TO_CELLULAR_HINT = "wlan_to_cellular_hint";

    private static final String KEY_CONNECT_NOTIFY = "notify_ap_connected";
    private static final String NOTIFY_USER_CONNECT = "notify_user_when_connect_cmcc";
    private static final String IS_USER_DISABLE_HS2_REL1 = "is_user_disable_hs2_rel1";

    private static final int NOTIFY_USER = 0;
    private static final int DO_NOT_NOTIFY_USER = -1;

    private CheckBoxPreference mAutoConnectEnablePref;
    private CheckBoxPreference mCellularToWlanHintPref;
    private SwitchPreference mEnableHs2Rel1;
    private ListPreference mCellularToWlanPref;
    private WifiManager mWifiManager;
    private NetworkScoreManager mNetworkScoreManager;
    private AppListSwitchPreference mWifiAssistantPreference;

    private IntentFilter mFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION) ||
                action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                refreshWifiInfo();
            }
        }
    };

    private ContentObserver mPasspointObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mEnableHs2Rel1 != null) {
                mEnableHs2Rel1.setChecked(Settings.Global.getInt(getContentResolver(),
                      Settings.Global.WIFI_HOTSPOT2_REL1_ENABLED, 0) == 1);
            }
        }
    };

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.WIFI_ADVANCED;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mNetworkScoreManager =
                (NetworkScoreManager) getSystemService(Context.NETWORK_SCORE_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
        getActivity().registerReceiver(mReceiver, mFilter);
        if(getResources().getBoolean(R.bool.config_wifi_hotspot2_enabled_Rel1)) {
            getActivity().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WIFI_HOTSPOT2_REL1_ENABLED), false,
                mPasspointObserver);
        }
        refreshWifiInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
        if(getResources().getBoolean(R.bool.config_wifi_hotspot2_enabled_Rel1)) {
            getActivity().getContentResolver().unregisterContentObserver(mPasspointObserver);
        }
    }

    private void initPreferences() {
        SwitchPreference notifyOpenNetworks =
            (SwitchPreference) findPreference(KEY_NOTIFY_OPEN_NETWORKS);
        notifyOpenNetworks.setChecked(Settings.Global.getInt(getContentResolver(),
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 0) == 1);
        notifyOpenNetworks.setEnabled(mWifiManager.isWifiEnabled());

        final Context context = getActivity();
        SwitchPreference enableHs2 =
            (SwitchPreference) findPreference(KEY_ENABLE_HS2);
        if (enableHs2 != null) {
            if (getResources().getBoolean(
                com.android.internal.R.bool.config_passpoint_setting_on)) {
                enableHs2.setChecked(Settings.Global.getInt(
                    getContentResolver(),
                    Settings.Global.WIFI_HOTSPOT2_ENABLED, WIFI_HS2_DISABLED) == WIFI_HS2_ENABLED);
            } else {
                getPreferenceScreen().removePreference(enableHs2);
            }
        }

        mEnableHs2Rel1 = (SwitchPreference) findPreference(KEY_ENABLE_HS2_REL1);
        mEnableHs2Rel1.setEnabled(mWifiManager.isWifiEnabled());
        if (mEnableHs2Rel1 != null && getResources().getBoolean(
                com.android.internal.R.bool.config_wifi_hotspot2_enabled) &&
            getResources().getBoolean(R.bool.config_wifi_hotspot2_enabled_Rel1)) {
            // Hotspot option should only be enabled when wifi is enabled.
            // If wifi is disabled, add network and remove network will not work
            mEnableHs2Rel1.setChecked(Settings.Global.getInt(getContentResolver(),
                      Settings.Global.WIFI_HOTSPOT2_REL1_ENABLED, 0) == 1);

        } else {
            getPreferenceScreen().removePreference(mEnableHs2Rel1);
        }

        Intent intent = new Intent(Credentials.INSTALL_AS_USER_ACTION);
        intent.setClassName("com.android.certinstaller",
                "com.android.certinstaller.CertInstallerMain");
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, android.os.Process.WIFI_UID);
        Preference pref = findPreference(KEY_INSTALL_CREDENTIALS);
        pref.setIntent(intent);

        mWifiAssistantPreference = (AppListSwitchPreference) findPreference(KEY_WIFI_ASSISTANT);
        Collection<NetworkScorerAppData> scorers =
                NetworkScorerAppManager.getAllValidScorers(context);
        if (UserHandle.myUserId() == UserHandle.USER_OWNER && !scorers.isEmpty()) {
            mWifiAssistantPreference.setOnPreferenceChangeListener(this);
            initWifiAssistantPreference(scorers);
        } else if (mWifiAssistantPreference != null) {
            getPreferenceScreen().removePreference(mWifiAssistantPreference);
        }

        Intent wifiDirectIntent = new Intent(context,
                com.android.settings.Settings.WifiP2pSettingsActivity.class);
        Preference wifiDirectPref = findPreference(KEY_WIFI_DIRECT);
        wifiDirectPref.setIntent(wifiDirectIntent);

        // WpsDialog: Create the dialog like WifiSettings does.
        Preference wpsPushPref = findPreference(KEY_WPS_PUSH);
        wpsPushPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference arg0) {
                    WpsFragment wpsFragment = new WpsFragment(WpsInfo.PBC);
                    wpsFragment.show(getFragmentManager(), KEY_WPS_PUSH);
                    return true;
                }
        });

        // WpsDialog: Create the dialog like WifiSettings does.
        Preference wpsPinPref = findPreference(KEY_WPS_PIN);
        wpsPinPref.setOnPreferenceClickListener(new OnPreferenceClickListener(){
                public boolean onPreferenceClick(Preference arg0) {
                    WpsFragment wpsFragment = new WpsFragment(WpsInfo.DISPLAY);
                    wpsFragment.show(getFragmentManager(), KEY_WPS_PIN);
                    return true;
                }
        });

        Preference prioritySettingPref = findPreference(KEY_PRIORITY_SETTINGS);
        if (prioritySettingPref != null) {
            if (!getResources().getBoolean(R.bool.set_wifi_priority)) {
                getPreferenceScreen().removePreference(prioritySettingPref);
            }
        } else {
            Log.d(TAG, "Fail to get priority pref...");
        }

        ListPreference frequencyPref = (ListPreference) findPreference(KEY_FREQUENCY_BAND);

        if (mWifiManager.isDualBandSupported()) {
            frequencyPref.setOnPreferenceChangeListener(this);
            int value = mWifiManager.getFrequencyBand();
            if (value != -1) {
                frequencyPref.setValue(String.valueOf(value));
                updateFrequencyBandSummary(frequencyPref, value);
            } else {
                Log.e(TAG, "Failed to fetch frequency band");
            }
        } else {
            if (frequencyPref != null) {
                // null if it has already been removed before resume
                getPreferenceScreen().removePreference(frequencyPref);
            }
        }

        ListPreference sleepPolicyPref = (ListPreference) findPreference(KEY_SLEEP_POLICY);
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(context)) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
            sleepPolicyPref.setOnPreferenceChangeListener(this);
            int value = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.WIFI_SLEEP_POLICY,
                    Settings.Global.WIFI_SLEEP_POLICY_NEVER);
            String stringValue = String.valueOf(value);
            sleepPolicyPref.setValue(stringValue);
            updateSleepPolicySummary(sleepPolicyPref, stringValue);
        }

        mAutoConnectEnablePref =
                (CheckBoxPreference) findPreference(KEY_AUTO_CONNECT_ENABLE);
        if (mAutoConnectEnablePref != null) {
            if (getResources().getBoolean(R.bool.config_auto_connect_wifi_enabled)) {
                mAutoConnectEnablePref.setChecked(isAutoConnectEnabled());
                mAutoConnectEnablePref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(mAutoConnectEnablePref);
            }
        }

        mCellularToWlanPref =
                (ListPreference) findPreference(KEY_CELLULAR_TO_WLAN);
        if (mCellularToWlanPref != null) {
            if (getResources().getBoolean(R.bool.cell_to_wifi)) {
                int value = getCellularToWlanValue();
                mCellularToWlanPref.setValue(String.valueOf(value));
                updateCellToWlanSummary(mCellularToWlanPref, value);
                mCellularToWlanPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(mCellularToWlanPref);
            }
        }

        CheckBoxPreference wlanToCellularHintPref =
                (CheckBoxPreference) findPreference(KEY_WLAN_TO_CELLULAR_HINT);
        if (wlanToCellularHintPref != null) {
            if (getResources().getBoolean(R.bool.wifi_to_cell)) {
                wlanToCellularHintPref.setChecked(isWlanToCellHintEnable());
                wlanToCellularHintPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(wlanToCellularHintPref);
            }
        }

        CheckBoxPreference notifyConnectedApPref =
                (CheckBoxPreference) findPreference(KEY_CONNECT_NOTIFY);
        if (notifyConnectedApPref != null) {
            if (getResources().getBoolean(R.bool.connect_to_cmcc_notify)) {
                notifyConnectedApPref.setChecked(ifNotifyConnect());
                notifyConnectedApPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(notifyConnectedApPref);
            }
        }

        mCellularToWlanHintPref = (CheckBoxPreference) findPreference(KEY_CELLULAR_TO_WLAN_HINT);
        if (mCellularToWlanHintPref != null) {
            if (getResources().getBoolean(R.bool.cellular_to_wlan_hint)) {
                mCellularToWlanHintPref.setChecked(isCellularToWlanHintEnable());
                mCellularToWlanHintPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(mCellularToWlanHintPref);
            }
        }
    }

    private boolean isCellularToWlanHintEnable() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                CELLULAR_TO_WLAN_HINT, NOTIFY_USER) == NOTIFY_USER;
    }

    private boolean isWlanToCellHintEnable() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                WLAN_TO_CELLULAR_HINT, NOTIFY_USER) == NOTIFY_USER;
    }

    private void setWlanToCellularHintEnable(boolean enable) {
        if (enable) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    WLAN_TO_CELLULAR_HINT, NOTIFY_USER);
        } else {
            Settings.System.putInt(getActivity().getContentResolver(),
                    WLAN_TO_CELLULAR_HINT, DO_NOT_NOTIFY_USER);
        }
    }

    private boolean ifNotifyConnect() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                NOTIFY_USER_CONNECT, NOTIFY_USER) == NOTIFY_USER;
    }

    private boolean isAutoConnectEnabled() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                WIFI_AUTO_CONNECT_TYPE, AUTO_CONNECT_ENABLED) == AUTO_CONNECT_ENABLED;
    }

    private void setAutoConnectTypeEnabled(boolean enable) {
        if (enable) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    WIFI_AUTO_CONNECT_TYPE, AUTO_CONNECT_ENABLED);
        } else {
            Settings.System.putInt(getActivity().getContentResolver(),
                    WIFI_AUTO_CONNECT_TYPE, AUTO_CONNECT_DISABLE);
        }
    }

    private int getCellularToWlanValue() {
        if (isAutoConnectEnabled()) {
            return CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO;
        } else {
            return Settings.System.getInt(getContentResolver(), CELLULAR_TO_WLAN_CONNECT_TYPE,
                    CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO);
        }
    }

    private void initWifiAssistantPreference(Collection<NetworkScorerAppData> scorers) {
        int count = scorers.size();
        String[] packageNames = new String[count];
        int i = 0;
        for (NetworkScorerAppData scorer : scorers) {
            packageNames[i] = scorer.mPackageName;
            i++;
        }
        mWifiAssistantPreference.setPackageNames(packageNames,
                mNetworkScoreManager.getActiveScorerPackage());
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            final int summaryArrayResId = Utils.isWifiOnly(getActivity()) ?
                    R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries;
            String[] summaries = getResources().getStringArray(summaryArrayResId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        sleepPolicyPref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        sleepPolicyPref.setSummary("");
        Log.e(TAG, "Invalid sleep policy value: " + value);
    }

    private void updateCellToWlanSummary(Preference preference, int index) {
        String[] summaries = getResources().getStringArray(R.array.cellcular2wifi_entries);
        preference.setSummary(summaries[index]);
    }

    private void updateFrequencyBandSummary(Preference frequencyBandPref, int index) {
        String[] summaries = getResources().getStringArray(R.array.wifi_frequency_band_entries);
        frequencyBandPref.setSummary(summaries[index]);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        String key = preference.getKey();

        if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_ENABLE_HS2.equals(key)) {
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_HOTSPOT2_ENABLED,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_ENABLE_HS2_REL1.equals(key)) {
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_HOTSPOT2_REL1_ENABLED,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
            Global.putInt(getContentResolver(),
                    IS_USER_DISABLE_HS2_REL1,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
            Intent i = new Intent("com.android.settings.action.USER_TAP_PASSPOINT");
            getActivity().sendBroadcast(i);
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getActivity();
        String key = preference.getKey();

        if (KEY_FREQUENCY_BAND.equals(key)) {
            try {
                int value = Integer.parseInt((String) newValue);
                mWifiManager.setFrequencyBand(value, true);
                updateFrequencyBandSummary(preference, value);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_frequency_band_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (KEY_WIFI_ASSISTANT.equals(key)) {
            NetworkScorerAppData wifiAssistant =
                    NetworkScorerAppManager.getScorer(context, (String) newValue);
            if (wifiAssistant == null) {
                mNetworkScoreManager.setActiveScorer(null);
                return true;
            }

            Intent intent = new Intent();
            if (wifiAssistant.mConfigurationActivityClassName != null) {
                // App has a custom configuration activity; launch that.
                // This custom activity will be responsible for launching the system
                // dialog.
                intent.setClassName(wifiAssistant.mPackageName,
                        wifiAssistant.mConfigurationActivityClassName);
            } else {
                // Fall back on the system dialog.
                intent.setAction(NetworkScoreManager.ACTION_CHANGE_ACTIVE);
                intent.putExtra(NetworkScoreManager.EXTRA_PACKAGE_NAME,
                        wifiAssistant.mPackageName);
            }

            startActivity(intent);
            // Don't update the preference widget state until the child activity returns.
            // It will be updated in onResume after the activity finishes.
            return false;
        }

        if (KEY_SLEEP_POLICY.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.Global.putInt(getContentResolver(), Settings.Global.WIFI_SLEEP_POLICY,
                        Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (KEY_WLAN_TO_CELLULAR_HINT.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            setWlanToCellularHintEnable(checked);
        }

        if (KEY_AUTO_CONNECT_ENABLE.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            setAutoConnectTypeEnabled(checked);
            updateCellularToWifiPrefs(checked);
            if (!checked) {
                updateCellularToWlanHintPref(true);
            }
        }

        if (KEY_CELLULAR_TO_WLAN.equals(key)) {
            int value = Integer.parseInt(((String) newValue));
            setCellToWlanType(value);
            mCellularToWlanPref.setValue(String.valueOf(value));
            updateCellToWlanSummary(mCellularToWlanPref, value);
            updateAutoConnectPref(value == CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO);
            if (CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO != value) {
                updateCellularToWlanHintPref(true);
            }
        }

        if (KEY_CONNECT_NOTIFY.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            setApConnectedNotify(checked);
        }

        if (KEY_CELLULAR_TO_WLAN_HINT.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            setCellularToWlanHintEnable(checked);
            if(!checked) {
                Toast.makeText(getActivity(),
                        getResources().getString(R.string.cellular_to_wlan_hint_toast),
                        Toast.LENGTH_LONG).show();
            }
        }

        return true;
    }

    private void updateCellularToWlanHintPref(boolean enable) {
        mCellularToWlanHintPref.setChecked(enable);
        setCellularToWlanHintEnable(enable);
    }

    private void setCellularToWlanHintEnable(boolean needNotify) {
        if (needNotify) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    CELLULAR_TO_WLAN_HINT, NOTIFY_USER);
        } else {
            Settings.System.putInt(getActivity().getContentResolver(),
                    CELLULAR_TO_WLAN_HINT, DO_NOT_NOTIFY_USER);
        }
    }

    private void setApConnectedNotify(boolean needNotify) {
        if (needNotify) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    NOTIFY_USER_CONNECT, NOTIFY_USER);
        } else {
            Settings.System.putInt(getActivity().getContentResolver(),
                    NOTIFY_USER_CONNECT, DO_NOT_NOTIFY_USER);
        }
    }

    private void setCellToWlanType(int value) {
        try {
            Settings.System.putInt(getContentResolver(), CELLULAR_TO_WLAN_CONNECT_TYPE,
                    value);
        } catch (NumberFormatException e) {
            Toast.makeText(getActivity(), R.string.wifi_setting_connect_type_error,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCellularToWifiPrefs(boolean isAutoEnabled) {
        if (!isAutoEnabled) {
            updateCellularToWlanHintPref(true);
        }
        int value = isAutoEnabled ? CELLULAR_TO_WLAN_CONNECT_TYPE_AUTO : CELLULAR_TO_WLAN_CONNECT_TYPE_MANUAL;
        Settings.System.putInt(getContentResolver(), CELLULAR_TO_WLAN_CONNECT_TYPE, value);
        mCellularToWlanPref.setValue(String.valueOf(value));
        updateCellToWlanSummary(mCellularToWlanPref, value);
    }

    private void updateAutoConnectPref(boolean isAutoMode) {
        setAutoConnectTypeEnabled(isAutoMode);
        mAutoConnectEnablePref.setChecked(isAutoMode);
    }

    private void refreshWifiInfo() {
        final Context context = getActivity();
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : context.getString(R.string.status_unavailable));
        wifiMacAddressPref.setSelectable(false);

        Preference wifiIpAddressPref = findPreference(KEY_CURRENT_IP_ADDRESS);
        String ipAddress = Utils.getWifiIpAddresses(context);
        wifiIpAddressPref.setSummary(ipAddress == null ?
                context.getString(R.string.status_unavailable) : ipAddress);
        wifiIpAddressPref.setSelectable(false);
        Preference wifiGatewayPref = findPreference(KEY_CURRENT_GATEWAY);
        String gateway = null;
        Preference wifiNetmaskPref = findPreference(KEY_CURRENT_NETMASK);
        String netmask = null;
        if (getResources().getBoolean(R.bool.config_netinfo)) {
            DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
            if (wifiInfo != null) {
                if (dhcpInfo != null) {
                    gateway = Formatter.formatIpAddress(dhcpInfo.gateway);
                    netmask = Formatter.formatIpAddress(dhcpInfo.netmask);
                }
            }
            if (wifiGatewayPref != null) {
                wifiGatewayPref.setSummary((gateway == null || dhcpInfo.gateway == 0) ?
                        getString(R.string.status_unavailable) : gateway);
            }
            if (wifiNetmaskPref != null) {
                wifiNetmaskPref.setSummary((netmask == null || dhcpInfo.netmask == 0) ?
                        getString(R.string.status_unavailable) : netmask);
            }
        } else {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen != null) {
                if (wifiGatewayPref != null) {
                    screen.removePreference(wifiGatewayPref);
                }
                if (wifiNetmaskPref != null) {
                    screen.removePreference(wifiNetmaskPref);
                }
            }
        }
    }

    /* Wrapper class for the WPS dialog to properly handle life cycle events like rotation. */
    public static class WpsFragment extends DialogFragment {
        private static int mWpsSetup;

        // Public default constructor is required for rotation.
        public WpsFragment() {
            super();
        }

        public WpsFragment(int wpsSetup) {
            super();
            mWpsSetup = wpsSetup;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new WpsDialog(getActivity(), mWpsSetup);
        }
    }

}
