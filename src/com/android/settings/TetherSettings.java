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

package com.android.settings;

import static com.android.settingslib.TetherUtil.TETHERING_INVALID;
import static com.android.settingslib.TetherUtil.TETHERING_WIFI;
import static com.android.settingslib.TetherUtil.TETHERING_USB;
import static com.android.settingslib.TetherUtil.TETHERING_BLUETOOTH;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.wifi.WifiApDialog;
import com.android.settings.wifi.WifiApEnabler;
import com.android.settingslib.TetherUtil;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends SettingsPreferenceFragment
        implements DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener {
    private static final String TAG = "TetherSettings";

    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private static final String ENABLE_WIFI_AP_EXT = "enable_wifi_ap_ext";
    private static final String ENABLE_BLUETOOTH_TETHERING = "enable_bluetooth_tethering";
    private static final String TETHER_CHOICE = "TETHER_TYPE";
    private static final String TETHERING_HELP = "tethering_help";
    private static final String ACTION_EXTRA = "choice";
    private static final String ACTION_EXTRA_VALUE = "value";
    private static final String SHAREPREFERENCE_DEFAULT_WIFI = "def_wifiap_set";
    private static final String SHAREPREFERENCE_FIFE_NAME = "MY_PERFS";
    private static final String ACTION_HOTSPOT_CONFIGURE = "Hotspot_PreConfigure";
    private static final String ACTION_HOTSPOT_POST_CONFIGURE = "Hotspot_PostConfigure";
    private static final String CONFIGURE_RESULT = "PreConfigure_result";
    private static final String ACTION_HOTSPOT_CONFIGURE_RRSPONSE =
            "Hotspot_PreConfigure_Response";

    private static final int DIALOG_AP_SETTINGS = 1;

    private SwitchPreference mUsbTether;

    private WifiApEnabler mWifiApEnabler;
    private Preference mEnableWifiAp;
    private PreferenceScreen mTetherHelp;

    private SwitchPreference mBluetoothTether;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;

    private String[] mWifiRegexs;

    private String[] mBluetoothRegexs;
    private AtomicReference<BluetoothPan> mBluetoothPan = new AtomicReference<BluetoothPan>();

    private static final String WIFI_AP_SSID_AND_SECURITY = "wifi_ap_ssid_and_security";
    private static final int CONFIG_SUBTEXT = R.string.wifi_tether_configure_subtext;

    private String[] mSecurityType;
    private Preference mCreateNetwork;

    private WifiApDialog mDialog;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConfig = null;
    private UserManager mUm;

    private boolean mUsbConnected;
    private boolean mMassStorageActive;

    private boolean mBluetoothEnableForTether;

    /* One of INVALID, WIFI_TETHERING, USB_TETHERING or BLUETOOTH_TETHERING */
    private int mTetherChoice = TETHERING_INVALID;

    /* Stores the package name and the class name of the provisioning app */
    private String[] mProvisionApp;
    private static final int PROVISION_REQUEST = 0;

    private boolean mUnavailable;
    private BroadcastReceiver mConfigureReceiver;

    /* Record the wifi status before usb tether is on */
    private boolean mUsbEnable = false;
    private WifiManager mWifiStatusManager;
    private boolean isWifiEnabled = false;

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.TETHER;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if(icicle != null) {
            mTetherChoice = icicle.getInt(TETHER_CHOICE);
        }
        addPreferencesFromResource(R.xml.tether_prefs);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)
                || UserHandle.myUserId() != UserHandle.USER_OWNER) {
            mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }

        final Activity activity = getActivity();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(activity.getApplicationContext(), mProfileServiceListener,
                    BluetoothProfile.PAN);
        }

        mCreateNetwork = findPreference(WIFI_AP_SSID_AND_SECURITY);

        boolean enableWifiApSettingsExt = getResources().getBoolean(R.bool.show_wifi_hotspot_settings);
        boolean isWifiApEnabled = getResources().getBoolean(R.bool.hide_wifi_hotspot);
        checkDefaultValue(getActivity());
        if (enableWifiApSettingsExt) {
            mEnableWifiAp =
                    (HotspotPreference) findPreference(ENABLE_WIFI_AP_EXT);
            getPreferenceScreen().removePreference(findPreference(ENABLE_WIFI_AP));
            getPreferenceScreen().removePreference(mCreateNetwork);
            Intent intent = new Intent();
            intent.setAction("com.qti.ap.settings");
            intent.setPackage("com.qualcomm.qti.extsettings");
            mEnableWifiAp.setIntent(intent);
        } else {
            mEnableWifiAp =
                    (SwitchPreference) findPreference(ENABLE_WIFI_AP);
            getPreferenceScreen().removePreference(findPreference(ENABLE_WIFI_AP_EXT));
        }
        if (isWifiApEnabled) {
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(mCreateNetwork);
        }

        if (getResources().getBoolean(
                R.bool.config_regional_hotspot_tether_help_enable)) {
            mTetherHelp = (PreferenceScreen) findPreference(TETHERING_HELP);
        } else {
            getPreferenceScreen().removePreference(findPreference(TETHERING_HELP));
        }

        mUsbTether = (SwitchPreference) findPreference(USB_TETHER_SETTINGS);
        mBluetoothTether = (SwitchPreference) findPreference(ENABLE_BLUETOOTH_TETHERING);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        mUsbRegexs = cm.getTetherableUsbRegexs();
        mWifiRegexs = cm.getTetherableWifiRegexs();
        mBluetoothRegexs = cm.getTetherableBluetoothRegexs();

        final boolean usbAvailable = mUsbRegexs.length != 0;
        final boolean wifiAvailable = mWifiRegexs.length != 0;
        final boolean bluetoothAvailable = mBluetoothRegexs.length != 0;

        if (!usbAvailable || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(mUsbTether);
        }

        if (wifiAvailable && !Utils.isMonkeyRunning()) {
            mWifiApEnabler = new WifiApEnabler(activity, mEnableWifiAp);
            initWifiTethering();
        } else {
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(mCreateNetwork);
        }

        if (!bluetoothAvailable) {
            getPreferenceScreen().removePreference(mBluetoothTether);
        } else {
            BluetoothPan pan = mBluetoothPan.get();
            if (pan != null && pan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
            } else {
                mBluetoothTether.setChecked(false);
            }
        }

        final boolean configShowBluetoothAndHelpMenu = getResources().getBoolean(
                R.bool.config_show_bluetooth_menu);
        if (configShowBluetoothAndHelpMenu && mBluetoothTether != null) {
            getPreferenceScreen().removePreference(mBluetoothTether);
        }

        mProvisionApp = getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);

        mUsbEnable = getResources().getBoolean(R.bool.config_usb_line_enable);
        mWifiStatusManager= (WifiManager) getActivity().getSystemService(
                Context.WIFI_SERVICE);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(TETHER_CHOICE, mTetherChoice);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void initWifiTethering() {
        final Activity activity = getActivity();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiConfig = mWifiManager.getWifiApConfiguration();
        mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);
        if (mCreateNetwork == null) {
            return;
        }
        if (mWifiConfig == null) {
            final String s = activity.getString(
                    com.android.internal.R.string.wifi_tether_configure_ssid_default);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    s, mSecurityType[WifiApDialog.OPEN_INDEX]));
        } else {
            int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    mWifiConfig.SSID,
                    mSecurityType[index]));
        }
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothPan.set((BluetoothPan) proxy);
        }
        public void onServiceDisconnected(int profile) {
            mBluetoothPan.set(null);
        }
    };

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_AP_SETTINGS) {
            final Activity activity = getActivity();
            mDialog = new WifiApDialog(activity, this, mWifiConfig);
            return mDialog;
        }

        return null;
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateState(available.toArray(new String[available.size()]),
                        active.toArray(new String[active.size()]),
                        errored.toArray(new String[errored.size()]));
            } else if (action.equals(Intent.ACTION_MEDIA_SHARED)) {
                mMassStorageActive = true;
                updateState();
            } else if (action.equals(Intent.ACTION_MEDIA_UNSHARED)) {
                mMassStorageActive = false;
                updateState();
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                mMassStorageActive = Environment.MEDIA_SHARED.equals(
                        Environment.getExternalStorageState());
                boolean usbAvailable = mUsbConnected && !mMassStorageActive;
                if(!usbAvailable && mUsbEnable && isWifiEnabled){
                    postTurnOn(getActivity(), TETHERING_USB, false);
                }
                updateState();
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (mBluetoothEnableForTether) {
                    switch (intent
                            .getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        case BluetoothAdapter.STATE_ON:
                            BluetoothPan bluetoothPan = mBluetoothPan.get();
                            if (bluetoothPan != null) {
                                bluetoothPan.setBluetoothTethering(true);
                                mBluetoothEnableForTether = false;
                            }
                            break;

                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.ERROR:
                            mBluetoothEnableForTether = false;
                            break;

                        default:
                            // ignore transition states
                    }
                }
                updateState();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mUnavailable) {
            TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
            getListView().setEmptyView(emptyView);
            if (emptyView != null) {
                emptyView.setText(R.string.tethering_settings_not_available);
            }
            return;
        }

        final Activity activity = getActivity();

        mMassStorageActive = Environment.MEDIA_SHARED.equals(Environment.getExternalStorageState());
        mTetherChangeReceiver = new TetherChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        Intent intent = activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        if (intent != null) mTetherChangeReceiver.onReceive(activity, intent);
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(this);
            mWifiApEnabler.resume();
        }

        updateState();
        registerConfigureReceiver(getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mUnavailable) {
            return;
        }
        getActivity().unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(null);
            mWifiApEnabler.pause();
        }
        unRegisterConfigureReceiver();
    }

    private void updateState() {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        String[] available = cm.getTetherableIfaces();
        String[] tethered = cm.getTetheredIfaces();
        String[] errored = cm.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }

    private void updateState(String[] available, String[] tethered,
            String[] errored) {
        updateUsbState(available, tethered, errored);
        updateBluetoothState(available, tethered, errored);
    }


    private void updateUsbState(String[] available, String[] tethered,
            String[] errored) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean usbAvailable = mUsbConnected && !mMassStorageActive;
        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        for (String s : available) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        usbError = cm.getLastTetherError(s);
                    }
                }
            }
        }
        boolean usbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbTethered = true;
            }
        }
        boolean usbErrored = false;
        for (String s: errored) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbErrored = true;
            }
        }

        if (usbTethered) {
            mUsbTether.setSummary(R.string.usb_tethering_active_subtext);
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(true);
        } else if (usbAvailable) {
            if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
            } else {
                mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            }
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(false);
        } else if (usbErrored) {
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
        } else if (mMassStorageActive) {
            mUsbTether.setSummary(R.string.usb_tethering_storage_active_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
        } else {
            mUsbTether.setSummary(R.string.usb_tethering_unavailable_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
        }
    }

    private void updateBluetoothState(String[] available, String[] tethered,
            String[] errored) {
        boolean bluetoothErrored = false;
        for (String s: errored) {
            for (String regex : mBluetoothRegexs) {
                if (s.matches(regex)) bluetoothErrored = true;
            }
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null)
            return;
        int btState = adapter.getState();
        if (btState == BluetoothAdapter.STATE_TURNING_OFF) {
            mBluetoothTether.setEnabled(false);
            mBluetoothTether.setSummary(R.string.bluetooth_turning_off);
        } else if (btState == BluetoothAdapter.STATE_TURNING_ON) {
            mBluetoothTether.setEnabled(false);
            mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
        } else {
            BluetoothPan bluetoothPan = mBluetoothPan.get();
            if (btState == BluetoothAdapter.STATE_ON && bluetoothPan != null &&
                    bluetoothPan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
                mBluetoothTether.setEnabled(true);
                int bluetoothTethered = bluetoothPan.getConnectedDevices().size();
                if (bluetoothTethered > 1) {
                    String summary = getString(
                            R.string.bluetooth_tethering_devices_connected_subtext,
                            bluetoothTethered);
                    mBluetoothTether.setSummary(summary);
                } else if (bluetoothTethered == 1) {
                    mBluetoothTether.setSummary(
                            R.string.bluetooth_tethering_device_connected_subtext);
                } else if (bluetoothErrored) {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_available_subtext);
                }
            } else {
                mBluetoothTether.setEnabled(true);
                mBluetoothTether.setChecked(false);
                mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        boolean enable = (Boolean) value;

        if (enable) {
            startProvisioningIfNecessary(TETHERING_WIFI);
        } else {
            if (TetherUtil.isProvisioningNeeded(getActivity())) {
                TetherService.cancelRecheckAlarmIfNecessary(getActivity(), TETHERING_WIFI);
            }
            mWifiApEnabler.setSoftapEnabled(false);
        }
        return false;
    }

    public static boolean isProvisioningNeededButUnavailable(Context context) {
        return (TetherUtil.isProvisioningNeeded(context)
                && !isIntentAvailable(context));
    }

    private static boolean isIntentAvailable(Context context) {
        String[] provisionApp = context.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(provisionApp[0], provisionApp[1]);

        return (packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).size() > 0);
    }

    private void startProvisioningIfNecessary(int choice) {
        mTetherChoice = choice;
        if (TetherUtil.isProvisioningNeeded(getActivity())) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mProvisionApp[0], mProvisionApp[1]);
            intent.putExtra(TETHER_CHOICE, mTetherChoice);
            startActivityForResult(intent, PROVISION_REQUEST);
        } else {
            if (preTurnOn(getActivity(),choice)) return;
            startTethering();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PROVISION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                TetherService.scheduleRecheckAlarm(getActivity(), mTetherChoice);
                startTethering();
            } else {
                //BT and USB need switch turned off on failure
                //Wifi tethering is never turned on until afterwards
                switch (mTetherChoice) {
                    case TETHERING_BLUETOOTH:
                        mBluetoothTether.setChecked(false);
                        break;
                    case TETHERING_USB:
                        mUsbTether.setChecked(false);
                        break;
                }
                mTetherChoice = TETHERING_INVALID;
            }
        }
    }

    private void startTethering() {
        switch (mTetherChoice) {
            case TETHERING_WIFI:
                mWifiApEnabler.setSoftapEnabled(true);
                break;
            case TETHERING_BLUETOOTH:
                // turn on Bluetooth first
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    mBluetoothEnableForTether = true;
                    adapter.enable();
                    mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
                    mBluetoothTether.setEnabled(false);
                } else {
                    BluetoothPan bluetoothPan = mBluetoothPan.get();
                    if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(true);
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_available_subtext);
                }
                break;
            case TETHERING_USB:
                setUsbTethering(true);
                break;
            default:
                //should not happen
                break;
        }
    }

    private void setUsbTethering(boolean enabled) {
        ConnectivityManager cm =
            (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mUsbTether.setChecked(false);
        if (cm.setUsbTethering(enabled) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            return;
        }
        postTurnOn(getActivity(), mTetherChoice, enabled);
        mUsbTether.setSummary("");
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        if (preference == mUsbTether) {
            boolean newState = mUsbTether.isChecked();
            //get the wifi status
            isWifiEnabled = mWifiStatusManager.isWifiEnabled();
            IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_STATE);
            getActivity().registerReceiver(mTetherChangeReceiver, filter);

            if (newState) {
                startProvisioningIfNecessary(TETHERING_USB);
            } else {
                if (TetherUtil.isProvisioningNeeded(getActivity())) {
                    TetherService.cancelRecheckAlarmIfNecessary(getActivity(), TETHERING_USB);
                }
                setUsbTethering(newState);
            }
        } else if (preference == mBluetoothTether) {
            boolean bluetoothTetherState = mBluetoothTether.isChecked();

            if (bluetoothTetherState) {
                startProvisioningIfNecessary(TETHERING_BLUETOOTH);
            } else {
                if (TetherUtil.isProvisioningNeeded(getActivity())) {
                    TetherService.cancelRecheckAlarmIfNecessary(getActivity(), TETHERING_BLUETOOTH);
                }
                boolean errored = false;

                String [] tethered = cm.getTetheredIfaces();
                String bluetoothIface = findIface(tethered, mBluetoothRegexs);
                if (bluetoothIface != null &&
                        cm.untether(bluetoothIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    errored = true;
                }

                BluetoothPan bluetoothPan = mBluetoothPan.get();
                if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(false);
                if (errored) {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
                }
            }
        } else if (preference == mCreateNetwork) {
            showDialog(DIALOG_AP_SETTINGS);
        } else if (getResources().getBoolean(
                R.bool.config_regional_hotspot_tether_help_enable)
                && preference == mTetherHelp) {
            AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
            alert.setTitle(R.string.tethering_help_dialog_title);
            alert.setMessage(R.string.tethering_help_dialog_text);
            alert.setPositiveButton(R.string.okay, null);
            alert.show();
        }

        return super.onPreferenceTreeClick(screen, preference);
    }

    private static String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            for (String regex : regexes) {
                if (iface.matches(regex)) {
                    return iface;
                }
            }
        }
        return null;
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            mWifiConfig = mDialog.getConfig();
            if (mWifiConfig != null) {
                /**
                 * if soft AP is stopped, bring up
                 * else restart with new config
                 * TODO: update config on a running access point when framework support is added
                 */
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
                    mWifiManager.setWifiApEnabled(null, false);
                    mWifiManager.setWifiApEnabled(mWifiConfig, true);
                } else {
                    mWifiManager.setWifiApConfiguration(mWifiConfig);
                }
                int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
                mCreateNetwork.setSummary(String.format(getActivity().getString(CONFIG_SUBTEXT),
                        mWifiConfig.SSID,
                        mSecurityType[index]));
            }
        }
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_tether;
    }

    private void checkDefaultValue(Context ctx) {
        boolean def_ssid = ctx.getResources().getBoolean(
                R.bool.hotspot_default_ssid_with_imei_enable);
        boolean clear_pwd = ctx.getResources().getBoolean( R.bool.use_empty_password_default);
        if (def_ssid || clear_pwd) {
            SharedPreferences sharedPreferences = ctx.getSharedPreferences(
                    SHAREPREFERENCE_FIFE_NAME,Activity.MODE_PRIVATE);
            boolean hasSetDefaultValue = sharedPreferences.getBoolean(
                    SHAREPREFERENCE_DEFAULT_WIFI, false);
            if ((!hasSetDefaultValue) && (setDefaultValue(ctx , def_ssid , clear_pwd))) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(SHAREPREFERENCE_DEFAULT_WIFI,true);
                editor.commit();
            }
        }
    }

    private boolean setDefaultValue(Context ctx, boolean default_ssid, boolean clear_password) {
        WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return false;
        }
        WifiConfiguration wifiAPConfig = wifiManager.getWifiApConfiguration();
        if (wifiAPConfig == null) {
            return false;
        }
        if (default_ssid) {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(
                    Context.TELEPHONY_SERVICE);
            String deviceId = tm.getDeviceId();
            String lastFourDigits = "";
            if ((deviceId != null) && (deviceId.length() > 3)) {
                lastFourDigits =  deviceId.substring(deviceId.length()-4);
            }
            wifiAPConfig.SSID = Build.MODEL;
            if ((!TextUtils.isEmpty(lastFourDigits)) && (wifiAPConfig.SSID != null)
                    && (wifiAPConfig.SSID.indexOf(lastFourDigits) < 0)) {
                 wifiAPConfig.SSID += " " + lastFourDigits;
            }
        }
        if (clear_password) {
            wifiAPConfig.preSharedKey = "";
        }
        wifiManager.setWifiApConfiguration(wifiAPConfig);
        return true;
    }

    private boolean preTurnOn(Context ctx, int choice) {
        if (TETHERING_BLUETOOTH == choice || (!ctx.getResources().getBoolean(
                R.bool.hotspot_accout_check_enable))) {
            return false;
        }
        Intent hotspot_preConfigure_intent = new Intent(ACTION_HOTSPOT_CONFIGURE);
        hotspot_preConfigure_intent.putExtra(ACTION_EXTRA, choice);
        ctx.startActivity(hotspot_preConfigure_intent);
        return true;
    }

    private boolean postTurnOn(Context ctx, int choice, boolean bEnable) {
        if (TETHERING_BLUETOOTH == choice || (!ctx.getResources().getBoolean(
                R.bool.hotspot_accout_check_enable))) {
            return false;
        }
        Intent hotspot_postConfigure_intent = new Intent(ACTION_HOTSPOT_POST_CONFIGURE);
        hotspot_postConfigure_intent.putExtra(ACTION_EXTRA, choice);
        hotspot_postConfigure_intent.putExtra( ACTION_EXTRA_VALUE , bEnable);
        ctx.startActivity(hotspot_postConfigure_intent);
        return true;
    }

    private void unRegisterConfigureReceiver() {
        if (mConfigureReceiver != null) {
            getActivity().unregisterReceiver(mConfigureReceiver);
            mConfigureReceiver = null;
        }
    }

    private void registerConfigureReceiver(Context ctx) {
        IntentFilter filter = new IntentFilter(ACTION_HOTSPOT_CONFIGURE_RRSPONSE);
        if (mConfigureReceiver == null) {
            mConfigureReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(ACTION_HOTSPOT_CONFIGURE_RRSPONSE)) {
                        boolean result = intent.getBooleanExtra(CONFIGURE_RESULT,true);
                        if (result) {
                            startTethering();
                        } else {
                            mWifiApEnabler.setChecked(false);
                        }
                    }
                }
            };
        }
        ctx.registerReceiver(mConfigureReceiver, filter);
    }
}
