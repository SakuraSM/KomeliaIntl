# HarmonyOS model notices

## Manga Panel and Text Detector (YOLO26-nano)

- Author: Leandro Narosky (`leoxs22`)
- Source: https://huggingface.co/leoxs22/manga-panel-detector-yolo26n
- ONNX export: https://huggingface.co/mednasserallah/manga-panel-detector-yolo26n-onnx
- License: Apache License 2.0
- Training data: Manga109-s

The bundled `manga_panel_detector_fp32_1024.ms` was produced from the upstream
FP32 ONNX export at commit `f6b7f3cb2bada1ec7d8b16501f9c984890811445`.
Three explicit default `MaxPool` attributes (`dilations=[1,1]`) were removed to
support the HarmonyOS MindSpore Lite 2.1 converter. This is an equivalent graph
normalization and does not alter the model weights. The normalized graph was
then converted to MindSpore Lite format with Huawei's `converter_lite` 2.1.0.

- Source ONNX SHA-256: `e66667bc6d5f00013ff27efc15d21e521825369d44dfd5d7f6e43cda2ca512b7`
- Bundled MindSpore model SHA-256: `bd81ca2e7bbb0d0a4fda5746c317335887cf7f1beef98d1c07e923011fe92a01`

The model detects class `0` (panel) and class `1` (text). Komelia uses only the
panel detections. Use of Manga109-s training data is disclosed here in
accordance with its model-use terms.

## MangaJaNai IllustrationJaNai 2x super-resolution

- Author: Snd-R / MangaJaNai contributors
- Source: https://github.com/Snd-R/mangajanai
- Release asset: `MangaJaNaiOnnxModels.zip` (`1.0.0`)
- Source model: `2x_IllustrationJaNai_V1_ESRGAN_120k.onnx`
- License: Creative Commons Attribution-NonCommercial 4.0 International

The bundled `mangajanai_illustration_2x_fp32_256.ms` is a modified build for
HarmonyOS MindSpore Lite. The upstream FP16 ONNX graph was converted to FP32,
fixed to a `1x3x256x256` input, and its dynamic pixel-unshuffle prefix was
replaced by `SpaceToDepth` with an equivalent first-convolution channel
permutation. The converted ONNX output was checked against the upstream model
with deterministic input (`max_abs=0.000398`, `mean_abs=0.0000943`,
`PSNR=78.79 dB`) before conversion with MindSpore Lite 2.9.0. Komelia stitches
inference using overlapped tiles and a center crop.

This model is provided only for non-commercial use under CC BY-NC 4.0. Any
commercial distribution must remove or replace this asset with a model whose
license permits that use.

- Source ONNX SHA-256: `452a71b045c06cd92977b2d714663641d37104b26d566ea9a317b426c983f55c`
- Modified FP32 ONNX SHA-256: `8b7fd70c1887435f5e71141dae330d8d0f8be98f38c1014b135ff1de750aa7d3`
- Bundled MindSpore model SHA-256: `a8d1fa7928a52c9ddb236a32d5cbd545ecd82f4634d8c049250c61cbb7d43283`
