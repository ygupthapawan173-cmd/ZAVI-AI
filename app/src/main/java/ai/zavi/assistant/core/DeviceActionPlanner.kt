package ai.zavi.assistant.core

import java.util.Locale

object DeviceActionPlanner {
    private val callPattern = Regex(
        """^(?:call|dial|phone)s+([+0-9][+0-9() .-]{4,})$""",
        RegexOption.IGNORE_CASE
    )
    private val smsPattern = Regex(
        """^(?:sms|text|send sms)s+(?:tos+)?([+0-9][+0-9() .-]{4,})s+(?:saying|message|:)s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val whatsappPattern = Regex(
        """^(?:whatsapp|send whatsapp)s+(?:tos+)?([+0-9][+0-9() .-]{4,})s+(?:saying|message|:)s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun plan(command: String): DeviceAction? {
        val input = command.trim()
        if (input.isBlank()) return null

        whatsappPattern.matchEntire(input)?.let { match ->
            return DeviceAction.OpenWhatsApp(
                number = digitsAndPlus(match.groupValues[1]),
                message = match.groupValues[2].trim()
            )
        }
        smsPattern.matchEntire(input)?.let { match ->
            return DeviceAction.ComposeSms(
                number = digitsAndPlus(match.groupValues[1]),
                message = match.groupValues[2].trim()
            )
        }
        callPattern.matchEntire(input)?.let { match ->
            return DeviceAction.Dial(digitsAndPlus(match.groupValues[1]))
        }

        val lower = input.lowercase(Locale.ROOT)
        val destinationPrefixes = listOf("navigate to ", "directions to ", "take me to ", "maps to ")
        destinationPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val destination = input.drop(prefix.length).trim()
            if (destination.isNotBlank()) return DeviceAction.Navigate(destination)
        }

        if (lower in listOf("open camera", "launch camera", "camera open")) {
            return DeviceAction.OpenCamera
        }

        val appMap = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps"
        )
        if (lower.startsWith("open ")) {
            val appName = lower.removePrefix("open ").trim()
            appMap[appName]?.let { return DeviceAction.OpenApp(appName, it) }
            when (appName) {
                "wifi settings", "wi-fi settings", "turn on wifi", "turn off wifi" ->
                    return DeviceAction.OpenSettings("android.settings.WIFI_SETTINGS", "Wi-Fi settings")
                "bluetooth settings", "turn on bluetooth", "turn off bluetooth" ->
                    return DeviceAction.OpenSettings("android.settings.BLUETOOTH_SETTINGS", "Bluetooth settings")
                "sound settings", "volume settings" ->
                    return DeviceAction.OpenSettings("android.settings.SOUND_SETTINGS", "Sound settings")
                "display settings", "brightness settings" ->
                    return DeviceAction.OpenSettings("android.settings.DISPLAY_SETTINGS", "Display settings")
            }
        }

        if (lower in listOf("turn on wifi", "turn off wifi", "wifi on", "wifi off")) {
            return DeviceAction.OpenSettings("android.settings.WIFI_SETTINGS", "Wi-Fi settings")
        }
        if (lower in listOf("turn on bluetooth", "turn off bluetooth", "bluetooth on", "bluetooth off")) {
            return DeviceAction.OpenSettings("android.settings.BLUETOOTH_SETTINGS", "Bluetooth settings")
        }
        return null
    }

    private fun digitsAndPlus(value: String): String =
        value.filterIndexed { index, char -> char.isDigit() || (char == '+' && index == 0) }
}
