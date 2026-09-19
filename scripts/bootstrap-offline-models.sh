#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lib_dir="$repo_root/app/libs"
model_dir="$repo_root/app/src/main/assets/models"
tessdata_dir="$repo_root/app/src/main/assets/tessdata"
mkdir -p "$lib_dir" "$model_dir" "$tessdata_dir"

fetch() {
  local url="$1"
  local output="$2"
  local expected="$3"
  if [[ -f "$output" ]] && echo "$expected  $output" | sha256sum --check --status; then
    return
  fi
  local partial="${output}.partial"
  curl --fail --location --retry 4 --retry-all-errors --output "$partial" "$url"
  echo "$expected  $partial" | sha256sum --check --status
  mv "$partial" "$output"
}

fetch \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar" \
  "$lib_dir/sherpa-onnx-1.13.8.aar" \
  "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
fetch \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx" \
  "$model_dir/tiny-encoder.int8.onnx" \
  "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"
fetch \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-decoder.int8.onnx" \
  "$model_dir/tiny-decoder.int8.onnx" \
  "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"
fetch \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-tokens.txt" \
  "$model_dir/tiny-tokens.txt" \
  "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
fetch \
  "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/ara.traineddata" \
  "$tessdata_dir/ara.traineddata" \
  "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"
fetch \
  "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/87416418657359cb625c412a48b6e1d6d41c29bd/eng.traineddata" \
  "$tessdata_dir/eng.traineddata" \
  "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"

split_model() {
  local source="$1"
  local prefix="${source}.part."
  rm -f "${prefix}"*
  split --bytes=64m --numeric-suffixes=0 --suffix-length=2 "$source" "$prefix"
  rm -f "$source"
}

split_model "$model_dir/tiny-encoder.int8.onnx"
split_model "$model_dir/tiny-decoder.int8.onnx"
