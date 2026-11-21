package ai.zavi.assistant.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object DeviceActionLauncher {
    fun launch(activity: Activity, action: DeviceAction): String {
        return try {
            val intent = when (action) {
                is DeviceAction.Dial ->
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(action.number)}"))

                is DeviceAction.ComposeSms ->
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(action.number)}"))
                        .putExtra("sms_body", action.message)

                is DeviceAction.OpenWhatsApp -> {
                    val digits = action.number.filter { it.isDigit() }
                    val encoded = URLEncoder.encode(action.message, StandardCharsets.UTF_8.name())
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=$encoded"))
                }

                is DeviceAction.Navigate ->
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("google.navigation:q=${Uri.encode(action.destination)}")
                    )

                is DeviceAction.OpenApp ->
                    activity.packageManager.getLaunchIntentForPackage(action.packageName)
                        ?: return "ఈ ఫోన్‌లో ${action.appName} app install అయి లేదు."

                is DeviceAction.OpenSettings ->
                    Intent(action.settingsAction)

                DeviceAction.OpenCamera ->
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            }

            if (intent.resolveActivity(activity.packageManager) == null) {
                if (action is DeviceAction.Navigate) {
                    val fallback = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=${Uri.encode(action.destination)}")
                    )
                    if (fallback.resolveActivity(activity.packageManager) != null) {
                        activity.startActivity(fallback)
                        return "Maps search తెరిచాను. Destination, route check చేయండి."
                    }
                }
                return "ఈ action కోసం అవసరమైన Android app అందుబాటులో లేదు."
            }

            activity.startActivity(intent)
            when (action) {
                is DeviceAction.Dial -> "Dialer తెరిచాను. Call చేయడానికి నువ్వే tap చేయాలి."
                is DeviceAction.ComposeSms -> "SMS draft తెరిచాను. Message verify చేసి నువ్వే Send చేయాలి."
                is DeviceAction.OpenWhatsApp -> "WhatsApp message draft తెరిచాను. Verify చేసి నువ్వే Send చేయాలి."
                is DeviceAction.Navigate -> "Maps navigation తెరిచాను. Route verify చేయండి."
                is DeviceAction.OpenApp -> "${action.appName} తెరిచాను."
                is DeviceAction.OpenSettings -> "${action.settingsName} తెరిచాను."
                DeviceAction.OpenCamera -> "Camera తెరిచాను."
            }
        } catch (_: ActivityNotFoundException) {
            "ఈ action కోసం compatible Android app దొరకలేదు."
        } catch (error: Exception) {
            error.message ?: "Android action ప్రారంభించలేకపోయాను."
        }
    }
}
