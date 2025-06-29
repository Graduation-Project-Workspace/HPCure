package com.example.domain.model

class CancerVolume (
    var volume : Int,
    var sequence: MRISequence,
    var affinityMatrix: Array<Array<FloatArray>>,
)