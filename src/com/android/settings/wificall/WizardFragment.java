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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settings.R;

public class WizardFragment extends Fragment implements OnClickListener{

    private static final boolean DEBUG = true;
    private int mWizardIndex = FIRST_WIZARD;
    private Button mLeftButton;
    private Button mRightButton;
    private TextView mContentText;
    private ImageView mImage;
    private TextView mStepText;
    private static final String TAG = "WifiCallingWizardActivity";
    private static final int FIRST_WIZARD = 1;
    private static final int SECOUND_WIZARD = 2;
    private static final int THIRD_WIZARD = 3;
    private WifiCallingWizardActivity mParent = null;
    public WizardFragment(WifiCallingWizardActivity pParent) {
        mParent = pParent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.wifi_call_wizard, container, false);
        if(view != null){
            if (DEBUG) Log.i(TAG, "init view and listener");
            mLeftButton = (Button)view.findViewById(R.id.leftbutton);
            mRightButton = (Button)view.findViewById(R.id.rightbutton);
            mContentText = (TextView)view.findViewById(R.id.content_view);
            mImage = (ImageView)view.findViewById(R.id.content_view_icon);
            mStepText = (TextView)view.findViewById(R.id.content_view_step_4);
            mImage.setVisibility(View.INVISIBLE);
            mStepText.setVisibility(View.INVISIBLE);
            mLeftButton.setOnClickListener(this);
            mRightButton.setOnClickListener(this);
        }
        return view;
    }

    @Override
    public void onClick(View v) {
        if (DEBUG) Log.i(TAG, "current wizard index : " + mWizardIndex);
        switch (mWizardIndex) {
            case FIRST_WIZARD:
                if(v.getId() == R.id.leftbutton){
                   mParent.handleFramework(false);
                }
                break;
            /* for second screen of this wizard */
            case SECOUND_WIZARD:
                changeWizard(++mWizardIndex);
                break;
            case THIRD_WIZARD:
                mParent.handleFramework(true);
                break;
            default:
                if (DEBUG) Log.i(TAG, "unknow current wizard index");
                break;
        }
    }

    private void changeWizard(int nextWizard) {
        switch (nextWizard) {
            case SECOUND_WIZARD:
                mLeftButton.setVisibility(View.GONE);
                mRightButton.setText(R.string.next_label);
                mContentText.setText(R.string.wifi_calling_wizard_content_step_2);
                break;

            case THIRD_WIZARD:
                mImage.setVisibility(View.VISIBLE);
                mStepText.setVisibility(View.VISIBLE);
                mLeftButton.setVisibility(View.GONE);
                mRightButton.setText(R.string.wifi_display_options_done);
                mContentText.setText(R.string.wifi_calling_wizard_content_step_3);
                break;

            default:
                break;
        }
    }
}
