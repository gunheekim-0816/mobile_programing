# FR-05: 로딩 및 오류 처리

> **상태**: ✅ 구현 완료  
> **구현 파일**: `MainActivity.kt`

---

## 기능 설명

시스템은 위치 정보 조회, 음식점 검색, AI 추천 과정에서 사용자가 진행 상황을 알 수 있도록 로딩 표시 및 오류 메시지를 제공합니다.

---

## 수용 조건 및 구현 현황

| 조건 | 상태 | 구현 위치 |
|------|------|-----------|
| [1] AI 추천 중에는 진행 중임을 표시한다 | ✅ 완료 | `cardLoading` + `tvLoadingText` 단계별 메시지 업데이트 |
| [2] API 요청 오류 시 오류 내용을 표시한다 | ✅ 완료 | `showErrorState()` → AlertDialog에 상세 오류 메시지 표시 |
| [3] 네트워크 오류 시 다시 시도하라는 안내를 보여준다 | ✅ 완료 | `catch (e: Exception)` → "네트워크 오류 또는 API 요청 중 문제..." + 재시도 안내 |

---

## 구현 세부 사항

### 로딩 단계별 메시지

추천 프로세스 중 `tvLoadingText`가 단계에 따라 업데이트됩니다:

```
1단계: "주변 음식점을 검색하고 있습니다..."
2단계: "반경 2km 내 N개 음식점 중 AI가 최적 맛집을 선정 중..."
```

- `cardLoading` (CardView): 로딩 중 visible, 완료 후 gone
- `layoutResults` (RecyclerView 컨테이너): 로딩 중 gone, 완료 후 visible

### 오류 처리 구조

```
validateAndRecommend()
├─ API 키 미입력 → Toast + 설정 다이얼로그 자동 오픈
├─ 위치 정보 없음 → showValidationWarning()
├─ 필수 조건 미선택 → showValidationWarning() (누락 항목 목록 포함)
└─ try-catch (네트워크/API 예외)
       └─ showErrorState(e.localizedMessage)
              └─ AlertDialog: 오류 제목 + 상세 메시지 + "확인" 버튼
```

### 세부 오류 메시지 예시

| 상황 | 표시 메시지 |
|------|------------|
| 주변 음식점 없음 | "주변 음식점을 찾을 수 없습니다. 위치가 올바른지 확인하고 다시 시도하세요." |
| 반경 2km 내 결과 없음 | "반경 2km 내 음식점을 찾을 수 없습니다. 위치를 확인하거나 다시 시도해 주세요." |
| Gemini 응답 없음 | "AI 추천 결과를 읽어오지 못했습니다. 다시 시도해 주세요." |
| JSON 파싱 실패 (폴백) | Kakao 상위 3개 음식점으로 자동 대체 추천 |
| 네트워크/API 예외 | "네트워크 오류 또는 API 요청 중 문제가 발생했습니다.\n[상세 내용]\n{e.localizedMessage}\nAPI 키와 네트워크 연결을 확인하고 다시 시도하세요." |

### 앱 비정상 종료 방지

- 모든 네트워크 작업은 `try-catch`로 감싸 앱 강제 종료 방지 (NFR-3.2 [1])
- Gemini 환각(Hallucination)으로 매칭 실패 시 Kakao 상위 3개로 폴백 (앱이 빈 결과를 보여주지 않음)
- 위치 `SecurityException` 개별 처리

---

## 관련 코드

- `MainActivity.kt` L390–L393: 로딩 카드 표시
- `MainActivity.kt` L439: 단계별 로딩 메시지 업데이트
- `MainActivity.kt` L518–L520: 네트워크 예외 catch
- `MainActivity.kt` L581–L598: `showValidationWarning()`, `showErrorState()`
