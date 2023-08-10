package com.example.myapplication


enum class InferenceState {
    Initial,
    InProgress,
    Completed
}

data class ImageItem(
    val name: String,
    val filePath: String,
    val orientation: Int,
    var qualityScore: Float? = null,
    var inferenceState: InferenceState = InferenceState.Initial
)