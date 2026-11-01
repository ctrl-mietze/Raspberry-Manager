package dev.mietze.piimager.data

data class CustomizationSettings(
    val enabled: Boolean = true,
    val hostname: String = "raspberrypi",
    val username: String = "pi",
    val password: String = "",
    val passwordlessSudo: Boolean = false,
    val wifiEnabled: Boolean = false,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiHidden: Boolean = false,
    val wifiOpen: Boolean = false,
    val wifiCountry: String = "DE",
    val timezone: String = "Europe/Berlin",
    val keyboard: String = "de",
    val sshEnabled: Boolean = false,
    val sshPasswordAuth: Boolean = true,
    val sshAuthorizedKeys: String = "",
    val piConnectEnabled: Boolean = false,
    val piConnectToken: String = "",
    val enableI2C: Boolean = false,
    val enableSPI: Boolean = false,
    val enable1Wire: Boolean = false,
    val enableUsbGadget: Boolean = false,
    val serialMode: String = "Disabled"
) {
    fun hasAnyContent(): Boolean =
        enabled && (
            hostname.isNotBlank() ||
            username.isNotBlank() ||
            password.isNotBlank() ||
            wifiEnabled ||
            sshEnabled ||
            piConnectEnabled ||
            enableI2C ||
            enableSPI ||
            enable1Wire ||
            enableUsbGadget ||
            serialMode != "Disabled" ||
            timezone.isNotBlank() ||
            keyboard.isNotBlank()
        )
}
