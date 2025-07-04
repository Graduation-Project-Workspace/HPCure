package com.example.domain.model

import java.io.Serializable

data class ROI (
    var xMin: Int,
    var xMax: Int,
    var yMin: Int,
    var yMax: Int,
    var score: Float = 0f,
    var sliceIndex: Int = 0
) : Serializable

