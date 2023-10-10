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

package com.android.systemui.settings

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.UserHandle
import android.provider.Settings
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import androidx.test.filters.SmallTest
import com.android.systemui.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.util.mockito.any
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class MaxBrightnessDialogControllerTest : SysuiTestCase() {

    @Mock private lateinit var context: Context
    @Mock private lateinit var activityStarter: ActivityStarter

    private lateinit var dialogController: MaxBrightnessDialogController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        dialogController = MaxBrightnessDialogController(context, activityStarter)
    }

    @Test
    fun testShowDialog() {
        // Mocking the necessary resources and settings
        whenever(context.getResources()).thenReturn(mock(Resources::class.java) as Resources)
        whenever(context.getResources().getBoolean(R.bool.config_enableMaxBrightnessDialog))
            .thenReturn(true)
        whenever(
                Settings.System.getIntForUser(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    UserHandle.USER_CURRENT
                )
            )
            .thenReturn(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

        // Triggering onMaxBrightness to show the dialog
        dialogController.onMaxBrightness()

        // Verifying that the dialog is shown
        verify(context).getString(R.string.oled_max_brightness_dialog_title_txt)
        verify(context).getString(R.string.oled_max_brightness_dialog_body_txt)
        verify(context).getString(R.string.oled_max_brightness_dialog_yes_button_txt)
        verify(context).getString(R.string.oled_max_brightness_dialog_no_button_txt)

        verify(activityStarter).postStartActivityDismissingKeyguard(any(Intent::class.java), eq(0))
    }

    @Test
    fun testNoDialogWhenAutomaticBrightness() {
        // Mocking the necessary resources and settings
        whenever(context.getResources()).thenReturn(mock(Resources::class.java) as Resources)
        whenever(context.getResources().getBoolean(R.bool.config_enableMaxBrightnessDialog))
            .thenReturn(true)
        whenever(
                Settings.System.getIntForUser(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    UserHandle.USER_CURRENT
                )
            )
            .thenReturn(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        // Triggering onMaxBrightness, but it shouldn't show the dialog due to automatic brightness
        dialogController.onMaxBrightness()

        // Verifying that the dialog is not shown
        verify(context, never()).getString(anyInt())
        verify(activityStarter, never())
            .postStartActivityDismissingKeyguard(any(Intent::class.java), anyInt())
    }
}
