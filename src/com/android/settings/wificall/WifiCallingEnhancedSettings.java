/* Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings.wificall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.R.integer;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.Toast;

import com.android.ims.ImsManager;
import com.android.settings.R;

public class WifiCallingEnhancedSettings extends PreferenceActivity
        implements Preference.OnPreferenceClickListener {

    private static final boolean DEBUG = false;
    private static final String TAG = WifiCallingEnhancedSettings.class.getSimpleName();
    private static final String KEY_WIFI_CALLING_WIZARD = "wifi_calling_wizard";
    private static final String KEY_WIFI_CALLING_PREFERRED_SCREEN = "wifi_calling_prefence";
    // Below is the prefence key as the same as the CheckBoxPreference title.
    private static final String KEY_WIFI_CALLING_PREFERRED = "Wi-Fi Preferred";
    private static final String KEY_CELLULAR_NETWORK_PREFERRED = "Cellular Network Preferred";
    private static final String KEY_WIFI_CALL_HELP_PREFERRED = "wifi_calling_tutorial";
    private static final String KEY_NEVER_USE_CELLULAR_NETWORK_PREFERRED
            = "Never use Cellular Network";
    private static final String KEY_WIFI_CALLING_CONNECTION_PREFERENCE
            = "wifi_calling_connection_pref";
    private static final String WIFI_CALLING_PREFERRED = "preference";
    private static final String PACKAGE_NAME = "com.android.settings";
    private static final String CLASS_NAME =
            "com.android.settings.wificall.WifiCallingWizardActivity";
    private static final String ACTION_WIFI_CALL_ON = "com.android.wificall.TURNON";
    private static final String ACTION_WIFI_CALL_OFF = "com.android.wificall.TURNOFF";
    private static final String INTENT_EXTRA = "triggeredFromHelp";
    private static final int WIFI_CALLING_STATE_ON = 2;
    private static final int WIFI_CALLING_STATE_OFF = 1;
    private static final int DEFAULT_INDEX = 0;
    private static int mStatus = WIFI_CALLING_STATE_OFF;
    private Preference mWifiCallingHelp;
    private Preference mWifiCallingConnectPre;
    private PreferenceScreen mScreen;
    private PreferenceCategory mPrefCate;
    private Switch mSwitch;
    private int mSelection = 0;
    private ArrayList<CheckBoxPreference> mCheckboxPref = new ArrayList<CheckBoxPreference>();
    private Map<String, Integer> mPrefenceIndex = new HashMap<String, Integer>();

    @Override
    protected void onCreate(Bundle icicle) {
        setTheme(android.R.style.Theme_Material_Settings);
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.wifi_call_enhanced_settings);
        mScreen = getPreferenceScreen();
        mSwitch = new Switch(this);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        updatePrefence();
    }

    private void updatePrefence(){
        mWifiCallingHelp = mScreen.findPreference(KEY_WIFI_CALL_HELP_PREFERRED);
        mWifiCallingHelp.setOnPreferenceClickListener(this);
        mWifiCallingConnectPre = mScreen
                .findPreference(KEY_WIFI_CALLING_CONNECTION_PREFERENCE);
        mWifiCallingConnectPre.setOnPreferenceClickListener(this);
        mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView,
                        boolean isChecked) {
                    if(DEBUG) Log.i(TAG, "onCheckedChanged isChecked : " + isChecked);
                    setWifiCallingPreference(
                            isChecked ? WifiCallingStatusControl.WifiCallingValueConstants.ON
                                    : WifiCallingStatusControl.WifiCallingValueConstants.OFF,
                            mSelection);
                    mPrefCate.setEnabled(isChecked);
                }
        });
        ActionBar actionBar = getActionBar();
        if (actionBar != null && mSwitch != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(mSwitch, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        }
        mPrefCate = (PreferenceCategory) findPreference(KEY_WIFI_CALLING_PREFERRED_SCREEN);
        String[] titleArray = getBaseContext().getResources().getStringArray(
                R.array.wifi_call_preferences_entries_title);
        String[] summaryArray = getBaseContext().getResources().getStringArray(
                R.array.wifi_call_preferences_entries_summary);
        String[] entriesArray = getBaseContext().getResources().getStringArray(
                R.array.wifi_call_preferences_entries);
        for (int i = 0; i < titleArray.length; i++) {
            CheckBoxPreference pref = new CheckBoxPreference(this);
            pref.setKey(titleArray[i]);
            pref.setOnPreferenceClickListener(this);
            pref.setChecked(i == DEFAULT_INDEX);
            pref.setTitle(titleArray[i]);
            pref.setSummary(summaryArray[i]);
            mPrefCate.addPreference(pref);
            mCheckboxPref.add(pref);
            mPrefenceIndex.put(titleArray[i], Integer.parseInt(entriesArray[i]));
            if (pref.isChecked()) mSelection = Integer.parseInt(entriesArray[i]);
        }
        mScreen.removePreference(mPrefCate);
        if (mStatus == WIFI_CALLING_STATE_ON) {
            changeToPreference();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getWifiCallingPreference();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private String getWifiPreferenceString(int wifiPreference) {
        switch (wifiPreference) {
            case WifiCallingStatusControl.WifiCallingPreference.WIFI_PREFERRED:
                return (getString(R.string.wifi_preferred));
            case WifiCallingStatusControl.WifiCallingPreference.WIFI_ONLY:
                return (getString(R.string.wifi_only));
            case WifiCallingStatusControl.WifiCallingPreference.CELLULAR_PREFERRED:
                return (getString(R.string.cellular_preferred));
            case WifiCallingStatusControl.WifiCallingPreference.CELLULAR_ONLY:
                return (getString(R.string.cellular_only));
            case WifiCallingStatusControl.WifiCallingPreference.WIFI_PREF_NONE:
            default:
                return (getString(R.string.wifi_pref_none));
        }
    }

    private void loadWifiCallingPreference(int status, int preference) {
        if(DEBUG) Log.d(TAG, "loadWifiCallingPreference status = " + status
                + " preference = " + preference);
        if (status == WifiCallingStatusControl.WifiCallingValueConstants.NOT_SUPPORTED) {
            return;
        }
        boolean isTurnOn = getWifiCallingSettingFromStatus(status);
        mSwitch.setChecked(isTurnOn);
        mPrefCate.setEnabled(isTurnOn);
        Set<String> set = mPrefenceIndex.keySet();
        for(String prefence : set){
            if(mPrefenceIndex.get(prefence).equals(preference)){
                updateSelection(prefence);
            }
        }
    }

    private void broadcastWifiCallingStatus(boolean isTurnOn, int preference) {
        Intent intent = new Intent(isTurnOn ? ACTION_WIFI_CALL_ON
                    : ACTION_WIFI_CALL_OFF);
        intent.putExtra(WIFI_CALLING_PREFERRED, preference);
        sendBroadcast(intent);
    }

    public void getWifiCallingPreference() {
        if(DEBUG) Log.d(TAG, "getWifiCallingPreference called");
        int wfcMode = ImsManager.getWfcMode(getBaseContext());
        int state = ImsManager.isWfcEnabledByUser(getBaseContext())?
               WifiCallingStatusControl.WifiCallingValueConstants.ON :
               WifiCallingStatusControl.WifiCallingValueConstants.OFF;
        loadWifiCallingPreference(state,wfcMode);
    }

    private boolean setWifiCallingPreference(int state, int preference) {
        if(DEBUG) Log.d(TAG, "setWifiCallingPreference:");
        ImsManager.setWfcSetting(getBaseContext(),
                state == WifiCallingStatusControl.WifiCallingValueConstants.ON);
        ImsManager.setWfcMode(getBaseContext(),preference);
        broadcastWifiCallingStatus(state == WIFI_CALLING_STATE_OFF, preference);
        return true;
    }

    private boolean getWifiCallingSettingFromStatus(int status) {
        switch (status) {
            case WifiCallingStatusControl.WifiCallingValueConstants.ON:
                return true;
            case WifiCallingStatusControl.WifiCallingValueConstants.OFF:
            case WifiCallingStatusControl.WifiCallingValueConstants.NOT_SUPPORTED:
            default:
                return false;
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(KEY_WIFI_CALL_HELP_PREFERRED)) {
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            intent.setClassName(PACKAGE_NAME,CLASS_NAME);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.wifi_help_title));
            final String[] options = {getString(R.string.wifi_help_option_tutorial),
                                            getString(R.string.wifi_help_option_top_questions)};
            builder.setItems(options, new DialogInterface.OnClickListener() {
                @Override
                    public void onClick(DialogInterface dialog, int which) {
                        intent.putExtra(INTENT_EXTRA,which == DEFAULT_INDEX);
                        startActivity(intent);
                    }
                }
            );
            builder.show();
        }
        if (preference.getKey().equals(KEY_WIFI_CALLING_CONNECTION_PREFERENCE)) {
            changeToPreference();
            return true;
        }
        updateSelection(preference.getKey());
        int state = mSwitch.isChecked() ?
                WifiCallingStatusControl.WifiCallingValueConstants.ON :
                WifiCallingStatusControl.WifiCallingValueConstants.OFF;
        boolean result = setWifiCallingPreference(state, mSelection);
        if (result) {
            loadWifiCallingPreference(state, mSelection);
        }
        return true;
    }

    // Control the three checkbox: only one should be selected.
    private void updateSelection(String preferenceKey){
        if (preferenceKey == null) {
            if (DEBUG) Log.i(TAG, "updateSelection is null");
            return;
        }
        for (int index = 0; index < mCheckboxPref.size(); index ++) {
            CheckBoxPreference checkbox = mCheckboxPref.get(index);
            if (preferenceKey.equals(checkbox.getKey())) {
                checkbox.setChecked(true);
                mSelection = mPrefenceIndex.get(preferenceKey);
            } else {
                checkbox.setChecked(false);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mScreen.findPreference(KEY_WIFI_CALLING_PREFERRED_SCREEN) != null) {
            mScreen.removePreference(mPrefCate);
            mScreen.addPreference(mWifiCallingConnectPre);
            mScreen.addPreference(mWifiCallingHelp);
            mSwitch.setVisibility(View.VISIBLE);
            mStatus = WIFI_CALLING_STATE_OFF;
            ActionBar actionBar = getActionBar();
            if (actionBar != null && mSwitch != null) {
                actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                        ActionBar.DISPLAY_SHOW_CUSTOM);
                actionBar.setCustomView(mSwitch, new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
        } else {
            super.onBackPressed();
        }
    }

    private void changeToPreference(){
        mStatus = WIFI_CALLING_STATE_ON;
        mScreen.removePreference(mWifiCallingConnectPre);
        mScreen.removePreference(mWifiCallingHelp);
        mScreen.addPreference(mPrefCate);
        mSwitch.setVisibility(View.VISIBLE);
    }
}

