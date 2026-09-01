/*
 * SPDX-FileCopyrightText: 2020 Anjani Kumar <anjanik012@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.clipboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect_tp.R;

public class ClipboardFloatingActivity extends AppCompatActivity {

    private static final String KEY_SHOW_TOAST = "SHOW_TOAST";

    public static Intent getIntent(Context context, boolean showToast) {
        Intent startIntent = new Intent(context.getApplicationContext(), ClipboardFloatingActivity.class);
        startIntent.putExtra(KEY_SHOW_TOAST, showToast);
        startIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return startIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clipboard_floating);
        WindowManager.LayoutParams wlp = getWindow().getAttributes();
        wlp.width = 1;
        wlp.height = 1;
        wlp.gravity = Gravity.TOP | Gravity.START;
        wlp.dimAmount = 0;
        wlp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        getWindow().setAttributes(wlp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When in onResume, this activity is in foreground, granting Clipboard access
        ClipboardListener.instance(this).onClipboardChanged();
        if (shouldShowToast()) {
            Toast.makeText(this, R.string.pref_plugin_clipboard_sent, Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ClipboardListener.instance(this).onClipboardChanged();
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private boolean shouldShowToast() {
        return getIntent().getBooleanExtra(KEY_SHOW_TOAST, false);
    }
}
