package org.baizey.commands.utils

object ModelSelection {
    val supportedModels = listOf("qwen3.5:9b", "qwen3.6:27b", "qwen3.6:35b")

    const val DEFAULT_MODEL = "qwen3.6:27b"
    const val BEST_LARGE = "qwen3.6:35b"
    const val BEST_SMALL = "qwen3.5:9b"

    @Volatile
    private var currentRevision = 0

    @Volatile
    private var selectedModel = DEFAULT_MODEL

    fun current(): String = selectedModel

    fun currentRevision(): Int = currentRevision

    @Synchronized
    fun select(option: String): ModelSelectionResult {
        if (option == selectedModel) return ModelSelectionResult.Unchanged(option)
        if (!supportedModels.contains(option)) return ModelSelectionResult.Unknown(option, supportedModels)
        selectedModel = option
        currentRevision++
        return ModelSelectionResult.Changed(option)
    }
}

sealed interface ModelSelectionResult {
    data class Changed(val modelName: String) : ModelSelectionResult
    data class Unchanged(val modelName: String) : ModelSelectionResult
    data class Unknown(val requested: String, val supportedModels: List<String>) : ModelSelectionResult
}
