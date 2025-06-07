package com.example.demoapp.Core
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.demoapp.Core.Interfaces.IFuzzySystem
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import com.example.demoapp.Core.SeedPredictor
import android.content.Context
import com.example.demoapp.Core.Interfaces.ISeedPrecitor

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(private val fuzzySystem: IFuzzySystem) {

    private val fuzzySystem: ParallelFuzzySystem = ParallelFuzzySystem();
    private lateinit var seedPredictor: ISeedPrecitor
    private lateinit var context: Context
     constructor(context: Context , seedPredictor: ISeedPrecitor) {
         this.context = context
         this.seedPredictor = seedPredictor;
         this.context = context
    }
    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
        val roiList = mriSeq.images.map { bitmap ->
            ROI(
                xMin = 0,
                xMax = bitmap.height - 1,
                yMin = 0,
                yMax = bitmap.width - 1,
            )
        }
        return fuzzySystem.estimateVolume(mriSeq, roiList, seedPoints, alphaCutValue)
    }
}