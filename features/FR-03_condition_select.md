# FR-03: 사용자 조건 선택

> **상태**: ✅ 구현 완료  
> **구현 파일**: `MainActivity.kt`, `res/layout/activity_main.xml`

---

## 기능 설명

사용자는 AI 추천을 받기 전에 **인원 수, 예산, 기분/음식 종류, 식사 상황**을 ChipGroup UI로 선택할 수 있습니다.

---

## 수용 조건 및 구현 현황

| 조건 | 상태 | 구현 위치 |
|------|------|-----------|
| [1] 인원·예산·기분·상황 조건을 선택할 수 있다 | ✅ 완료 | 4개 ChipGroup (groupPeople, groupBudget, groupMood, groupSituation) |
| [2] 필수 항목 미선택 시 추천 버튼 누르면 안내 메시지 표시 | ✅ 완료 | `validateAndRecommend()` → 미선택 필드 목록 포함 경고 다이얼로그 |

---

## 구현 세부 사항

### 조건 선택 UI

**ChipGroup 단일 선택 (SingleSelection)**

| 그룹 | 제공 옵션 예시 |
|------|--------------|
| 인원 수 (groupPeople) | 혼자, 2명, 3~4명, 5명 이상, ✏️ 직접 입력 |
| 예산 (groupBudget) | 1만원 이하, 1~2만원, 2만원 이상, ✏️ 직접 입력 |
| 기분/종류 (groupMood) | 든든한, 가벼운, 매운 음식, 새로운 음식, ✏️ 직접 입력 |
| 식사 상황 (groupSituation) | 혼밥, 친구와 식사, 미팅 후, 여행 중 식사, ✏️ 직접 입력 |

### 직접 입력 Chip

각 그룹에 `✏️ 직접 입력` Chip이 있어, 클릭 시 `AlertDialog + EditText` 다이얼로그가 열립니다.
- 입력값이 있으면 Chip 텍스트가 입력값으로 교체되고 해당 Chip이 선택 상태로 전환
- 빈 입력이면 선택 해제 및 텍스트 복원
- 이전 입력값은 `chip.tag`에 저장되어 재편집 시 미리 채워짐

### 유효성 검사

```kotlin
// validateAndRecommend() 내부
if (peopleChipId == View.NO_ID || budgetChipId == View.NO_ID ||
    moodChipId == View.NO_ID || situationChipId == View.NO_ID) {
    // 미선택 항목 목록을 포함한 경고 다이얼로그 표시
}
```

---

## 관련 코드

- `MainActivity.kt` L99–L183: `setupCustomInputChips()`, `showCustomInputDialog()`
- `MainActivity.kt` L369–L388: `validateAndRecommend()` 내 조건 검증 로직
