# FR-04: 음식점 상세 정보 확인

> **상태**: ✅ 구현 완료  
> **구현 파일**: `MainActivity.kt`, `ui/RestaurantAdapter.kt`, `res/layout/item_restaurant.xml`

---

## 기능 설명

사용자는 추천된 음식점을 선택하여 이름, 주소, 전화번호, AI 추천 이유 등 상세 정보를 확인할 수 있습니다.

---

## 수용 조건 및 구현 현황

| 조건 | 상태 | 구현 위치 |
|------|------|-----------|
| [1] 추천 결과를 누르면 상세 화면 또는 다이얼로그가 열린다 | ✅ 완료 | `RestaurantAdapter.onItemClick` → `showRestaurantDetailDialog()` |
| [2] 상세 정보에 이름, 주소, 전화번호, 거리, URL이 표시된다 | ✅ 완료 | `AlertDialog` 메시지에 업종·거리·주소·전화번호·AI추천이유 + "카카오맵에서 보기" 버튼 |
| [3] 전화번호 정보가 없는 경우 "전화번호 정보 없음"으로 표시한다 | ✅ 완료 | `doc.phone.ifEmpty { "전화번호 정보 없음" }` |

---

## 구현 세부 사항

### 목록 카드 (item_restaurant.xml)

RecyclerView 각 항목에서 바로 확인 가능한 요약 정보:

| 뷰 ID | 표시 내용 |
|-------|----------|
| `tvName` | 음식점 이름 |
| `tvCategory` | 카테고리 (마지막 세분류만 표시, `>` 기준 split) |
| `tvDistance` | 거리 (예: `350m`) |
| `tvAddress` | 도로명 주소 (없으면 지번 주소) |
| `tvReason` | AI 추천 이유 |

### 상세 다이얼로그

항목 클릭 시 `AlertDialog`로 상세 정보 표시:

```
📍 업종: [카테고리 세분류]
📏 거리: [N]m
🏠 주소: [도로명/지번 주소]
📞 전화번호: [번호 or "전화번호 정보 없음"]

💬 AI 추천 이유:
[Gemini가 생성한 추천 이유]
```

- URL이 있는 경우 **"카카오맵에서 보기"** 버튼 추가 → `Intent(ACTION_VIEW, Uri.parse(url))`로 브라우저 열기

### 데이터 클래스

```kotlin
data class RecommendedRestaurant(
    val name: String,
    val category: String,
    val distance: String,
    val address: String,       // 도로명 없으면 지번 폴백
    val phone: String,         // 없으면 "전화번호 정보 없음"
    val reason: String,        // Gemini 추천 이유
    val url: String            // 카카오맵 URL
)
```

---

## 관련 코드

- `MainActivity.kt` L75–L81: RecyclerView + Adapter 초기화
- `MainActivity.kt` L600–L630: `showRestaurantDetailDialog()`
- `ui/RestaurantAdapter.kt` L17–L27: `ViewHolder.bind()`
- `data/DataClasses.kt` L61–L69: `RecommendedRestaurant` 데이터 클래스
