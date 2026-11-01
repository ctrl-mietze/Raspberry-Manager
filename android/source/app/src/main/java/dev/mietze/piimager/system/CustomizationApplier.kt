package dev.mietze.piimager.system

import android.util.Base64
import dev.mietze.piimager.data.CustomizationSettings
import dev.mietze.piimager.data.StorageTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CustomizationApplier {

    suspend fun apply(
        target: StorageTarget,
        initFormat: String?,
        settings: CustomizationSettings
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = CustomizationGenerator.generate(initFormat, settings)
        if (payload.files.isEmpty() && payload.cmdlineAppend.isBlank()) {
            return@withContext false
        }

        val script = buildScript(target.path, payload)
        val p = ProcessBuilder("su", "-c", script)
            .redirectErrorStream(true)
            .start()
        val output = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        check(code == 0) {
            "OS customisation failed ($code): " + output.trim().takeLast(1200)
        }
        true
    }

    private fun buildScript(targetPath: String, payload: CustomizationPayload): String {
        require(targetPath.startsWith("/dev/block/"))
        val qTarget = shellQuote(targetPath)
        val b = StringBuilder()

        b.appendLine("set -e")
        b.appendLine("TARGET=" + qTarget)
        b.appendLine("blockdev --rereadpt \"\$TARGET\" >/dev/null 2>&1 || true")
        b.appendLine("sleep 2")
        b.appendLine("case \"\$TARGET\" in")
        b.appendLine("  *[0-9]) PART=\"\$TARGET\"p1 ;;")
        b.appendLine("  *) PART=\"\$TARGET\"1 ;;")
        b.appendLine("esac")
        b.appendLine("[ -b \"\$PART\" ] || { echo \"Boot partition not found: \$PART\"; exit 41; }")
        b.appendLine("MNT=/data/local/tmp/pi-imager-boot-\$\$")
        b.appendLine("mkdir -p \"\$MNT\"")
        b.appendLine("cleanup() { umount \"\$MNT\" >/dev/null 2>&1 || true; rmdir \"\$MNT\" >/dev/null 2>&1 || true; }")
        b.appendLine("trap cleanup EXIT")
        b.appendLine("mount | grep \"\$PART\" | awk '{print $3}' | while read m; do umount \"\$m\" >/dev/null 2>&1 || true; done")
        b.appendLine("mount -t vfat -o rw \"\$PART\" \"\$MNT\" 2>/dev/null || mount -o rw \"\$PART\" \"\$MNT\"")

        payload.files.forEach { (name, bytes) ->
            require(name.matches(Regex("[A-Za-z0-9._-]+")))
            val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            b.appendLine("printf '%s' " + shellQuote(data) + " | base64 -d > \"\$MNT/" + name + "\"")
        }

        if (payload.cmdlineAppend.isNotBlank()) {
            val append = payload.cmdlineAppend.trim()
            b.appendLine("CMD=\"\$MNT/cmdline.txt\"")
            b.appendLine("if [ -f \"\$CMD\" ]; then")
            b.appendLine("  CURRENT=$(tr -d '\\r\\n' < \"\$CMD\")")
            append.split(Regex("\\s+(?=[A-Za-z0-9_.-]+=)"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { token ->
                    val key = token.substringBefore('=')
                    b.appendLine("  echo \" \$CURRENT \" | grep -q " + shellQuote(" " + key + "=") +
                        " || CURRENT=\"\$CURRENT " + shellEscapeDouble(token) + "\"")
                }
            b.appendLine("  printf '%s\\n' \"\$CURRENT\" > \"\$CMD\"")
            b.appendLine("fi")
        }

        b.appendLine("sync")
        b.appendLine("echo CUSTOMISATION_OK")
        return b.toString()
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun shellEscapeDouble(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace(0x60.toChar().toString(), "\\" + 0x60.toChar())
}
