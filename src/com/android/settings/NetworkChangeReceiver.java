/************************************************************
copyright (c) 2016, The Linux Foundation. All rights reserved.

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
****************************************************************/

package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.util.Log;

import static android.net.ConnectivityManager.TYPE_MOBILE;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private WifiManager mWifiManager;
    private boolean isEoGREDisabled = SystemProperties.getBoolean("persist.sys.disable_eogre",true);
    private String APP_TAG = "NetworkChangeReceiver";
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(APP_TAG, "Received the intent :" + intent.getAction());

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, 0);
        boolean isWiFi = networkType == ConnectivityManager.TYPE_WIFI;
        boolean isMobile = networkType == ConnectivityManager.TYPE_MOBILE;
        NetworkInfo networkInfo = connMgr.getNetworkInfo(networkType);
        boolean isConnected = networkInfo.isConnected();

        if (isMobile) {
            if (isConnected) {
                Log.i(APP_TAG, "Mobile - CONNECTED");
            } else {
                Log.i(APP_TAG, "Mobile - DISCONNECTED");
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED
                        && !isEoGREDisabled)
                    mWifiManager.setWifiApEnabled(null, false);
            }
       }
    }
}
