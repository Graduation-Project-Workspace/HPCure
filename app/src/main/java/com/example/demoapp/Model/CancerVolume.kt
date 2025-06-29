package com.example.demoapp.Model

class CancerVolume (
    var volume : Float,
    var sequence: MRISequence,
    var affinityMatrix: Array<Array<FloatArray>>,
)