package dev.mietze.piimager.system

import dev.mietze.piimager.data.StorageTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootStorage {

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", "id -u")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor() == 0 && out.lines().any { it.trim() == "0" }
        }.getOrDefault(false)
    }

    suspend fun scanTargets(): List<StorageTarget> = withContext(Dispatchers.IO) {
        val command = listOf(
            "for b in /sys/class/block/*; do",
            "  [ -e \"\$b/partition\" ] && continue",
            "  n=\${b##*/}",
            "  case \"\$n\" in loop*|ram*|zram*|dm-*) continue ;; esac",
            "  [ -r \"\$b/size\" ] || continue",
            "  rem=\$(cat \"\$b/removable\" 2>/dev/null || echo 0)",
            "  sectors=\$(cat \"\$b/size\" 2>/dev/null || echo 0)",
            "  real=\$(readlink -f \"\$b\" 2>/dev/null)",
            "  usb=0",
            "  echo \"\$real\" | grep -q \"/usb\" && usb=1",
            "  if [ \"\$rem\" = \"1\" ] || [ \"\$usb\" = \"1\" ]; then",
            "    vendor=\$(cat \"\$b/device/vendor\" 2>/dev/null | tr -d '\\r\\n' | tr '|' '/')",
            "    model=\$(cat \"\$b/device/model\" 2>/dev/null | tr -d '\\r\\n' | tr '|' '/')",
            "    echo \"\$n|\$rem|\$usb|\$sectors|\$vendor|\$model\"",
            "  fi",
            "done"
        ).joinToString("\n")

        val p = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val lines = p.inputStream.bufferedReader().readLines()
        p.waitFor()
        if (p.exitValue() != 0) return@withContext emptyList()

        lines.mapNotNull { line ->
            val x = line.split('|')
            if (x.size < 6) return@mapNotNull null
            val sectors = x[3].toLongOrNull() ?: return@mapNotNull null
            if (sectors <= 0L) return@mapNotNull null
            val name = x[0]
            StorageTarget(
                name = name,
                path = "/dev/block/$name",
                model = x[5].trim().ifBlank { "Removable storage" },
                vendor = x[4].trim(),
                sizeBytes = sectors * 512L,
                removable = x[1] == "1",
                usbBacked = x[2] == "1"
            )
        }.sortedBy { it.name }
    }

    fun openRootWriter(targetPath: String): Process {
        require(targetPath.startsWith("/dev/block/")) { "Unsafe block path" }
        val safe = shellQuote(targetPath)
        val command = listOf(
            "for p in $safe*; do",
            "  umount \"\$p\" >/dev/null 2>&1 || true",
            "done",
            "cat > $safe"
        ).joinToString("\n")
        return ProcessBuilder("su", "-c", command).start()
    }

    fun openRootReader(targetPath: String, bytes: Long): Process {
        require(targetPath.startsWith("/dev/block/")) { "Unsafe block path" }
        require(bytes > 0) { "Invalid verify length" }
        val command = "head -c $bytes " + shellQuote(targetPath)
        return ProcessBuilder("su", "-c", command).start()
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", "sync").start()
            p.waitFor()
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
