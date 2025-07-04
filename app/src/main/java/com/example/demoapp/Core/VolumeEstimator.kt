package com.example.demoapp.Core
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.domain.model.LogRepository
import com.example.domain.interfaces.tumor.IFuzzySystem
import com.example.domain.interfaces.network.INetworkService
import com.example.domain.interfaces.tumor.IRoiPredictor
import com.example.domain.interfaces.tumor.ISeedPredictor
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import com.example.network.coordinator.VolumeEstimateCoordinatorStrategy
import android.graphics.Bitmap

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(
    private val fuzzySystem: IFuzzySystem,
    private val seedPredictor: ISeedPredictor,
    private val roiPredictor: IRoiPredictor,
    private val network: INetworkService
) {
    private val coordinatorStrategy = VolumeEstimateCoordinatorStrategy(network)

    fun estimateVolumeGrpc(mriSeq: MRISequence, alphaCutValue: Float): Pair<CancerVolume, MRISequence> {
        try {
            Log.d("VolumeEstimator", "Starting gRPC volume estimation for ${mriSeq.images.size} slices")
            
            // Step 1: Get available workers
            val availableWorkers = network.getAvailableWorkers()
            if (availableWorkers.isEmpty()) {
                Log.w("VolumeEstimator", "No workers available, falling back to local computation")
                return estimateVolumeLocal(mriSeq, alphaCutValue)
            }

            // Step 2: Convert MRISequence to protobuf request
            val request = coordinatorStrategy.convertMRISequenceToRequest(mriSeq, alphaCutValue)

            // Step 3: Start distributed computation (ROI + Seed prediction)
            val (roiList, seedPoints) = coordinatorStrategy.start(
                request = request,
                availableWorkers = availableWorkers,
                logs = LogRepository.sharedLogs
            )

            // Step 4: Create filtered MRI sequence from gRPC results
            val filteredMriSeq = MRISequence(
                images = mriSeq.images.filterIndexed { index, _ ->
                    roiList.any { it.sliceIndex == index }
                },
                metadata = mriSeq.metadata
            )

            // Step 5: Use fuzzy system to compute final volume with filtered data
            val cancerVolume = fuzzySystem.estimateVolume(
                mriSequence = filteredMriSeq,
                roiList = roiList,
                seedPoints = seedPoints,
                alphaCut = alphaCutValue
            )

            Log.d("VolumeEstimator", "gRPC volume estimation completed successfully")
            return Pair(cancerVolume, filteredMriSeq)

        } catch (e: Exception) {
            Log.e("VolumeEstimator", "Error in gRPC computation, falling back to local computation", e)
            return estimateVolumeLocal(mriSeq, alphaCutValue)
        }
    }

    private fun estimateVolumeLocal(mriSeq: MRISequence, alphaCutValue: Float): Pair<CancerVolume, MRISequence> {
        Log.d("VolumeEstimator", "Using local computation fallback")
        
        // Step 1: ROI prediction (local)
        val roiList = roiPredictor.predictRoi(mriSeq)
        
        // Step 2: Seed prediction (local)
        val seedPoints = seedPredictor.predictSeed(mriSeq, roiList).toList()
        
        // filtered mri sequence
        val filteredMriSeq = MRISequence(
            images = mriSeq.images.filterIndexed { index, _ ->
                roiList.any { it.sliceIndex == index }
            },
            metadata = mriSeq.metadata
        )   

        // Step 3: Volume calculation (local)
        val cancerVolume = fuzzySystem.estimateVolume(
            mriSequence = mriSeq,
            roiList = roiList,
            seedPoints = seedPoints,
            alphaCut = alphaCutValue
        )
        return Pair(cancerVolume, filteredMriSeq)
    }
}