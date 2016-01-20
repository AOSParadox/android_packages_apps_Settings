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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.settings.R;

public class WifiCallingWizardActivity extends Activity{

    private static final boolean DEBUG = true;
    private static final String TAG = "WifiCallingWizardActivity";
    private static final String WIZARD_TRIGGERED = "triggeredFromHelp";
    public static final String PRIVTE_PREFERENCE = "wifi_walling_wizard_preference";
    public static final String WIZARD_SHOW_PREFERENCE = "wifi_walling_wizard_preference";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        boolean hasWizard = true;
        if (intent != null) {
            hasWizard = intent.getBooleanExtra(WIZARD_TRIGGERED, true);
        }
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (hasWizard) {
            getActionBar().setTitle(getString(R.string.wifi_calling_tutorial_title));
            fragmentTransaction.add(com.android.internal.R.id.content,
                    new WizardFragment(this));
        } else {
            getActionBar().setTitle(getString(R.string.wifi_calling_questions_title));
            fragmentTransaction.add(com.android.internal.R.id.content,
                    new QuestionFragment());
        }
        fragmentTransaction.commit();
    }

    public void handleFramework(boolean finished) {
        if (finished) {
            SharedPreferences sprefence = getSharedPreferences(PRIVTE_PREFERENCE, MODE_PRIVATE);
            Editor editor = sprefence.edit();
            editor.putBoolean(WIZARD_SHOW_PREFERENCE, false);
            editor.commit();
        }
        finish();
    }

    public class QuestionFragment extends Fragment{

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            super.onCreateView(inflater, container, savedInstanceState);
            View view = inflater.inflate(R.layout.wifi_call_top_questions, container, false);
            if(view != null){
                if (DEBUG) Log.i(TAG, "init wifi call top questions");
            }
            return view;
        }
    }
}
