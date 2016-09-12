/*
 *  Copyright (c) 2016, The Linux Foundation. All rights reserved.

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

package com.android.settings.notification;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

import com.android.settings.R;

public class ScaryWarningDialog extends DialogFragment {


    static final String KEY_COMPONENT = "c";
    static final String KEY_LABEL = "l";
    static final String KEY_TITLE = "t";
    static final String KEY_SUMMARY = "s";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context context = getActivity();
        final Bundle args = getArguments();
        final String label = args.getString(KEY_LABEL);
        final ComponentName cn = ComponentName.unflattenFromString(args.getString(KEY_COMPONENT));
        final int dialogTitle = args.getInt(KEY_TITLE);
        final int dialogSummary = args.getInt(KEY_SUMMARY);
        final String title = getResources().getString(dialogTitle, label);
        final String summary = getResources().getString(dialogSummary, label);

        return new AlertDialog.Builder(context)
                .setMessage(summary)
                .setTitle(title)
                .setCancelable(true)
                .setPositiveButton(R.string.allow,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if (getTargetFragment() instanceof ManagedServiceSettings) {
                                    ((ManagedServiceSettings) getTargetFragment())
                                          .setServiceListingStatus(cn, true);
                                }
                            }
                        })
                .setNegativeButton(R.string.deny,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // pass
                            }
                        })
                .create();
    }

    public static void show(Fragment parent, ComponentName cn, 
                                String label, int title, int summary) {
        if (!parent.isAdded()) return;

        final ScaryWarningDialog dialog = new ScaryWarningDialog();
        Bundle args = new Bundle();
        args.putString(KEY_COMPONENT, cn.flattenToString());
        args.putString(KEY_LABEL, label);
        args.putInt(KEY_TITLE, title);
        args.putInt(KEY_SUMMARY, summary);
        dialog.setArguments(args);
        dialog.setTargetFragment(parent, 0);

        dialog.show(parent.getFragmentManager(), "dialog");
    }
}

