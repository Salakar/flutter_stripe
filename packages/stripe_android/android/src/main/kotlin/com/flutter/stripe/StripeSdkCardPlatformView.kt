package com.flutter.stripe

import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.reactnativestripesdk.StripeSdkCardView
import com.reactnativestripesdk.StripeSdkCardViewManager
import com.stripe.android.databinding.CardInputWidgetBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView


class StripeSdkCardPlatformView(
        private val context: Context,
        private val channel: MethodChannel,
        id: Int,
        private val creationParams: Map<String?, Any?>?,
        private val stripeSdkCardViewManager: StripeSdkCardViewManager
) : PlatformView, MethodChannel.MethodCallHandler {

    lateinit var cardView: StripeSdkCardView

    init {
        cardView =  stripeSdkCardViewManager.getCardViewInstance() ?: let {
            return@let stripeSdkCardViewManager.createViewInstance(ThemedReactContext(context, channel))
        }
        channel.setMethodCallHandler(this)
        if (creationParams?.containsKey("cardStyle") == true) {
            val cardStyle =
            stripeSdkCardViewManager.setCardStyle(cardView, ReadableMap(creationParams["cardStyle"] as Map<String, Any>))
        }
        if (creationParams?.containsKey("placeholder") == true) {
            stripeSdkCardViewManager.setPlaceHolders(cardView, ReadableMap(creationParams["placeholder"] as Map<String, Any>))
        }
        if (creationParams?.containsKey("postalCodeEnabled") == true) {
            stripeSdkCardViewManager.setPostalCodeEnabled(cardView, creationParams["postalCodeEnabled"] as Boolean)
        }
        applyFocusFix()
    }

    /**
     * https://github.com/flutter-stripe/flutter_stripe/issues/14
     * https://github.com/flutter/engine/pull/26602 HC_PLATFORM_VIEW was introduced in
     * that PR - we're checking for its availability and apply the old fix accordingly
     */
    private fun applyFocusFix() {
        try {
            val enumConstants = Class.forName("io.flutter.plugin.editing.TextInputPlugin\$InputTarget\$Type").enumConstants as Array<Enum<*>>
            val shouldApplyFix = enumConstants.none { it.name == "HC_PLATFORM_VIEW" }
            if (shouldApplyFix) {
                // Temporal fix to https://github.com/flutter/flutter/issues/81029
                val binding = CardInputWidgetBinding.bind(cardView.mCardWidget)
                binding.cardNumberEditText.inputType = InputType.TYPE_CLASS_TEXT
                binding.cvcEditText.inputType = InputType.TYPE_CLASS_TEXT
                binding.expiryDateEditText.inputType = InputType.TYPE_CLASS_TEXT
            }
        } catch (e: Exception) {
            Log.e("Stripe Plugin", "Error", e)
        }
    }

    override fun getView(): View {
        return cardView
    }

    override fun dispose() {
        stripeSdkCardViewManager.getCardViewInstance()?.let {
            stripeSdkCardViewManager.onDropViewInstance(it)
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {

        when (call.method) {
            "onStyleChanged" -> {
                val arguments = ReadableMap(call.arguments as Map<String, Any>)
                stripeSdkCardViewManager.setCardStyle(cardView, arguments.getMap("cardStyle") as  ReadableMap)
                result.success(null)
            }
            "onPlaceholderChanged" -> {
                val arguments = ReadableMap(call.arguments as Map<String, Any>)
                stripeSdkCardViewManager.setPlaceHolders(cardView,  arguments.getMap("placeholder") as ReadableMap )
                result.success(null)
            }
            "onPostalCodeEnabledChanged" -> {
                val arguments = ReadableMap(call.arguments as Map<String, Any>)
                stripeSdkCardViewManager.setPostalCodeEnabled(cardView, arguments.getBoolean("postalCodeEnabled") as Boolean)
                result.success(null)
            }
            "requestFocus" -> {
                val binding = CardInputWidgetBinding.bind(cardView.mCardWidget)
                binding.cardNumberEditText.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                result.success(null)
            }
            "clearFocus" -> {
                // Hide keyboard
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(cardView.getWindowToken(), 0)
                // Clear focus
                cardView.clearFocus()
                result.success(null)
            }
        }
    }

}