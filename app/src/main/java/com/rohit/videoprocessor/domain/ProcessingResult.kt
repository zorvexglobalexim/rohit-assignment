package com.rohit.videoprocessor.domain

import android.graphics.Bitmap
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.CollageResult
import com.rohit.videoprocessor.domain.model.DebugReport
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.IdentityClusteringResult
import com.rohit.videoprocessor.domain.model.RepresentativeFrame
import com.rohit.videoprocessor.domain.model.VideoMetadata

/**
 * Phase 7 (final) result: frame extraction + face detection + face
 * embeddings + temporal appearance segmentation + identity clustering +
 * representative frame selection + collage generation. [collage] is null
 * only when no people were found in the video at all - otherwise it always
 * has exactly one tile per [identityClustering] identity.
 */
data class ProcessingResult(
    val frameCount: Int,
    val metadata: VideoMetadata,
    val totalDetections: Int,
    val framesWithFaces: Int,
    val maxFacesInSingleFrame: Int,
    val embeddings: List<FaceEmbedding>,
    val embeddingErrors: Int,
    val appearanceSegments: List<AppearanceSegment>,
    val identityClustering: IdentityClusteringResult,
    val representativeFrames: List<RepresentativeFrame>,
    val collage: CollageResult?,
    val debugReport: DebugReport,
    /**
     * The same generously-cropped, already-decoded per-person images used to build [collage]
     * (keyed by [com.rohit.videoprocessor.domain.model.PersonIdentity.id]) - retained here
     * (rather than discarded once the collage is drawn) so the optional Person Detail screen
     * can show a person's photo without re-decoding/re-cropping anything. Empty when [collage]
     * is null (no people found).
     */
    val personImages: Map<Int, Bitmap>,
)
