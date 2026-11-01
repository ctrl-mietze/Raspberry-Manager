package dev.mietze.piimager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mietze.piimager.data.CatalogRepository
import dev.mietze.piimager.data.CustomizationSettings
import dev.mietze.piimager.data.FlashProgress
import dev.mietze.piimager.data.FlashStage
import dev.mietze.piimager.data.ImageSource
import dev.mietze.piimager.data.OsCatalog
import dev.mietze.piimager.data.OsItem
import dev.mietze.piimager.data.PiDevice
import dev.mietze.piimager.data.StorageTarget
import dev.mietze.piimager.data.filterOsForDevice
import dev.mietze.piimager.system.ImageWriter
import dev.mietze.piimager.system.RootStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class WizardStep {
    DEVICE, OS, CUSTOMIZE, STORAGE, REVIEW, WRITE
}

data class ImagerUiState(
    val step: WizardStep = WizardStep.DEVICE,
    val loadingCatalog: Boolean = true,
    val catalogError: String? = null,
    val libraryItem: OsItem? = null,
    val catalog: OsCatalog? = null,
    val selectedDevice: PiDevice? = null,
    val selectedSource: ImageSource? = null,
    val customization: CustomizationSettings = CustomizationSettings(),
    val targets: List<StorageTarget> = emptyList(),
    val selectedTarget: StorageTarget? = null,
    val rootAvailable: Boolean? = null,
    val scanningStorage: Boolean = false,
    val verifyAfterWrite: Boolean = true,
    val repoUrl: String = CatalogRepository.DEFAULT_REPO,
    val flashProgress: FlashProgress = FlashProgress(),
    val flashError: String? = null,
    val writing: Boolean = false
)

class ImagerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("imager", 0)
    private val repository = CatalogRepository(app)
    private val imageWriter = ImageWriter(app.contentResolver)
    private val cancelled = AtomicBoolean(false)

    private val _ui = MutableStateFlow(
        ImagerUiState(
            verifyAfterWrite = prefs.getBoolean("verify", true),
            repoUrl = prefs.getString("repo", CatalogRepository.DEFAULT_REPO)
                ?: CatalogRepository.DEFAULT_REPO
        )
    )
    val ui: StateFlow<ImagerUiState> = _ui.asStateFlow()

    init {
        refreshRoot()
        loadCatalog(false)
    }

    fun loadCatalog(force: Boolean) {
        val url = _ui.value.repoUrl.trim().ifBlank { CatalogRepository.DEFAULT_REPO }
        _ui.update { it.copy(loadingCatalog = true, catalogError = null) }
        viewModelScope.launch {
            runCatching { repository.load(url, force) }
                .onSuccess { catalog ->
                    _ui.update { it.copy(catalog = catalog, loadingCatalog = false) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            loadingCatalog = false,
                            catalogError = e.message ?: "Unable to load OS catalogue"
                        )
                    }
                }
        }
    }

    fun setRepoUrl(url: String) {
        val clean = url.trim().ifBlank { CatalogRepository.DEFAULT_REPO }
        prefs.edit().putString("repo", clean).apply()
        _ui.update { it.copy(repoUrl = clean) }
        loadCatalog(true)
    }

    fun setVerify(enabled: Boolean) {
        prefs.edit().putBoolean("verify", enabled).apply()
        _ui.update { it.copy(verifyAfterWrite = enabled) }
    }

    fun refreshRoot() {
        viewModelScope.launch {
            _ui.update { it.copy(rootAvailable = RootStorage.hasRoot()) }
        }
    }

    fun importLibraryItem(item: OsItem) {
        _ui.update { it.copy(libraryItem = item, selectedSource = ImageSource.Remote(item), selectedDevice = null, selectedTarget = null, step = WizardStep.DEVICE) }
    }

    fun clearLibraryItem() {
        _ui.update { it.copy(libraryItem = null, selectedSource = null, selectedDevice = null, step = WizardStep.DEVICE) }
    }

    fun selectDevice(device: PiDevice) {
        val pending = _ui.value.libraryItem
        if (pending != null && pending.devices.isNotEmpty() && device.tags.isNotEmpty() && pending.devices.none(device.tags::contains)) return
        _ui.update {
            it.copy(
                selectedDevice = device,
                selectedSource = pending?.let { image -> ImageSource.Remote(image) },
                selectedTarget = null,
                step = if (pending == null) WizardStep.OS else WizardStep.CUSTOMIZE
            )
        }
    }

    fun filteredOs(): List<OsItem> {
        val state = _ui.value
        val list = state.catalog?.osList.orEmpty()
        return filterOsForDevice(list, state.selectedDevice?.tags.orEmpty())
    }

    fun selectOs(item: OsItem) {
        require(!item.url.isNullOrBlank())
        _ui.update {
            it.copy(
                selectedSource = ImageSource.Remote(item),
                libraryItem = null,
                selectedTarget = null,
                step = WizardStep.CUSTOMIZE
            )
        }
    }

    fun selectLocal(uri: Uri, displayName: String) {
        _ui.update {
            it.copy(
                selectedSource = ImageSource.Local(uri.toString(), displayName),
                libraryItem = null,
                selectedTarget = null,
                step = WizardStep.CUSTOMIZE
            )
        }
    }

    fun updateCustomization(update: (CustomizationSettings) -> CustomizationSettings) {
        _ui.update { it.copy(customization = update(it.customization)) }
    }

    fun continueFromCustomization() {
        _ui.update { it.copy(step = WizardStep.STORAGE) }
        scanStorage()
    }

    fun selectedInitFormat(): String? = when (val source = _ui.value.selectedSource) {
        is ImageSource.Remote -> source.item.initFormat
        else -> null
    }

    fun scanStorage() {
        _ui.update { it.copy(scanningStorage = true) }
        viewModelScope.launch {
            val root = RootStorage.hasRoot()
            val targets = if (root) RootStorage.scanTargets() else emptyList()
            _ui.update {
                it.copy(
                    rootAvailable = root,
                    targets = targets,
                    scanningStorage = false,
                    selectedTarget = it.selectedTarget?.let { selected ->
                        targets.firstOrNull { t -> t.path == selected.path }
                    }
                )
            }
        }
    }

    fun selectTarget(target: StorageTarget) {
        _ui.update { it.copy(selectedTarget = target, step = WizardStep.REVIEW) }
    }

    fun goTo(step: WizardStep) {
        if (_ui.value.writing) return
        _ui.update { it.copy(step = step) }
    }

    fun startWrite() {
        val source = _ui.value.selectedSource ?: return
        val target = _ui.value.selectedTarget ?: return
        if (_ui.value.rootAvailable != true) {
            _ui.update { it.copy(flashError = "Root access is required to write raw removable media.") }
            return
        }

        cancelled.set(false)
        _ui.update {
            it.copy(
                step = WizardStep.WRITE,
                writing = true,
                flashError = null,
                flashProgress = FlashProgress(FlashStage.PREPARING, message = "Starting")
            )
        }

        viewModelScope.launch {
            runCatching {
                imageWriter.write(
                    source = source,
                    target = target,
                    verifyDisk = _ui.value.verifyAfterWrite,
                    customizationSettings = _ui.value.customization,
                    initFormat = selectedInitFormat(),
                    isCancelled = { cancelled.get() },
                    onProgress = { progress ->
                        _ui.update { old -> old.copy(flashProgress = progress) }
                    }
                )
            }.onSuccess {
                _ui.update { state -> state.copy(writing = false) }
            }.onFailure { e ->
                if (e is ImageWriter.CancelledException) {
                    _ui.update {
                        it.copy(
                            writing = false,
                            flashProgress = FlashProgress(
                                FlashStage.CANCELLED,
                                message = "Write cancelled"
                            )
                        )
                    }
                } else {
                    _ui.update {
                        it.copy(
                            writing = false,
                            flashError = e.message ?: e.javaClass.simpleName,
                            flashProgress = FlashProgress(
                                FlashStage.ERROR,
                                message = "Write failed",
                                detail = e.message ?: "Unknown error"
                            )
                        )
                    }
                }
            }
        }
    }

    fun cancelWrite() {
        cancelled.set(true)
    }

    fun resetAfterWrite() {
        _ui.update {
            it.copy(
                step = WizardStep.DEVICE,
                libraryItem = null,
                selectedDevice = null,
                selectedSource = null,
                selectedTarget = null,
                targets = emptyList(),
                flashProgress = FlashProgress(),
                flashError = null
            )
        }
    }
}
