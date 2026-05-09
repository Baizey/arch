package org.baizey.commands.utils

import org.baizey.runtime.agentic.instance.ProviderType

object ModelSelection {
    const val DEFAULT_MODEL = "qwen3.6:27b"
    const val BEST_SMALL = "qwen3.5:9b"
    private var catalogLoader: () -> List<SupportedModelOption> = { ProviderModelCatalog().load() }
    private var catalog: List<SupportedModelOption> = catalogLoader()

    @Volatile
    private var currentRevision = 0

    @Volatile
    var current: SupportedModelOption = preferredModel()
        private set

    @Volatile
    var supportedModels: List<SupportedModelOption> = catalog
        private set

    fun currentRevision(): Int = currentRevision

    @Synchronized
    fun select(option: String): ModelSelectionResult {
        val model = supportedModels.firstOrNull { it.id == option }
            ?: return ModelSelectionResult.Unknown(option, supportedModels)
        if (option == current.id) return ModelSelectionResult.Unchanged(model)
        current = model
        currentRevision++
        return ModelSelectionResult.Changed(model)
    }

    @Synchronized
    internal fun installCatalogLoaderForTests(loader: () -> List<SupportedModelOption>) {
        catalogLoader = loader
        reloadCatalog()
    }

    @Synchronized
    internal fun resetCatalogLoaderForTests() {
        catalogLoader = { ProviderModelCatalog().load() }
        reloadCatalog()
    }

    @Synchronized
    private fun reloadCatalog() {
        catalog = catalogLoader()
        supportedModels = catalog
        current = preferredModel()
        currentRevision = 0
    }

    private fun preferredModel(): SupportedModelOption {
        return catalog.firstOrNull { it.provider == ProviderType.OLLAMA && it.name == DEFAULT_MODEL }
            ?: catalog.firstOrNull { it.provider == ProviderType.OLLAMA }
            ?: catalog.firstOrNull { it.provider == ProviderType.OPENAI }
            ?: catalog.firstOrNull() ?: throw IllegalStateException("No supported models found")
    }
}

sealed interface ModelSelectionResult {
    data class Changed(val model: SupportedModelOption) : ModelSelectionResult
    data class Unchanged(val model: SupportedModelOption) : ModelSelectionResult
    data class Unknown(val requested: String, val supportedModels: List<SupportedModelOption>) : ModelSelectionResult
}
