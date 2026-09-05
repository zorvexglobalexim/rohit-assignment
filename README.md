# Video Processor

An on-device Android app that scans a portrait video, detects faces, and (in
progress) groups appearances by person to build a shareable collage. Kotlin +
Jetpack Compose, minSdk 26, no backend - everything runs on-device.

## Face embedding model

### Model name

**MobileFaceNet** (TFLite), bundled at `app/src/main/assets/mobilefacenet.tflite`.

### Source

Redistributed via [`MCarlomagno/FaceRecognitionAuth`](https://github.com/MCarlomagno/FaceRecognitionAuth/blob/master/assets/mobilefacenet.tflite),
whose repository license (BSD-3-Clause) covers this bundled asset. The
underlying architecture/training lineage traces to the [MobileFaceNets paper](https://arxiv.org/abs/1804.07573)
and the [`sirius-ai/MobileFaceNet_TF`](https://github.com/sirius-ai/MobileFaceNet_TF)
(Apache-2.0) training implementation, which is the most common lineage for
this exact 112×112→192-d `.tflite` file circulating in Android face-recognition
sample projects. SHA-256 of the bundled file, for provenance:
`be4bc7cfc53f7bc336d0f28b1ab92535f618c913a422b683210750f6b5354854`.

### License

BSD-3-Clause (per the redistributing repository above). Compatible with
bundling in this project with attribution, as done here.

### Why this model was selected

Compared against the FaceNet family (Inception-ResNet-v1, 128-d/512-d,
~45-95 MB even quantized) during the Phase 4 research pass:

- **Size**: ~5 MB vs. an order of magnitude larger for FaceNet - matters a lot
  for "lightweight enough for a phone" plus this being a demo APK.
- **Speed**: designed explicitly for real-time mobile inference (<1M
  parameters); this pipeline calls the embedder once per detected face per
  sampled frame, so per-inference cost compounds quickly.
- **Accuracy is not the bottleneck for this task**: the app clusters a
  handful of unique people within one short video, not 1:N verification
  against a large gallery. MobileFaceNet's ~99.5%-LFW-class accuracy has
  ample headroom; the pipeline's real accuracy ceiling is crop/alignment
  quality and clustering-threshold tuning, not the embedding model's last
  percentage point of benchmark accuracy.
- **Integration precedent**: the most commonly bundled model in Android
  on-device face-recognition sample apps, meaning fewer novel integration
  problems for a time-boxed build.

### Input size

`112 × 112` RGB, float32. Verified directly from the bundled file's tensor
metadata (not assumed):

```
input  tensor: [1, 112, 112, 3]  FLOAT32
output tensor: [1, 192]          FLOAT32
```

### Output dimension

**192-d** float embedding vector (verified from the model file itself, not
hardcoded from documentation - `FaceEmbeddingEngine` reads this dynamically
from the loaded model's output tensor shape rather than assuming 192, so a
different embedding dimension in a future swapped-in model is picked up
automatically).

### Preprocessing

1. **Eye-based rotation alignment**: when ML Kit successfully localizes both
   eye landmarks (`FaceDetectorOptions.LANDMARK_MODE_ALL`), the source image
   is rotated around the face box's center so the eyes are level *before*
   cropping - see `EyeAlignment.rotationDegrees` and
   `FaceEmbeddingEngine.cropFace`. Standard face-recognition preprocessing:
   the same person photographed at different head tilts should still produce
   consistent embeddings, and un-aligned crops are a real source of drift.
   Skipped safely (falls back to the plain unaligned crop below) when a
   landmark is missing or the implied tilt exceeds ~45° - more likely a
   mis-detection than genuine pose, so the crop is left alone rather than
   risking a wrong rotation.
2. **Generous crop, not tight-to-bbox**: `DetectedFace.boundingBox` (from ML
   Kit) is expanded by 40% of its larger side on every edge, then forced
   square (centered on the original box), before anything else - see
   `FaceEmbeddingEngine.cropFace`. This keeps forehead/chin/ears/background
   context and tolerates imprecise detector boxes.
3. **Padding**: if the expanded square extends outside the source frame
   (face near an edge), the out-of-bounds area is filled with neutral gray
   (`RGB 128,128,128`) rather than shrinking or off-centering the crop. Gray
   was chosen deliberately: it normalizes to ~0 (see below), the lowest-signal
   value the network can receive, minimizing any bias the padding region
   introduces.
4. **Resize**: the padded square crop is resized to the model's input size
   (112×112, read from the model rather than hardcoded) via
   `Bitmap.createScaledBitmap` with bilinear filtering.
5. **Pixel normalization**: `(pixelChannel - 128) / 128` per R/G/B channel,
   mapping `[0, 255]` to approximately `[-1, 1]`. Confirmed against a
   reference implementation using this exact model file (not guessed).

### Normalization (embedding vector)

The raw 192-d model output is **not** internally L2-normalized by this
graph (confirmed against a reference implementation using the same file) -
`FaceEmbeddingEngine` explicitly L2-normalizes every embedding
(`v / ||v||₂`) before returning it, so it's safe regardless of what any
future swapped-in model does internally. Verified at runtime by an
instrumented test asserting `||vector|| ≈ 1.0`.

### Similarity metric

**Cosine similarity**, computed as a plain dot product of two L2-normalized
vectors (since `‖a‖ = ‖b‖ = 1`, cosine similarity reduces to `a·b`). Cheap,
symmetric, and standard for this class of embedding. (Comparison/clustering
logic itself is a later phase - this phase only guarantees the vectors are
normalized and ready for it.)

### Expected performance / reliability

Paper-reported: 99.55% LFW accuracy, 92.59% TAR@FAR1e-6 on MegaFace. Real
on-device verification for this project (Pixel-class emulator, x86_64,
API 36): model loads and runs via TensorFlow Lite's XNNPACK CPU delegate
(230/231 graph nodes delegated), confirmed by an instrumented test that
embeds synthetic crops and checks output shape + unit-norm, including a
face box touching the image edge (exercises the padding path) - see
`app/src/androidTest/.../FaceEmbeddingEngineInstrumentedTest.kt`.

### Known limitations

- Eye-based rotation alignment (see Preprocessing above) only corrects
  in-plane tilt (roll) - it does not correct yaw/pitch (a face turned away
  from the camera), which MobileFaceNet still has to tolerate via the
  generous crop alone. A full 3D pose normalization is out of scope.
- Model provenance is redistribution-based (a widely-used file whose exact
  original conversion author isn't independently cryptographically
  verifiable beyond the licensed repository it was pulled from); documented
  transparently above rather than overstated.
- `org.tensorflow:tensorflow-lite` is pinned at `2.16.1`, the last release
  before the artifact was relocated to `com.google.ai.edge.litert` - that
  newer package pulls in unrelated dynamic model-download ("AiPack")
  permissions/services this fully-offline app has no use for. The Java API
  (`org.tensorflow.lite.Interpreter`) is identical either way.
