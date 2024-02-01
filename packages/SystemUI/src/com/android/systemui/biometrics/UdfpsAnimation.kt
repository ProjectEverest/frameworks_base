/**
 * Copyright (C) 2024 The risingOS Android Project
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
package com.android.systemui.biometrics

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import android.util.DisplayUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.android.systemui.Dependency
import com.android.systemui.R
import com.android.systemui.biometrics.AuthController
import com.android.systemui.doze.util.getBurnInOffset
import com.android.systemui.plugins.statusbar.StatusBarStateController

class UdfpsAnimation(context: Context, private val windowManager: WindowManager, private val authController: AuthController) :
    ImageView(context) {
    companion object {
        private const val DEBUG = true
        private const val LOG_TAG = "UdfpsAnimations"
        private const val UDFPS_ANIMATIONS_PACKAGE = "com.everest.udfps.resources"
    }

    private val scaleFactor: Float = DisplayUtils.getScaleFactor(context)
    private var mEnabled = false
    private var mShowing = false
    private val mContext: Context = context
    private val mAnimationSize: Int = context.resources.getDimensionPixelSize(R.dimen.udfps_animation_size)
    private val mAnimationOffset: Int =
        (context.resources.getDimensionPixelSize(R.dimen.udfps_animation_offset) *
                DisplayUtils.getScaleFactor(mContext)
        ).toInt()
    private val mAnimParams =
        WindowManager.LayoutParams(
            mAnimationSize,
            mAnimationSize,
            WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER
        }

    private var mIsKeyguard = false
    private val mMaxBurnInOffsetX: Int =
        (context.resources.getDimensionPixelSize(R.dimen.udfps_burn_in_offset_x) *
                DisplayUtils.getScaleFactor(mContext)
        ).toInt()
    private val mMaxBurnInOffsetY: Int =
        (context.resources.getDimensionPixelSize(R.dimen.udfps_burn_in_offset_y) *
                DisplayUtils.getScaleFactor(mContext)
        ).toInt()

    private var mStyleNames: Array<String>

    private val mApkResources: Resources by lazy {
        try {
            context.packageManager.getResourcesForApplication(UDFPS_ANIMATIONS_PACKAGE)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            resources
        }
    }

    private var recognizingAnim: AnimationDrawable? = null

    init {
        updatePosition()
        val res = mApkResources.getIdentifier("udfps_animation_styles", "array", UDFPS_ANIMATIONS_PACKAGE)
        mStyleNames = mApkResources.getStringArray(res)

        scaleType = ImageView.ScaleType.CENTER_INSIDE

        val udfpsAnimEnabled = Settings.System.getUriFor(Settings.System.UDFPS_ANIM)
        val udfpsAnimStyle = Settings.System.getUriFor(Settings.System.UDFPS_ANIM_STYLE)
        val contentObserver =
            object : ContentObserver(null) {
                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri,
                ) {
                    mEnabled =
                        Settings.System.getIntForUser(
                            mContext.contentResolver,
                            Settings.System.UDFPS_ANIM,
                            0,
                            UserHandle.USER_CURRENT,
                        ) != 0
                    val value =
                        Settings.System.getIntForUser(
                            mContext.contentResolver,
                            Settings.System.UDFPS_ANIM_STYLE,
                            0,
                            UserHandle.USER_CURRENT,
                        )
                    val style = if (value < 0 || value >= mStyleNames.size) 0 else value
                    mContext.mainExecutor.execute {
                        updateAnimationStyle(style)
                    }
                }
            }
        mContext.contentResolver.registerContentObserver(
            udfpsAnimEnabled,
            false,
            contentObserver,
            UserHandle.USER_CURRENT,
        )
        mContext.contentResolver.registerContentObserver(
            udfpsAnimStyle,
            false,
            contentObserver,
            UserHandle.USER_CURRENT,
        )
        contentObserver.onChange(true, udfpsAnimEnabled)
        contentObserver.onChange(true, udfpsAnimStyle)
    }

    fun lerp(
        start: Float,
        stop: Float,
        amount: Float,
    ): Float {
        return start + amount * (stop - start)
    }

    private fun updateAnimationStyle(styleIdx: Int) {
        val bgDrawable = getBgDrawable(styleIdx)
        background = if (mEnabled) bgDrawable else null
        recognizingAnim = bgDrawable as? AnimationDrawable
    }

    private fun getBgDrawable(styleIdx: Int): Drawable? {
        val drawableName = mStyleNames[styleIdx]
        if (DEBUG) Log.i(LOG_TAG, "Updating animation style to:$drawableName")
        return try {
            val resId = mApkResources.getIdentifier(drawableName, "drawable", UDFPS_ANIMATIONS_PACKAGE)
            if (DEBUG) Log.i(LOG_TAG, "Got resource id: $resId from package")
            mApkResources.getDrawable(resId)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    fun isAnimationEnabled(): Boolean = recognizingAnim != null && authController.getUdfpsLocation() != null && mEnabled

    fun updatePosition() {
        val udfpsLocation = authController.getUdfpsLocation()
        val udfpsRadius = authController.getUdfpsRadius()

        if (udfpsLocation != null && udfpsRadius != null) {
            val yPosition = (
                (udfpsLocation.y * scaleFactor)
                        - (udfpsRadius * scaleFactor)
                        - mAnimationSize / 2
                ).toInt()
            mAnimParams.y = yPosition
            if (DEBUG) Log.d(LOG_TAG + ":updatePosition", "mAnimationSize: " + mAnimationSize + "scaleFactor: " 
                    + scaleFactor + "udfpsLocation: " + udfpsLocation + "udfpsRadius: " + udfpsRadius)
            if (mShowing) {
                showAnimation()
            }
        }
    }

    fun show() {
        if (!mShowing && mIsKeyguard && isAnimationEnabled()) {
            mShowing = true
            showAnimation()
        }
    }

    private fun showAnimation() {
        try {
            if (windowToken == null) {
                windowManager.addView(this, mAnimParams)
            } else {
                windowManager.updateViewLayout(this, mAnimParams)
            }
        } catch (e: RuntimeException) {
            // Ignore
        }
        recognizingAnim?.start()
    }

    fun hide() {
        if (mShowing) {
            mShowing = false
            recognizingAnim?.let {
                clearAnimation()
                it.stop()
                it.selectDrawable(0)
            }
            if (windowToken != null) {
                windowManager.removeView(this)
            }
        }
    }

    fun setIsKeyguard(isKeyguard: Boolean) {
        mIsKeyguard = isKeyguard
    }

    fun dozeTimeTick() {
        val amt = Dependency.get(StatusBarStateController::class.java).dozeAmount

        val mBurnInOffsetX =
            lerp(
                0f,
                getBurnInOffset(mMaxBurnInOffsetX * 2, true).toFloat(),
                amt,
            ) - mMaxBurnInOffsetX
        val mBurnInOffsetY =
            lerp(
                0f,
                getBurnInOffset(mMaxBurnInOffsetY * 2, false).toFloat(),
                amt,
            ) - mMaxBurnInOffsetY
        translationX = mBurnInOffsetX
        translationY = mBurnInOffsetY
    }
}
