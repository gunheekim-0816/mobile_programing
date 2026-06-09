# FR-02: 주변 음식점 검색

> **상태**: ✅ 구현 완료  
> **구현 파일**: `MainActivity.kt`, `network/ApiService.kt`, `data/DataClasses.kt`

---

## 기능 설명

시스템은 사용자의 현재 위치를 기준으로 **카카오 Local API**를 호출하여 주변 음식점 목록을 가져옵니다.

---

## 수용 조건 및 구현 현황

| 조건 | 상태 | 구현 위치 |
|------|------|-----------|
| [1] 검색 결과가 있으면 음식점 목록을 화면에 표시한다 | ✅ 완료 | `restaurantAdapter.updateData()` → RecyclerView 갱신 |
| [2] 검색 결과가 없으면 안내 메시지를 보여준다 | ✅ 완료 | `showErrorState("주변 음식점을 찾을 수 없습니다...")` |
| [3] API 요청 실패 시 오류 메시지를 제공한다 | ✅ 완료 | `catch (e: Exception)` → `showErrorState(e.localizedMessage)` |

---

## 구현 세부 사항

### API 호출

```
KakaoApiService.getNearbyRestaurants()
  - endpoint: GET https://dapi.kakao.com/v2/local/search/category.json
  - Authorization: KakaoAK {key}
  - 파라미터:
      x (경도), y (위도)
      radius = 2000 (2km)
      category_group_code = "FD6" (음식점)
      sort = "distance" (거리순)
      size = 15 (최대 15개)
```

### 결과 처리 파이프라인

```
Kakao API 응답 (최대 15개)
    └─ 반경 2km 초과 필터링
           └─ 이름/ID 기준 중복 제거 (HashSet 활용)
                  └─ uniqueDocs → Gemini 프롬프트 입력
```

### 데이터 클래스

| 클래스 | 역할 |
|--------|------|
| `KakaoSearchResponse` | API 응답 최상위 래퍼 |
| `KakaoDocument` | 개별 음식점 정보 (id, place_name, category_name, phone, address_name, road_address_name, place_url, distance) |
| `KakaoMeta` | 페이지네이션 메타 정보 |

---

## 관련 코드

- `network/ApiService.kt` L13–L23: `KakaoApiService` 인터페이스
- `data/DataClasses.kt` L6–L27: Kakao 관련 데이터 클래스
- `MainActivity.kt` L398–L437: API 호출 및 필터링/중복 제거 로직
