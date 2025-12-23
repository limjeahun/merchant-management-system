# PaddleOCR ONNX 모델 디렉토리

이 디렉토리에 PaddleOCR ONNX 모델 파일을 저장합니다.

## 현재 모델 파일

| 파일명 | 용도 | 설명 |
|--------|------|------|
| `ch_PP-OCRv4_det_infer.onnx` | 텍스트 영역 검출 | Detection 모델 |
| `korean_PP-OCRv3_rec_infer.onnx` | 문자 인식 | **한국어** Recognition 모델 |
| `korean_dict.txt` | 문자 사전 | 한국어 문자 매핑 |

## 모델 소스

- Detection: https://huggingface.co/onnx-community/PaddleOCR
- Korean Recognition: https://github.com/PaddlePaddle/PaddleOCR/blob/main/doc/doc_en/models_list_en.md

## 참고

- 해당 경로로 다운로드를 못 할 수도 있습니다.
- 모델 파일은 Git에 포함되지 않습니다 (.gitignore에 *.onnx 추가됨)
- 첫 실행 전 반드시 모델을 다운로드해야 합니다
