package ai.zavi.assistant.core

sealed class DeviceAction(
    open val title: String,
    open val description: String
) {
    data class Dial(val number: String) : DeviceAction(
        "Open phone dialer",
        "The dialer will open with this number. You must tap Call yourself."
    )

    data class ComposeSms(val number: String, val message: String) : DeviceAction(
        "Prepare SMS",
        "Your SMS app will open with a draft. Review it and tap Send yourself."
    )

    data class OpenWhatsApp(val number: String, val message: String) : DeviceAction(
        "Prepare WhatsApp message",
        "WhatsApp will open with a draft message. Review it and tap Send yourself."
    )

    data class Navigate(val destination: String) : DeviceAction(
        "Open navigation",
        "Google Maps will open with this destination. Review the route before starting."
    )

    data class OpenApp(val appName: String, val packageName: String) : DeviceAction(
        "Open $appName",
        "ZAVI will ask Android to open the installed app."
    )

    data class OpenSettings(val settingsAction: String, val settingsName: String) : DeviceAction(
        "Open $settingsName",
        "Android Settings will open. You control the setting there."
    )

    data object OpenCamera : DeviceAction(
        "Open camera",
        "The installed camera app will open. Capture a photo only when you choose."
    )
}
