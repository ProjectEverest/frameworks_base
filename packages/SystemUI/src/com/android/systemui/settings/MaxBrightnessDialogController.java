/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.settings.brightness.BrightnessController;
import com.android.systemui.statusbar.phone.SystemUIDialog;

public class MaxBrightnessDialogController implements BrightnessController.OnMaxBrightnessCallback {
    private static final String TAG = MaxBrightnessDialogController.class.getSimpleName();

    private final ActivityStarter mActivityStarter;
    private final Context mContext;
    private boolean mHasShown = false;

    public MaxBrightnessDialogController(Context context, ActivityStarter activityStarter) {
        this.mContext = context;
        this.mActivityStarter = activityStarter;
    }

    /**
     *  Max brightness callback, called when max brightness has been set
     */
    @Override
    public void onMaxBrightness() {
        if (!mHasShown && !isBrightnessModeAutomatic()
                && mContext.getResources().getBoolean(R.bool.config_enableMaxBrightnessDialog)) {
            show();
        }
    }

    /**
        Show the Max brightness dialog to the user
     */
    public void show() {
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(R.string.oled_max_brightness_dialog_title_txt);
        dialog.setMessage(R.string.oled_max_brightness_dialog_body_txt);
        dialog.setPositiveButton(R.string.oled_max_brightness_dialog_yes_button_txt,
                (d, w) -> {
                    mHasShown = true;
                    startSettingsActivity();
                });
        dialog.setNegativeButton(R.string.oled_max_brightness_dialog_no_button_txt,
                (d, w) -> mHasShown = true);
        dialog.show();
    }

    private boolean isBrightnessModeAutomatic() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                UserHandle.USER_CURRENT)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    private void startSettingsActivity() {
        mActivityStarter.postStartActivityDismissingKeyguard(
                new Intent(Settings.ACTION_DISPLAY_SETTINGS), 0);
    }
}
