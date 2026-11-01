package dev.mietze.piimager.data

data class PiDevice(
    val name: String,
    val description: String,
    val icon: String?,
    val tags: List<String>
)

data class OsItem(
    val name: String,
    val description: String,
    val icon: String?,
    val url: String?,
    val extractSize: Long?,
    val extractSha256: String?,
    val downloadSize: Long?,
    val downloadSha256: String?,
    val releaseDate: String?,
    val initFormat: String?,
    val devices: List<String>,
    val subitems: List<OsItem>
) {
    val isCategory: Boolean get() = subitems.isNotEmpty() && url.isNullOrBlank()
}

data class OsCatalog(
    val devices: List<PiDevice>,
    val osList: List<OsItem>
)

data class StorageTarget(
    val name: String,
    val path: String,
    val model: String,
    val vendor: String,
    val sizeBytes: Long,
    val removable: Boolean,
    val usbBacked: Boolean
)

sealed interface ImageSource {
    data class Remote(val item: OsItem) : ImageSource
    data class Local(val uri: String, val displayName: String) : ImageSource
}

enum class FlashStage {
    IDLE,
    PREPARING,
    DOWNLOADING_WRITING,
    VERIFYING,
    CUSTOMIZING,
    DONE,
    ERROR,
    CANCELLED
}

data class FlashProgress(
    val stage: FlashStage = FlashStage.IDLE,
    val fraction: Float = 0f,
    val bytesDone: Long = 0L,
    val bytesTotal: Long? = null,
    val message: String = "",
    val detail: String = ""
)
