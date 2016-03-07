/*
Copyright (c) 2015-2016, The Linux Foundation. All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.android.settings.wificall;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.preference.SwitchPreference;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.SearchView.OnSuggestionListener;
import android.widget.Switch;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.settings.R;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WifiCallSwitchPreference extends SwitchPreference {

    private static final boolean DEBUG = false;
    private static final String TAG = "WifiCallSwitchPreference";
    private static final String ACTION_EXTRA = "preference";
    private static final String SHAREDPREFERENCES_FILE_NAME = "MY_PERFS";
    private static final String SHAREDPREFERENCES_WIFI_CALL_ENABLED = "is_first_use_wfc";
    private static BroadcastReceiver mReceiver = null;
    private int mState = WifiCallingStatusControl.WifiCallingValueConstants.ON;
    private int mPreference = WifiCallingStatusControl.WifiCallingPreference.WIFI_PREFERRED;
    private Activity mParent = null;
    private boolean mIsSwitchClicked = false;
    private String mWFCStatusMsgDisplay = "";

    public WifiCallSwitchPreference(Context context) {
        super(context);
        mIsSwitchClicked = false;
    }

    public WifiCallSwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mIsSwitchClicked = false;
    }

    public WifiCallSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public WifiCallSwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.switchPreferenceStyle);
    }

    public void setParentActivity(Activity curActivity) {
        mParent = curActivity;
    }

    public void registerReciever() {
        if(DEBUG) Log.d(TAG, "registerReciever");
        unRegisterReciever();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiCallingStatusControl.ACTION_WIFI_CALL_READY_STATUS_CHANGE);
        filter.addAction(WifiCallingStatusControl.ACTION_WIFI_CALL_ERROR_CODE);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ((mState == WifiCallingStatusControl.WifiCallingValueConstants.OFF) ||
                        (mState == WifiCallingStatusControl.
                                WifiCallingValueConstants.NOT_SUPPORTED)){
                    if(DEBUG) Log.d(TAG, "do not handle any intent when wificall turned off");
                    return;
                }

                String action = intent.getAction();
                if (WifiCallingStatusControl.ACTION_WIFI_CALL_ERROR_CODE.
                            equals(action) ||
                    WifiCallingStatusControl.ACTION_WIFI_CALL_READY_STATUS_CHANGE.
                            equals(action)) {
                    if (intent != null) updateWFCStatusFromIntent(intent);
                    refreshSwitchSummary(mWFCStatusMsgDisplay);
                }

            }
        };
        getContext().registerReceiver(mReceiver, filter);
    }

    public void unRegisterReciever() {
        if (mReceiver != null) {
            getContext().unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void updateWFCStatusFromIntent(Intent intent) {
        if (WifiCallingStatusControl.ACTION_WIFI_CALL_ERROR_CODE.
                equals(intent.getAction())) {
            mWFCStatusMsgDisplay = intent.getStringExtra(
                    WifiCallingStatusControl.ACTION_WIFI_CALL_ERROR_CODE_EXTRA);
        }
        if(DEBUG) Log.d(TAG, "updateWFCStatusFromIntent called. mWFCStatusMsgDisplay:" +
                mWFCStatusMsgDisplay);
    }

    private void updateWFCStatusFromProp() {
        if ((mState == WifiCallingStatusControl.WifiCallingValueConstants.OFF) ||
                (mState == WifiCallingStatusControl.WifiCallingValueConstants.NOT_SUPPORTED)) {
            mWFCStatusMsgDisplay = getContext().getString(R.string.wifi_call_status_disabled);
        } else if ((mPreference ==
                WifiCallingStatusControl.WifiCallingPreference.CELLULAR_PREFERRED)
                && isCellularNetworkAvailable()) {
            mWFCStatusMsgDisplay =
                    getContext().getString(R.string.wifi_call_status_cellular_preferred);
        } else if (!isWifiTurnedOn()) {
            mWFCStatusMsgDisplay = getContext().getString(R.string.wifi_call_status_wifi_off);
        } else if (!isWifiConnected()) {
            mWFCStatusMsgDisplay =
                    getContext().getString(R.string.wifi_call_status_not_connected_wifi);
        } else {
            if (mIsSwitchClicked) {
                //Display "enabling..." if user just turns on WFC.
                //This message will be updated upon other intents.
                mWFCStatusMsgDisplay = getContext().getString(R.string.wifi_call_status_enabling);
            } else {
                String msg = SystemProperties.get(
                        WifiCallingStatusControl.SYSTEM_PROPERTY_WIFI_CALL_STATUS_MSG, "");
                if (!TextUtils.isEmpty(msg)) {
                    mWFCStatusMsgDisplay = msg;
                } else {
                    mWFCStatusMsgDisplay =
                            getContext().getString(R.string.wifi_call_status_error_unknown);
                }
            }
        }

        if(DEBUG) Log.d(TAG, "updateWFCStatusFromProp called. mWFCStatusMsgDisplay:" +
                mWFCStatusMsgDisplay);
    }

    private boolean isWifiTurnedOn() {
        boolean isWifiOn = false;
        WifiManager wifimgr = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        isWifiOn = (wifimgr != null && wifimgr.isWifiEnabled());
        if(DEBUG) Log.d(TAG, "isWifiTurnedOn = " + isWifiOn);
        return isWifiOn;
    }

    private boolean isWifiConnected() {
        boolean isWifiConnected = false;
        ConnectivityManager connect = (ConnectivityManager) getContext()
               .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connect != null) {
            NetworkInfo wifiNet = connect.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            isWifiConnected = (wifiNet == null) ? false : (wifiNet.isConnected());
        }
        if(DEBUG) Log.d(TAG, "isWifiConnected = " + isWifiConnected);
        return isWifiConnected;
    }

    private boolean isCellularNetworkAvailable() {
        try {
            List<CellInfo> cellInfoList = ((TelephonyManager) getContext().getSystemService(
                    Context.TELEPHONY_SERVICE)).getAllCellInfo();
            if (cellInfoList != null) {
                for (CellInfo cellinfo : cellInfoList) {
                    if (cellinfo.isRegistered()) {
                        return true;
                    }
                }
            }
        } catch (NullPointerException e) {
            if(DEBUG) Log.e(TAG, "null pointer exception" + e);
        }
        return false;
    }

    @Override
    protected void onClick() {
        super.onClick();
        if(DEBUG) Log.i(TAG, "onClik CheckedStatus : " + isChecked());
        // The switchpreference turn on/off must keep as the same as the interal switch
        setChecked(!isChecked());
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        View checkableView = view
                .findViewById(com.android.internal.R.id.switchWidget);
        if (checkableView != null && checkableView instanceof Switch) {
            if(DEBUG) Log.i(TAG, "start setOnCheckedChangeListener");
            ((Switch) checkableView).setClickable(true);
            final Switch switchView = (Switch) checkableView;
            // Add the switch checkedChangeListener for that
            // when user press the switch to turn on/off the wifi calling
            // press the text part for the wifi calling preference interface.
            switchView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(
                        CompoundButton buttonView, boolean isChecked) {
                    if(DEBUG) Log.i(TAG, "start onCheckedChanged isChecked : " + isChecked);
                    if (!callChangeListener(isChecked)) {
                        if(DEBUG) Log.e(TAG,
                                "onCheckedChanged doesn't like it. Change it back.");
                        buttonView.setChecked(!isChecked);
                        return;
                    }
                    if(DEBUG) Log.i(TAG, "start onCheckedChanged isChecked : " + isChecked);
                    WifiCallSwitchPreference.this.setChecked(isChecked);
                    onSwitchClicked();
                }
            });
        }
    }

    public void onSwitchClicked() {
        if(DEBUG) Log.d(TAG, "onSwitchClicked " + isChecked());
        mIsSwitchClicked = true;
        mState = isChecked() ? WifiCallingStatusControl.WifiCallingValueConstants.ON :
                WifiCallingStatusControl.WifiCallingValueConstants.OFF;
        getWifiCallingPreference();
        updateWFCStatusFromProp();
        refreshSwitchSummary(mWFCStatusMsgDisplay);
    }


    private void syncUserSetting2Modem(int state, int pref) {
        if(DEBUG) Log.d(TAG, "sync user setting to modem: state=" + state + " Preference=" + pref);
        setWifiCallingPreference(state, pref);
        String wifiCallStatus = (state == WifiCallingStatusControl.WifiCallingValueConstants.ON ?
                WifiCallingStatusControl.ACTION_WIFI_CALL_TURN_ON :
                WifiCallingStatusControl.ACTION_WIFI_CALL_TURN_OFF);
        Intent intent = new Intent(wifiCallStatus);
        intent.putExtra(ACTION_EXTRA, pref);
        getContext().sendBroadcast(intent);
    }

    private void syncUserSettingFromModem(int state, int pref) {
        if(DEBUG) Log.d(TAG, "sync user setting from modem: state=" + state +
                " Preference=" + pref);
        mPreference = pref;
        if (state != mState) {
            mState = state;
            updateWFCStatusFromProp();
            refreshSwitchState(mState);
            refreshSwitchSummary(mWFCStatusMsgDisplay);
        }
    }

    private boolean ismParentEnabled() {
        if (mParent == null) {
            if(DEBUG) Log.e(TAG, "refreshSwitchState: mParent = null!");
            return false;
        }
        return true;
    }
    private void refreshSwitchState(final int state) {
        if(DEBUG) Log.d(TAG, "refreshSwitchState");
        if (!ismParentEnabled()) return;
        mParent.runOnUiThread(new Runnable() {
            public void run() {
                if(DEBUG) Log.d (TAG, "new UI thread.");
                boolean isChecked = (state ==
                        WifiCallingStatusControl.WifiCallingValueConstants.ON);
                setChecked(isChecked);
            }
        });
    }

    private void refreshSwitchSummary(final String msg) {
        if(DEBUG) Log.d(TAG, "refreshSwitchSummary");
        if (!ismParentEnabled() && (msg == null)) {
            if(DEBUG) Log.e(TAG, "refreshSwitchSummary: mParent = null or message = null");
            return ;
        }
        mParent.runOnUiThread(new Runnable() {
            public void run() {
                if(DEBUG) Log.d (TAG, "new UI thread.");
                setSummary(msg);
            }
        });
    }

    private boolean isWifiCallUsedFirstTime() {
        if (!ismParentEnabled()) return false;
        SharedPreferences sharedPreferences = mParent.getSharedPreferences(
                SHAREDPREFERENCES_FILE_NAME, mParent.MODE_PRIVATE);
        if (getFirstUseWfcFromPreferences(sharedPreferences)) {
            if(DEBUG) Log.d(TAG, "first time use WifiCall.");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(SHAREDPREFERENCES_WIFI_CALL_ENABLED, false);
            editor.commit();
        }
        return getFirstUseWfcFromPreferences(sharedPreferences);
    }

    private boolean getFirstUseWfcFromPreferences(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(
                SHAREDPREFERENCES_WIFI_CALL_ENABLED, true);
    }

    public void getWifiCallingPreference() {
        if(DEBUG) Log.d(TAG, "getWifiCallingPreference called");
        int wfcMode = ImsManager.getWfcMode(getContext());
        int state = ImsManager.isWfcEnabledByUser(getContext())?
               WifiCallingStatusControl.WifiCallingValueConstants.ON :
               WifiCallingStatusControl.WifiCallingValueConstants.OFF;
        onGetWifiCallingPreference(state,wfcMode);
    }

    private boolean setWifiCallingPreference(int state, int preference) {
        if(DEBUG) Log.d(TAG, "setWifiCallingPreference:");
        ImsManager.setWfcSetting(getContext(),
                state == WifiCallingStatusControl.WifiCallingValueConstants.ON);
        ImsManager.setWfcMode(getContext(),preference);
        return true;
    }

    private void onGetWifiCallingPreference(int wifiCallingStatus,
             int wifiCallingPreference) {
         if(DEBUG) Log.d(TAG, "onGetWifiCallingPreference");
         if (mIsSwitchClicked) {
             if(DEBUG) Log.d (TAG, "mIsSwitchClicked is true.");
             int state = isChecked()? WifiCallingStatusControl.WifiCallingValueConstants.ON :
                     WifiCallingStatusControl.WifiCallingValueConstants.OFF;
             //add check if change happen
             syncUserSetting2Modem(state, mPreference);
             mIsSwitchClicked = false;
             return ;
         }
         boolean wifiCallPre = (wifiCallingPreference !=
                 WifiCallingStatusControl.WifiCallingPreference.WIFI_PREFERRED);
         boolean wifiCallStatu = (wifiCallingStatus !=
                 WifiCallingStatusControl.WifiCallingValueConstants.ON);
         boolean wificallEnabled = isWifiCallUsedFirstTime() && (wifiCallPre || wifiCallStatu);
         if (wificallEnabled) {
             if(DEBUG) Log.d(TAG, "Config default setting when first time use wificall");
             mState = WifiCallingStatusControl.WifiCallingValueConstants.ON;
             wifiCallingStatus = mState;
             mPreference = WifiCallingStatusControl.WifiCallingPreference.WIFI_PREFERRED;
             syncUserSetting2Modem(mState, mPreference);
         } else {
             syncUserSettingFromModem(wifiCallingStatus, wifiCallingPreference);
         }
         updateWFCStatusFromProp();
         refreshSwitchState(wifiCallingStatus);
         refreshSwitchSummary(mWFCStatusMsgDisplay);
    }
}
