package com.example.domain.model

class CancerVolume (
    var volume : Float,
    var sequence: MRISequence,
    var affinityMatrix: Array<Array<FloatArray>>,
)