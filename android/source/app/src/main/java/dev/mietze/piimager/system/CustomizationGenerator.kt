package dev.mietze.piimager.system

import dev.mietze.piimager.data.CustomizationSettings
import org.apache.commons.codec.digest.Sha2Crypt
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CustomizationPayload(
    val files: Map<String, ByteArray>,
    val cmdlineAppend: String = ""
)

object CustomizationGenerator {
    private val random = SecureRandom()
    private const val SALT_CHARS =
        "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun generate(initFormat: String?, settings: CustomizationSettings): CustomizationPayload {
        if (!settings.enabled || !settings.hasAnyContent()) return CustomizationPayload(emptyMap())
        return when (initFormat.orEmpty()) {
            "systemd" -> systemd(settings)
            "cloudinit", "cloudinit-rpi" -> cloudInit(settings, initFormat == "cloudinit-rpi")
            "rpi-preseed" -> rpiPreseed(settings)
            else -> CustomizationPayload(emptyMap())
        }
    }

    private fun passwordHash(password: String): String {
        if (password.isBlank()) return ""
        val clean = password.replace("\r", "").replace("\n", "")
        val salt = buildString {
            append("\$5\$")
            repeat(10) { append(SALT_CHARS[random.nextInt(SALT_CHARS.length)]) }
        }
        return Sha2Crypt.sha256Crypt(clean.toByteArray(Charsets.UTF_8), salt)
    }

    private fun wifiPsk(password: String, ssid: String): String {
        val clean = password.replace("\r", "").replace("\n", "")
        if (clean.isBlank()) return ""
        if (clean.length == 64 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return clean.lowercase()
        }
        if (clean.length !in 8..63) return clean
        return pbkdf2Sha1(clean.toByteArray(Charsets.UTF_8), ssid.toByteArray(Charsets.UTF_8), 4096, 32)
            .joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2Sha1(password: ByteArray, salt: ByteArray, iterations: Int, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(password, "HmacSHA1"))
        val hLen = mac.macLength
        val blocks = (outLen + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        var pos = 0
        for (block in 1..blocks) {
            mac.reset()
            mac.update(salt)
            mac.update(byteArrayOf(
                (block ushr 24).toByte(),
                (block ushr 16).toByte(),
                (block ushr 8).toByte(),
                block.toByte()
            ))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                mac.reset()
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, pos, hLen)
            pos += hLen
        }
        return out.copyOf(outLen)
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun yaml(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun toml(value: String): String =
        "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "") + "\""

    private fun country(value: String): String {
        val c = value.trim().uppercase()
        return if (c.length == 2 && c.all { it in 'A'..'Z' }) c else ""
    }

    private fun keyLines(settings: CustomizationSettings): List<String> =
        settings.sshAuthorizedKeys.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun systemd(s: CustomizationSettings): CustomizationPayload {
        val script = StringBuilder()
        val hash = passwordHash(s.password)
        val user = s.username.trim().ifBlank { "pi" }
        val keys = keyLines(s)
        val cc = country(s.wifiCountry)
        val psk = if (s.wifiOpen) "" else wifiPsk(s.wifiPassword, s.wifiSsid)

        script.appendLine("#!/bin/sh")
        script.appendLine("set +e")
        script.appendLine("FIRSTUSER=\$(getent passwd 1000 | cut -d: -f1)")
        script.appendLine("FIRSTUSERHOME=\$(getent passwd 1000 | cut -d: -f6)")

        if (s.hostname.isNotBlank()) {
            val h = s.hostname.replace("\r", "").replace("\n", "")
            script.appendLine("IMAGER_HOSTNAME=" + shellQuote(h))
            script.appendLine("if [ -x /usr/lib/raspberrypi-sys-mods/imager_custom ]; then")
            script.appendLine("  /usr/lib/raspberrypi-sys-mods/imager_custom set_hostname \"\$IMAGER_HOSTNAME\"")
            script.appendLine("else")
            script.appendLine("  hostnamectl set-hostname \"\$IMAGER_HOSTNAME\" 2>/dev/null || echo \"\$IMAGER_HOSTNAME\" >/etc/hostname")
            script.appendLine("fi")
        }

        if (s.username.isNotBlank() || hash.isNotBlank() || keys.isNotEmpty()) {
            script.appendLine("IMAGER_USER=" + shellQuote(user))
            if (hash.isNotBlank()) script.appendLine("IMAGER_PASS=" + shellQuote(hash))
            script.appendLine("if [ -x /usr/lib/userconf-pi/userconf ]; then")
            script.appendLine("  /usr/lib/userconf-pi/userconf \"\$IMAGER_USER\" " + shellQuote(hash))
            script.appendLine("else")
            if (hash.isNotBlank()) script.appendLine("  echo \"\$FIRSTUSER:\$IMAGER_PASS\" | chpasswd -e")
            script.appendLine("  if [ \"\$FIRSTUSER\" != \"\$IMAGER_USER\" ]; then")
            script.appendLine("    usermod -l \"\$IMAGER_USER\" \"\$FIRSTUSER\"")
            script.appendLine("    usermod -m -d \"/home/\$IMAGER_USER\" \"\$IMAGER_USER\"")
            script.appendLine("    groupmod -n \"\$IMAGER_USER\" \"\$FIRSTUSER\"")
            script.appendLine("  fi")
            script.appendLine("fi")
        }

        if (s.passwordlessSudo) {
            val sudo = "/etc/sudoers.d/010_" + user + "-nopasswd"
            script.appendLine("echo " + shellQuote(user + " ALL=(ALL) NOPASSWD:ALL") + " >" + shellQuote(sudo))
            script.appendLine("chmod 0440 " + shellQuote(sudo))
        }

        if (s.sshEnabled) {
            script.appendLine("systemctl enable ssh 2>/dev/null || true")
            if (keys.isNotEmpty()) {
                script.appendLine("TARGET_HOME=\$(getent passwd " + shellQuote(user) + " | cut -d: -f6)")
                script.appendLine("[ -n \"\$TARGET_HOME\" ] || TARGET_HOME=/home/" + shellQuote(user))
                script.appendLine("install -o " + shellQuote(user) + " -m 700 -d \"\$TARGET_HOME/.ssh\"")
                val encoded = keys.joinToString("\n").toByteArray().let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
                script.appendLine("echo " + shellQuote(encoded) + " | base64 -d > \"\$TARGET_HOME/.ssh/authorized_keys\"")
                script.appendLine("chown " + shellQuote(user + ":" + user) + " \"\$TARGET_HOME/.ssh/authorized_keys\"")
                script.appendLine("chmod 600 \"\$TARGET_HOME/.ssh/authorized_keys\"")
            }
            script.appendLine("mkdir -p /etc/ssh/sshd_config.d")
            script.appendLine("printf '%s\\n' 'PasswordAuthentication " + if (s.sshPasswordAuth) "yes" else "no" + "' >/etc/ssh/sshd_config.d/10-rpi-imager.conf")
        }

        if (s.wifiEnabled && s.wifiSsid.isNotBlank()) {
            val hidden = if (s.wifiHidden) " -h " else " "
            script.appendLine("if [ -x /usr/lib/raspberrypi-sys-mods/imager_custom ]; then")
            script.appendLine("  /usr/lib/raspberrypi-sys-mods/imager_custom set_wlan" + hidden +
                shellQuote(s.wifiSsid) + " " + shellQuote(psk) + " " + shellQuote(cc))
            script.appendLine("else")
            script.appendLine("  mkdir -p /etc/wpa_supplicant")
            script.appendLine("  cat >/etc/wpa_supplicant/wpa_supplicant.conf <<'WPAEOF'")
            if (cc.isNotBlank()) script.appendLine("country=" + cc)
            script.appendLine("ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev")
            script.appendLine("update_config=1")
            script.appendLine("network={")
            script.appendLine("  ssid=\"" + s.wifiSsid.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            if (s.wifiHidden) script.appendLine("  scan_ssid=1")
            if (s.wifiOpen) {
                script.appendLine("  key_mgmt=NONE")
            } else {
                script.appendLine("  key_mgmt=WPA-PSK SAE")
                script.appendLine("  psk=" + psk)
                script.appendLine("  ieee80211w=1")
            }
            script.appendLine("}")
            script.appendLine("WPAEOF")
            script.appendLine("  chmod 600 /etc/wpa_supplicant/wpa_supplicant.conf")
            script.appendLine("fi")
            script.appendLine("rfkill unblock wifi 2>/dev/null || true")
        }

        if (s.timezone.isNotBlank()) {
            script.appendLine("if [ -x /usr/lib/raspberrypi-sys-mods/imager_custom ]; then")
            script.appendLine("  /usr/lib/raspberrypi-sys-mods/imager_custom set_timezone " + shellQuote(s.timezone))
            script.appendLine("else timedatectl set-timezone " + shellQuote(s.timezone) + " 2>/dev/null || true; fi")
        }
        if (s.keyboard.isNotBlank()) {
            script.appendLine("if [ -x /usr/lib/raspberrypi-sys-mods/imager_custom ]; then")
            script.appendLine("  /usr/lib/raspberrypi-sys-mods/imager_custom set_keymap " + shellQuote(s.keyboard))
            script.appendLine("fi")
        }

        if (s.piConnectEnabled && s.piConnectToken.isNotBlank()) {
            val cfg = "/home/" + user + "/.config/com.raspberrypi.connect"
            script.appendLine("install -o " + shellQuote(user) + " -m 700 -d " + shellQuote(cfg))
            script.appendLine("printf '%s\\n' " + shellQuote(s.piConnectToken.trim()) + " >" + shellQuote(cfg + "/auth.key"))
            script.appendLine("chown " + shellQuote(user + ":" + user) + " " + shellQuote(cfg + "/auth.key"))
            script.appendLine("chmod 600 " + shellQuote(cfg + "/auth.key"))
            script.appendLine("loginctl enable-linger " + shellQuote(user) + " 2>/dev/null || true")
        }

        script.appendLine("rm -f /boot/firstrun.sh /boot/firmware/firstrun.sh")
        script.appendLine("sed -i 's| systemd.run=.*||g' /boot/cmdline.txt 2>/dev/null || true")
        script.appendLine("sed -i 's| systemd.run=.*||g' /boot/firmware/cmdline.txt 2>/dev/null || true")
        script.appendLine("exit 0")

        var cmd = " systemd.run=/boot/firstrun.sh systemd.run_success_action=reboot systemd.unit=kernel-command-line.target"
        if (cc.isNotBlank()) cmd += " cfg80211.ieee80211_regdom=" + cc
        return CustomizationPayload(mapOf("firstrun.sh" to script.toString().toByteArray()), cmd)
    }

    private fun cloudInit(s: CustomizationSettings, hasCcRpi: Boolean): CustomizationPayload {
        val y = StringBuilder()
        val user = s.username.trim().ifBlank { "pi" }
        val hash = passwordHash(s.password)
        val keys = keyLines(s)
        val cc = country(s.wifiCountry)

        y.appendLine("#cloud-config")
        y.appendLine("manage_resolv_conf: false")
        if (s.hostname.isNotBlank()) {
            y.appendLine("hostname: \"" + yaml(s.hostname) + "\"")
            y.appendLine("manage_etc_hosts: true")
            y.appendLine("packages:")
            y.appendLine("  - avahi-daemon")
            y.appendLine("apt:")
            y.appendLine("  preserve_sources_list: true")
            y.appendLine("  conf: |")
            y.appendLine("    Acquire {")
            y.appendLine("      Check-Date \"false\";")
            y.appendLine("    };")
        }
        if (s.timezone.isNotBlank()) y.appendLine("timezone: \"" + yaml(s.timezone) + "\"")
        if (s.keyboard.isNotBlank()) {
            y.appendLine("keyboard:")
            y.appendLine("  model: pc105")
            y.appendLine("  layout: \"" + yaml(s.keyboard) + "\"")
        }

        if (s.username.isNotBlank() || hash.isNotBlank() || keys.isNotEmpty()) {
            y.appendLine("user:")
            y.appendLine("  name: \"" + yaml(user) + "\"")
            y.appendLine("  shell: /bin/bash")
            if (hash.isNotBlank()) {
                y.appendLine("  lock_passwd: false")
                y.appendLine("  passwd: \"" + yaml(hash) + "\"")
            } else if (keys.isNotEmpty()) {
                y.appendLine("  lock_passwd: true")
            }
            if (keys.isNotEmpty() && s.sshEnabled) {
                y.appendLine("  ssh_authorized_keys:")
                keys.forEach { y.appendLine("    - \"" + yaml(it) + "\"") }
            }
            if (s.passwordlessSudo) y.appendLine("  sudo: ALL=(ALL) NOPASSWD:ALL")
            else y.appendLine("  sudo: null")
        }

        if (s.sshEnabled) y.appendLine("ssh_pwauth: " + if (s.sshPasswordAuth) "true" else "false")

        if (hasCcRpi && (s.enableI2C || s.enableSPI || s.enable1Wire || s.enableUsbGadget || s.serialMode != "Disabled")) {
            y.appendLine("rpi:")
            if (s.enableUsbGadget) y.appendLine("  enable_usb_gadget: true")
            if (s.enableI2C || s.enableSPI || s.enable1Wire || s.serialMode != "Disabled") {
                y.appendLine("  interfaces:")
                if (s.enableI2C) y.appendLine("    i2c: true")
                if (s.enableSPI) y.appendLine("    spi: true")
                if (s.enable1Wire) y.appendLine("    onewire: true")
                when (s.serialMode) {
                    "Default" -> y.appendLine("    serial: true")
                    "Console" -> { y.appendLine("    serial:"); y.appendLine("      console: true"); y.appendLine("      hardware: false") }
                    "Hardware" -> { y.appendLine("    serial:"); y.appendLine("      console: false"); y.appendLine("      hardware: true") }
                    "Console & Hardware" -> { y.appendLine("    serial:"); y.appendLine("      console: true"); y.appendLine("      hardware: true") }
                }
            }
        }

        val commands = mutableListOf<String>()
        if (s.sshEnabled) commands += "systemctl enable --now ssh"
        if (s.passwordlessSudo) {
            val sudo = "/etc/sudoers.d/010_" + user + "-nopasswd"
            commands += "echo " + shellQuote(user + " ALL=(ALL) NOPASSWD:ALL") + " >" + shellQuote(sudo)
            commands += "chmod 0440 " + shellQuote(sudo)
        }
        if (s.piConnectEnabled && s.piConnectToken.isNotBlank()) {
            val cfg = "/home/" + user + "/.config/com.raspberrypi.connect"
            commands += "install -o " + shellQuote(user) + " -m 700 -d " + shellQuote(cfg)
            commands += "printf '%s\\n' " + shellQuote(s.piConnectToken.trim()) + " >" + shellQuote(cfg + "/auth.key") +
                " && chown " + shellQuote(user + ":" + user) + " " + shellQuote(cfg + "/auth.key") +
                " && chmod 600 " + shellQuote(cfg + "/auth.key")
            commands += "loginctl enable-linger " + shellQuote(user) + " 2>/dev/null || true"
        }
        if (cc.isNotBlank() && (!s.wifiEnabled || s.wifiSsid.isBlank())) {
            commands += "rfkill unblock wifi 2>/dev/null || true"
        }
        if (commands.isNotEmpty()) {
            y.appendLine("runcmd:")
            commands.forEach { y.appendLine("  - [ sh, -c, \"" + yaml(it) + "\" ]") }
        }

        val files = linkedMapOf<String, ByteArray>()
        files["user-data"] = y.toString().toByteArray()

        if (s.wifiEnabled && s.wifiSsid.isNotBlank()) {
            val n = StringBuilder()
            n.appendLine("network:")
            n.appendLine("  version: 2")
            n.appendLine("  ethernets:")
            n.appendLine("    eth0:")
            n.appendLine("      dhcp4: true")
            n.appendLine("      dhcp6: true")
            n.appendLine("      optional: true")
            n.appendLine("  wifis:")
            n.appendLine("    wlan0:")
            n.appendLine("      dhcp4: true")
            if (cc.isNotBlank()) n.appendLine("      regulatory-domain: \"" + cc + "\"")
            n.appendLine("      access-points:")
            n.appendLine("        \"" + yaml(s.wifiSsid) + "\":")
            if (s.wifiHidden) n.appendLine("          hidden: true")
            if (s.wifiOpen) {
                n.appendLine("          auth:")
                n.appendLine("            key-management: none")
            } else {
                n.appendLine("          password: \"" + yaml(wifiPsk(s.wifiPassword, s.wifiSsid)) + "\"")
            }
            n.appendLine("      optional: true")
            files["network-config"] = n.toString().toByteArray()
        }

        val instance = "rpi-imager-android-" + System.currentTimeMillis()
        files["meta-data"] = ("instance-id: " + instance + "\n").toByteArray()
        var cmd = " ds=nocloud;i=" + instance
        if (cc.isNotBlank()) cmd += " cfg80211.ieee80211_regdom=" + cc
        return CustomizationPayload(files, cmd)
    }

    private fun rpiPreseed(s: CustomizationSettings): CustomizationPayload {
        val t = StringBuilder()
        val user = s.username.trim().ifBlank { "pi" }
        val hash = passwordHash(s.password)
        val keys = keyLines(s)
        val cc = country(s.wifiCountry)

        t.appendLine("config_version = \"1.0\"")
        t.appendLine()
        if (s.hostname.isNotBlank()) {
            t.appendLine("[system]")
            t.appendLine("hostname = " + toml(s.hostname))
            t.appendLine()
        }

        if (s.username.isNotBlank() || hash.isNotBlank()) {
            t.appendLine("[user]")
            t.appendLine("name = " + toml(user))
            if (hash.isNotBlank()) {
                t.appendLine("password = " + toml(hash))
                t.appendLine("password_encrypted = true")
            }
            t.appendLine("groups = [\"sudo\"]")
            if (s.passwordlessSudo) t.appendLine("passwordless_sudo = true")
            t.appendLine()
        }

        if (s.sshEnabled) {
            t.appendLine("[ssh]")
            t.appendLine("enabled = true")
            t.appendLine("password_authentication = " + if (s.sshPasswordAuth) "true" else "false")
            if (keys.isNotEmpty()) {
                t.appendLine("authorized_keys = [")
                keys.forEach { t.appendLine("  " + toml(it) + ",") }
                t.appendLine("]")
            }
            t.appendLine()
        }

        if (s.wifiEnabled && s.wifiSsid.isNotBlank()) {
            t.appendLine("[wlan]")
            t.appendLine("ssid = " + toml(s.wifiSsid))
            if (!s.wifiOpen && s.wifiPassword.isNotBlank()) {
                t.appendLine("password = " + toml(wifiPsk(s.wifiPassword, s.wifiSsid)))
                t.appendLine("password_encrypted = true")
            }
            t.appendLine("hidden = " + if (s.wifiHidden) "true" else "false")
            if (cc.isNotBlank()) t.appendLine("country = " + toml(cc))
            t.appendLine()
        }

        if (s.timezone.isNotBlank() || s.keyboard.isNotBlank()) {
            t.appendLine("[locale]")
            if (s.keyboard.isNotBlank()) t.appendLine("keymap = " + toml(s.keyboard))
            if (s.timezone.isNotBlank()) t.appendLine("timezone = " + toml(s.timezone))
            t.appendLine()
        }

        if (s.piConnectEnabled) {
            t.appendLine("[connect]")
            t.appendLine("enabled = true")
            if (s.piConnectToken.isNotBlank()) {
                t.appendLine("mode = \"token\"")
                t.appendLine("token = " + toml(s.piConnectToken.trim()))
            } else {
                t.appendLine("mode = \"device-identity\"")
            }
            t.appendLine()
        }

        if (s.enableI2C || s.enableSPI || s.enable1Wire || s.enableUsbGadget || s.serialMode != "Disabled") {
            t.appendLine("[interfaces]")
            if (s.enableI2C) t.appendLine("i2c = true")
            if (s.enableSPI) t.appendLine("spi = true")
            if (s.enable1Wire) t.appendLine("onewire = true")
            if (s.enableUsbGadget) t.appendLine("usb_gadget = true")
            when (s.serialMode) {
                "Default" -> t.appendLine("serial = \"default\"")
                "Console" -> t.appendLine("serial = \"console\"")
                "Hardware" -> t.appendLine("serial = \"hardware\"")
                "Console & Hardware" -> t.appendLine("serial = \"console_hardware\"")
            }
        }

        var cmd = ""
        if (cc.isNotBlank()) cmd = " cfg80211.ieee80211_regdom=" + cc
        return CustomizationPayload(mapOf("rpi-preseed.toml" to t.toString().trimEnd().plus("\n").toByteArray()), cmd)
    }
}
