/*
* Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*      with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*      contributors may be used to endorse or promote products derived
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.settings.R;

import java.util.List;

public class WifiCallingStatusControl extends BroadcastReceiver {

    public static final String ACTION_WIFI_CALL_TURN_ON = "com.android.wificall.TURNON";
    public static final String ACTION_WIFI_CALL_TURN_OFF = "com.android.wificall.TURNOFF";
    public static final String ACTION_WIFI_CALL_ERROR_CODE = "com.android.wificall.ERRORCODE";
    public static final String ACTION_WIFI_CALL_ERROR_CODE_EXTRA =
            "com.android.wificall.errorcode.extra";
    public static final String ACTION_IMS_STATE_CHANGE = "com.android.imscontection.DISCONNECTED";
    public static final String ACTION_WIFI_REFRESH_RADIO = "com.android.wificall.REFRESH_RADIO";
    public static final int WIFI_CALLING_ROVE_IN_THRESHOD = -75;
    public static final String ACTION_WIFI_CALL_READY_STATUS_CHANGE = "com.android.wificall.READY";
    public static final String ACTION_WIFI_CALL_READY_EXTRA = "com.android.wificall.ready.extra";
    public static final String SYSTEM_PROPERTY_WIFI_CALL_READY = "sys.wificall.ready";
    public static final String SYSTEM_PROPERTY_WIFI_CALL_STATUS_MSG = "sys.wificall.status.msg";
    public static final String SYSTEM_PROPERTY_WIFI_CALL_TURNON = "persist.sys.wificall.turnon";
    private static final String SHAREDPREFERENCES_FILE_NAME = "MY_PERFS";
    private static final String SHAREDPREFERENCES_WIFI_CALL_SET = "is_first_set_wfc";
    private static final String SHAREDPREFERENCES_NAME = "MY_PERFS";
    private static final String WIFI_CALLING_PREFERENCE = "currentWifiCallingPreference";
    private static final String WIFI_CALLING_STATE = "currentWifiCallingStatus";
    private static final String WIFI_CALLING_ENABLED = "true";
    private static final String WIFI_CALLING_DISABLED = "false";
    private static final String ACTION_EXTRA = "preference";
    private static final String ACTION_RESULT = "result";
    private static final String TAG = WifiCallingStatusControl.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static int WIFI_CALL_PREFERRED_DISENABLED = -1;
    private static Context mContext;
    private static int mWifiCallPreferred = -1;
    private static int mErrorCode = -1;
    private static boolean mIsE911CallOngoing = false;
    private static boolean mIsGuardTimerOngoing = false;
    private static PhoneStateListener mPhoneStateListener = new PhoneStateListener(){
        public void onCallStateChanged(int state, String incomingNumber) {
            WifiCallingNotification.updateWFCCallStateChange(mContext, state);
        };
    };

    private static final int WIFI_CALLING_STATE_REGISTERED = 1;
    private static final int WIFI_CALLING_STATE_NOT_REGISTERED = 2;
    private static final int WIFI_CALLING_STATE_REGISTERING = 3;
    private static final long REFRESH_RADIO_TIMER_AFTER_E911 = 180000;

    private static boolean mWifiTurnOn = false;
    private static boolean mWifiConnected = false;
    private static boolean mWifiCallTurnOn = false;
    private static boolean mImsRegisted = false;
    private static boolean mIsWifiSignalWeak = false;
    private static boolean mWifiCallReady = false;
    private static NetworkInfo mWifiNetwork = null;
    private static String mWifiCallStatusMsg = "Not Ready";
    private static String mOldWifiCallStatusMsg = "Not Ready";
    private static String mExtraMsg = "";
    private static int mOldErrorCode = -1;
    private static boolean mOldWifiCallReady = false;
    private static int mRegState = WIFI_CALLING_STATE_NOT_REGISTERED;
    private static String mOldErrorMessage = "";
    private WifiManager mWifiManager = null;

    public static class WifiCallingValueConstants {
        public static final int NOT_SUPPORTED = 0;
        public static final int ON = 1;
        public static final int OFF = 2;
    }

    public static class WifiCallingPreference {
        public static final int WIFI_PREF_NONE = 4;
        public static final int WIFI_PREFERRED = 2;
        public static final int WIFI_ONLY      = 0;
        public static final int CELLULAR_PREFERRED = 1;
        public static final int CELLULAR_ONLY = 3;
    }

    private void savePreference(int iPreference, boolean status) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                   SHAREDPREFERENCES_NAME, mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(WIFI_CALLING_PREFERENCE, iPreference);
        editor.putBoolean(WIFI_CALLING_STATE, status );
        editor.commit();
    }

    private void readPreference() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                SHAREDPREFERENCES_NAME, mContext.MODE_PRIVATE);
        mWifiCallPreferred = sharedPreferences.getInt(WIFI_CALLING_PREFERENCE,
                WifiCallingPreference.WIFI_PREFERRED);
        mWifiCallTurnOn = sharedPreferences.getBoolean(WIFI_CALLING_STATE, true);
        SystemProperties.set(SYSTEM_PROPERTY_WIFI_CALL_TURNON,
                (mWifiCallTurnOn? WIFI_CALLING_ENABLED : WIFI_CALLING_DISABLED));
        if (DEBUG) Log.d(TAG, "readPreference, mWifiCallPreferred = " + mWifiCallPreferred);
        if (DEBUG) Log.d(TAG, "readPreference, mWifiCallTurnOn = " + mWifiCallTurnOn);
    }

    private void getWifiStatus() {
        if (mWifiManager != null) {
            mWifiTurnOn = mWifiManager.isWifiEnabled();
        }
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private boolean cellularNetworkIsAvailable() {
        List<CellInfo> cellInfoList = getTelephonyManager().getAllCellInfo();
        if (cellInfoList != null) {
            for (CellInfo cellinfo : cellInfoList) {
                if (cellinfo.isRegistered()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void wifiCallTurnOn(Intent intent, String action) {
        if (DEBUG) Log.d(TAG, "action = " + action);

        savePreference(getWifiCallPreferred(action, intent), getWifiCallTurnOn(action));

        if (mWifiCallPreferred == WifiCallingPreference.CELLULAR_PREFERRED
                && cellularNetworkIsAvailable()) {
            mWifiCallTurnOn = false;
        }

        SystemProperties.set(SYSTEM_PROPERTY_WIFI_CALL_TURNON,
                (mWifiCallTurnOn? WIFI_CALLING_ENABLED : WIFI_CALLING_DISABLED));
        if (DEBUG) Log.d(TAG, "mWifiCallPreferred = " + mWifiCallPreferred);
        if (DEBUG) Log.d(TAG, "mWifiCallTurnOn = " + mWifiCallTurnOn);
        if (!mWifiCallTurnOn) {
            WifiCallingNotification.cancelNotification(mContext);
        }
    }

    private boolean getWifiCallTurnOn(String action) {
        if (ACTION_WIFI_CALL_TURN_OFF.equals(action)
                || ACTION_WIFI_CALL_TURN_ON.equals(action)) {
            mWifiCallTurnOn = ACTION_WIFI_CALL_TURN_ON.equals(action);
        } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                    SHAREDPREFERENCES_NAME, mContext.MODE_PRIVATE);
            mWifiCallTurnOn = sharedPreferences.getBoolean(WIFI_CALLING_STATE, true);
        }
        return mWifiCallTurnOn;
    }

    private int getWifiCallPreferred(String action , Intent intent) {
         if (ACTION_WIFI_CALL_TURN_OFF.equals(action)
                || ACTION_WIFI_CALL_TURN_ON.equals(action)) {
            mWifiCallPreferred = intent.getIntExtra(ACTION_EXTRA,
                    WifiCallingPreference.WIFI_PREFERRED);
        } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                    SHAREDPREFERENCES_NAME, mContext.MODE_PRIVATE);
            mWifiCallPreferred = sharedPreferences.getInt(WIFI_CALLING_PREFERENCE,
                    WifiCallingPreference.WIFI_PREFERRED);
        }
        return mWifiCallPreferred;
    }

    private void wifiStatusChange(Intent intent, String action) {
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            if (DEBUG) Log.d(TAG, "isWifiTurnOn");
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                    WifiCallingWizardActivity.PRIVTE_PREFERENCE, Context.MODE_PRIVATE);
            boolean showWifiCallWizard = sharedPreferences.getBoolean(
                    WifiCallingWizardActivity.WIZARD_SHOW_PREFERENCE, true);
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            if (DEBUG) Log.d(TAG, "showWifiCallingWizardActivity = " + showWifiCallWizard);

            if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                mWifiTurnOn = true;
                if (showWifiCallWizard) {
                    Intent start = new Intent(mContext, WifiCallingWizardActivity.class);
                    start.setAction("android.intent.action.MAIN");
                    start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(start);
                }
            } else {
                mWifiTurnOn = false;
                WifiCallingNotification.cancelNotification(mContext);
            }
        } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
            updateWifiSignalWeak();
        }

        //check wifi contivity state
        ConnectivityManager connect = (ConnectivityManager) mContext
               .getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiNetwork = connect.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        mWifiConnected = mWifiNetwork != null && mWifiNetwork.isConnected();
        if (DEBUG) Log.d(TAG, "mWifiConnected = " + mWifiConnected);
    }

    private void updateWifiSignalWeak() {
        if ((mWifiManager == null) || (mWifiManager.getConnectionInfo() == null)) {
            mIsWifiSignalWeak = false;
        } else {
            if (DEBUG) Log.d (TAG, "wifiStatusChange, Wifi RSSI = "
                    + mWifiManager.getConnectionInfo().getRssi() + "dbm");
            mIsWifiSignalWeak = (mWifiManager.getConnectionInfo().getRssi() <
                    WIFI_CALLING_ROVE_IN_THRESHOD);
        }
    }

    private void updateImsRegistedState(Intent intent, String action) {
        if (ACTION_IMS_STATE_CHANGE.equals(action) ) {
            if (DEBUG) Log.d(TAG, "updateImsRegistedState");
            mRegState = intent.getIntExtra("stateChanged", WIFI_CALLING_STATE_NOT_REGISTERED);
            mImsRegisted = (mRegState == WIFI_CALLING_STATE_REGISTERED);

            if (DEBUG) Log.d(TAG, "mRegState =" + mRegState + ", mImsRegisted = " + mImsRegisted);

            //handle IMS fail reason
            if (mRegState == WIFI_CALLING_STATE_NOT_REGISTERED) {
                Parcelable bundle = intent.getParcelableExtra(ACTION_RESULT);
                if (bundle != null && bundle instanceof ImsReasonInfo) {
                    ImsReasonInfo imsReasonInfo = (ImsReasonInfo)bundle;
                    if (DEBUG) Log.i(TAG, "mRegState =" + mRegState);
                    mErrorCode = imsReasonInfo.getExtraCode();
                    mExtraMsg = imsReasonInfo.getExtraMessage();
                    if (DEBUG) Log.i(TAG, "get ImsDisconnected extracode : " + mErrorCode);
                    if (DEBUG) Log.i(TAG, "get ImsDisconnected getExtraMessage :" + mExtraMsg);
                }
            }
        }
    }

    private boolean getCheckErrorMsg(int stringID) {
        boolean checkErrorMsg = !mWifiTurnOn || !mWifiConnected ||
                (mRegState == WIFI_CALLING_STATE_REGISTERED) ||
                (mRegState == WIFI_CALLING_STATE_REGISTERING);
        if (mWifiCallPreferred == WifiCallingPreference.CELLULAR_PREFERRED) {
            SystemProperties.set(
                    SYSTEM_PROPERTY_WIFI_CALL_STATUS_MSG, mContext.getString(stringID));
            return !cellularNetworkIsAvailable();
        } else if (checkErrorMsg) {
            return false;
        }
        return true;
    }

    private int getStringID() {
        if (mWifiCallPreferred == WifiCallingPreference.CELLULAR_PREFERRED) {
            return R.string.wifi_call_status_cellular_preferred;
        } else if (!mWifiTurnOn) {
            return R.string.wifi_call_status_wifi_off;
        } else if (!mWifiConnected) {
            return R.string.wifi_call_status_not_connected_wifi;
        } else if (mIsWifiSignalWeak) {
            if (DEBUG) Log.i(TAG, "debug: Wifi is Weak");
            return R.string.wifi_call_status_poor_wifi_signal;
        } else if (mRegState == WIFI_CALLING_STATE_REGISTERED) {
            return R.string.wifi_call_status_ready;
        } else if (mRegState == WIFI_CALLING_STATE_REGISTERING) {
            return R.string.wifi_call_status_enabling;
        }

        return R.string.wifi_call_status_error_unknown;
    }

    private String getExtraMsg() {
        // For IMS NOT registered state, show the right error message.
        if ((mExtraMsg != null) && (!TextUtils.isEmpty(mExtraMsg))) {
            if (DEBUG) Log.i(TAG, "valid error message received");
        } else {
            if (DEBUG)
                Log.i(TAG,"get null error message from low layer. Still use original one");
            mExtraMsg = mOldErrorMessage;
            if (mErrorCode != mOldErrorCode) {
                mExtraMsg = "";
            }
        }

        return mExtraMsg;
    }

    private void handleWFCErrorMsg() {
        if (DEBUG) Log.i(TAG, "handleWFCErrorMsg");
        boolean displayErrorCode = false;

        if (DEBUG) Log.i(TAG, "stringID=" + getStringID() + "checkErrorMsg=" + getCheckErrorMsg(getStringID()));
        // Check if there is error message to display.
        // The error message will override the status message above.
        if ((mRegState == WIFI_CALLING_STATE_NOT_REGISTERED) && getCheckErrorMsg(getStringID())) {
            getExtraMsg();
            if ((mExtraMsg != null) && (!TextUtils.isEmpty(mExtraMsg))) {
                if (DEBUG) Log.i(TAG, "display extra error message");
                displayErrorCode = true;
            }

            mOldErrorCode = mErrorCode;
            mOldErrorMessage = mExtraMsg;
        }

        if (DEBUG) Log.i(TAG, "Save WFC status Msg to system property. mExtraMsg="
                           + mExtraMsg + "stringID=" +getStringID()
                           + "displayErrorCode=" + displayErrorCode);

        if (mWifiCallTurnOn) {
            if (displayErrorCode) {
                mWifiCallStatusMsg = mExtraMsg;
                WifiCallingNotification.updateRegistrationError(mContext, mExtraMsg);
            } else {
                mWifiCallStatusMsg = mContext.getString(getStringID());
            }

            SystemProperties.set(SYSTEM_PROPERTY_WIFI_CALL_STATUS_MSG, mWifiCallStatusMsg);
        }
    }

    private void registerWFCInCallListener() {
        TelephonyManager tm = (TelephonyManager)mContext.
                getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void unregisterWFCInCallListener() {
        TelephonyManager tm = (TelephonyManager)mContext.
                getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    private void updateWFCReadyIcon() {
        WifiCallingNotification.getIntance().updateWFCStatusChange(mContext, mWifiCallReady);
    }

    private void updateWFCInCallIcon() {
        if (mWifiCallReady) {
            registerWFCInCallListener();
        } else {
            unregisterWFCInCallListener();
        }
    }

    private  boolean isAirplaneModeOn() {
        if (DEBUG) Log.d(TAG, "airplane mode is = " + Settings.Global.getInt(
                           mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        return (Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
    }

    private void updateRadioStatus() {
        if (mIsE911CallOngoing) {
            if (DEBUG) Log.d(TAG, "do not change radio during E911 call procedure");
            return;
        }
        boolean isRadioPowerOn = cellularNetworkIsAvailable();
        if (DEBUG) Log.d(TAG, "isRadioPowerOn = " + isRadioPowerOn);
        if (mWifiCallPreferred == WifiCallingPreference.WIFI_ONLY && mWifiCallTurnOn) {
            if (isRadioPowerOn && !isAirplaneModeOn() && cellularNetworkIsAvailable()) {
                if (getTelephonyManager() != null) {
                    getTelephonyManager().setRadioPower(false);
                    if (DEBUG) Log.d(TAG, "updateRadioStatus, turn radio off");
                }
            }
        } else {
            if (!isRadioPowerOn && !cellularNetworkIsAvailable() && !isAirplaneModeOn()) {
                getTelephonyManager().setRadioPower(true);
                if (DEBUG) Log.d(TAG, "updateRadioStatus, turn radio on");
            }
        }
    }

    private void broadcastWifiCallReadyStatus() {
        Intent intent = new Intent(ACTION_WIFI_CALL_READY_STATUS_CHANGE);
        intent.putExtra(ACTION_WIFI_CALL_READY_EXTRA, mWifiCallReady);
        mContext.sendBroadcast(intent);
    }

    private void broadcastWifiCallErrorCode() {
        if (mWifiCallTurnOn) {
            if (mOldWifiCallStatusMsg.compareTo(mWifiCallStatusMsg) != 0) {
                mOldWifiCallStatusMsg = mWifiCallStatusMsg;
                Intent intent = new Intent(ACTION_WIFI_CALL_ERROR_CODE);
                intent.putExtra(ACTION_WIFI_CALL_ERROR_CODE_EXTRA, mWifiCallStatusMsg);
                mContext.sendBroadcast(intent);
            }
        }
    }

    private boolean setWifiCallingPreference(boolean state, int preference) {
        if(DEBUG) Log.d(TAG, "setWifiCallingPreference:" + state + " pre : " + preference);
        ImsManager.setWfcSetting(mContext, state);
        ImsManager.setWfcMode(mContext,preference);
        return true;
    }

    private boolean isFirstBoot(){
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                SHAREDPREFERENCES_FILE_NAME, mContext.MODE_PRIVATE);
        return sharedPreferences.getBoolean(
                SHAREDPREFERENCES_WIFI_CALL_SET, true);
    }

    private void disableFirstBoot(){
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                SHAREDPREFERENCES_FILE_NAME, mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHAREDPREFERENCES_WIFI_CALL_SET, false);
        editor.commit();
        return;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        mContext = context;
        if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
             handlePhoneStateChange(context,intent);
             return;
        } else if (action.equals(ACTION_WIFI_REFRESH_RADIO)) {
             mIsE911CallOngoing = false;
             WifiCallingNotification.getIntance().updateWFCStatusChange(mContext, mWifiCallReady);
             updateRadioStatus();
             mIsGuardTimerOngoing = false;
             return;
        }
        boolean WifiCallStatusChanged = false;
        if (!WifiCallingNotification.getWifiCallingNotifiEnable(context)) {
            if (DEBUG) Log.d(TAG, "getIntent : " + intent.getAction() + " flag : false");
            return;
        }
        mContext = context;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) && isFirstBoot()) {
            if (DEBUG) Log.d(TAG, "getIntent : " + intent.getAction());
            readPreference();
            setWifiCallingPreference(mWifiCallTurnOn, mWifiCallPreferred);
            disableFirstBoot();
            return;
        }
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
        if (mWifiCallReady != mOldWifiCallReady) {
            mOldWifiCallReady = mWifiCallReady;
            WifiCallStatusChanged = true;
        }
        if (mWifiCallPreferred == WIFI_CALL_PREFERRED_DISENABLED) {
            readPreference();
            getWifiStatus();
            if (WifiCallStatusChanged) {
                SystemProperties.set(SYSTEM_PROPERTY_WIFI_CALL_READY,
                        (mWifiCallReady ? WIFI_CALLING_ENABLED : WIFI_CALLING_DISABLED));
                broadcastWifiCallReadyStatus();
            }
        }

        if (DEBUG) Log.d(TAG, "WifiCallingStatusContral, onReceive, action = " + action);

        wifiCallTurnOn(intent, action);
        wifiStatusChange(intent, action);
        updateImsRegistedState(intent, action);

        if (DEBUG) Log.d(TAG, "mWifiCallTurnOn = " + mWifiCallTurnOn
                + ", mWifiConnected = " + mWifiConnected + ", mImsRegisted = " + mImsRegisted);

        boolean wifiCallState = mWifiCallTurnOn &&
                    mWifiConnected && mImsRegisted && !mIsWifiSignalWeak;
        SystemProperties.set(SYSTEM_PROPERTY_WIFI_CALL_READY,
                    (wifiCallState ? WIFI_CALLING_ENABLED : WIFI_CALLING_DISABLED));
        if (mWifiCallReady != wifiCallState) {
            mWifiCallReady = wifiCallState;
            updateWFCReadyIcon();
            updateWFCInCallIcon();
            if (WifiCallStatusChanged) {
                broadcastWifiCallReadyStatus();
            }
        }

        handleWFCErrorMsg();
        broadcastWifiCallErrorCode();
        updateRadioStatus();
    }

    private void handlePhoneStateChange(Context ctx, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }
        String state = bundle.getString(TelephonyManager.EXTRA_STATE);
        if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
            String strDialString = bundle.getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (PhoneNumberUtils.isEmergencyNumber(strDialString)) {
                mIsE911CallOngoing = true;
                WifiCallingNotification.cancelNotification(mContext);
            }
        } else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
            if (!mIsE911CallOngoing && !mIsGuardTimerOngoing) {
                 AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                 long retryAt = System.currentTimeMillis() + REFRESH_RADIO_TIMER_AFTER_E911;
                 Intent refreshRadioIntent = new Intent(ACTION_WIFI_REFRESH_RADIO);
                 PendingIntent tempIntent =
                         PendingIntent.getBroadcast(ctx, 0, refreshRadioIntent, 0);
                 am.setExact(AlarmManager.RTC, retryAt, tempIntent);
                 mIsGuardTimerOngoing = true;
            }
        }
    }
}
