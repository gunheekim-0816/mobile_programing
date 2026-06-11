# AGENT.md — 먹킷리스트 (Eating Recommendation App)

> **작성자**: 김건희 / 20211903  
> **과목**: 모바일프로그래밍  
> **버전**: eating_v4 (Bottom Sheet & Kakao Map 웹뷰 개편 버전)  
> **최종 수정**: 2026-06-11

---

## 프로젝트 개요

현재 위치 기반으로 주변 음식점을 검색하고, 사용자의 **기분 · 예산 · 인원 · 상황**을 입력받아 **Gemini AI**가 최적의 식당을 추천해주는 Android 앱입니다.

이번 v4 업데이트에서는 기존 단일 폼 기반 레이아웃을 **네이버 지도 스타일의 풀스크린 지도 및 Persistent Bottom Sheet 기반 단계별 마법사 UX**로 대폭 개편하여 모바일 최적화 및 시각적 직관성을 극대화했습니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 | Kotlin |
| 최소 SDK | API 26 (Android 8.0) |
| 레이아웃 | XML (CoordinatorLayout, NestedScrollView) + View Binding |
| UI 컴포넌트 | BottomSheetBehavior (Material Design 3 스타일) |
| 지도/맵 시각화 | WebView + Kakao Maps JavaScript API |
| 위치 | FusedLocationProviderClient (Google Play Services) |
| 음식점 검색 | 카카오 Local API (category search) |
| AI 추천 | Google Gemini 2.5 Flash API |
| 네트워크 | Retrofit2 + OkHttp3 + Gson |
| 비동기 | Kotlin Coroutines (lifecycleScope) |

---

## 프로젝트 구조

```
Mprogram/
├── app/src/main/java/com/example/my_project/
│   ├── MainActivity.kt          # 메인 화면 — BottomSheet 상태 전이 제어, 주소/맵 연동, 추천 실행
│   ├── data/
│   │   └── DataClasses.kt       # 카카오 API / Gemini API / UI 데이터 클래스
│   ├── network/
│   │   └── ApiService.kt        # Retrofit 인터페이스 및 RetrofitClient 싱글턴
│   └── ui/
│       └── RestaurantAdapter.kt # RecyclerView 어댑터 (추천 결과 목록)
├── app/src/main/res/
│   ├── drawable/
│   │   └── bg_bottom_sheet.xml  # [NEW] 하단 슬라이드 바 배경 드로어블 (24dp 상단 라운딩)
│   ├── layout/
│   │   ├── activity_main.xml    # 메인 레이아웃 (풀스크린 웹뷰 + 플로팅 주소창 + persistent bottom sheet)
│   │   ├── item_restaurant.xml  # 추천 결과 카드 아이템
│   │   └── dialog_settings.xml  # API 키 설정 다이얼로그 (REST Key, JS Key, Gemini Key 세 개 입력)
│   └── values/
│       ├── colors.xml
│       ├── strings.xml
│       └── themes.xml
├── AGENT.md                     # 이 파일 (최신화 완료)
├── README.md                    # 사용자 리드미 (최신화 완료)
├── create_issues.ps1            # [NEW] GitHub Issues 자동 등록 스크립트
└── features/                    # 기능별 상세 문서
    ├── FR-01_location.md
    ├── FR-02_restaurant_search.md
    ├── FR-03_condition_select.md
    ├── FR-04_detail_view.md
    └── FR-05_loading_error.md
```

---

## 실행 방법

### 사전 준비

1. **카카오 API 키 발급**
   - [Kakao Developers](https://developers.kakao.com) → 애플리케이션 생성
   - **REST API 키** 복사 (주소 변환 및 주변 맛집 검색용)
   - **JavaScript 키** 복사 (웹뷰 지도 렌더링용)
   - **Web 플랫폼 설정**: 플랫폼 설정에서 사이트 도메인에 `http://localhost` 및 `https://localhost`를 등록해야 웹뷰에서 지도가 작동합니다.

2. **Google Gemini API 키 발급**
   - [Google AI Studio](https://aistudio.google.com) → Get API Key

### 앱 실행 및 사용 순서

1. Android Studio에서 프로젝트 열기 (`Mprogram/` 폴더)
2. 에뮬레이터 또는 실기기 연결 및 실행
3. **에뮬레이터 사용 시**: 에뮬레이터 우측 `⋮` → Location → 위도 `37.5665` / 경도 `126.9780` 설정 → Set Location 실행
4. 앱 실행 후 우상단 ⚙️ 버튼 클릭 → **카카오 REST API 키**, **카카오 JavaScript 키**, **Gemini API 키** 총 3가지를 각각 올바르게 기입한 후 저장
5. 위치 권한 허용 완료 시 상단 바에 현재 한글 상세 주소가 자동 표시되며, 카카오 지도가 내 위치를 중심으로 자동 렌더링됩니다.
6. 하단의 `오늘의 AI 맛집 추천 받기 🚀` 플로팅 버튼을 터치하여 Bottom Sheet 마법사 진입
7. 인원 → 예산 → 기분 → 상황 조건을 순차적으로 선택(또는 ✏️ 직접 입력)
8. 최종 요약 화면 확인 후 `🚀 AI 맛집 추천 시작하기` 버튼 클릭
9. 추천 완료 시 바가 자동으로 접히며 하단에 맛집 3선 카드 목록이 노출되고 상단 영역의 지도에 핀 마커들이 표시됩니다.

---

## 주요 설계 결정 (v4 기준)

| 설계 결정 사항 | 도입 목적 및 기술적 배경 |
|----------------|--------------------------|
| **WebView 기반 카카오 맵 JS API 도입** | 네이티브 Google Maps SDK 사용 시 필수적인 신용카드 빌링 프로필 등록 허들을 우회하고 완전히 무료로 연동하기 위해 도입. |
| **Cleartext Traffic 허용** | Android 9+ 보안 정책(기본 HTTP 차단)에 따라 차단되는 카카오 지도의 HTTP 이미지 타일 리소스(`daumcdn.net`) 수신을 위해 `usesCleartextTraffic="true"` 적용. |
| **소프트웨어 렌더링 모드 적용** | 일부 에뮬레이터 환경에서 그래픽 가속 에러로 인해 웹뷰 화면이 하얗게만 노출되는 빈 화면 버그를 잡기 위해 `LAYER_TYPE_SOFTWARE` 지정. |
| **JS relayout 지연 호출 (300ms)** | 웹뷰 컨테이너의 레이아웃 가로/세로 크기 측정 지연 문제를 해결하여 정확한 맵 영역 갱신 및 리센터링 보장. |
| **BottomSheetBehavior 상태 연동** | 마법사 선택 중 어중간하게 닫히는 현상을 막기 위해 `peekHeight = 0` 및 `STATE_EXPANDED`로 강제하며, 결과 표시 시에는 `peekHeight = 240dp` 및 `STATE_COLLAPSED`를 주어 맵 마커와 리스트를 동시에 노출시킴. |
| **Android-JS 인터랙션 브릿지** | 지도 위 카카오맵 마커를 클릭하면 안드로이드 네이티브 식당 상세 다이얼로그(`showRestaurantDetailDialog`)가 뜨도록 JavascriptInterface 설계. |

---

## 알려진 제한사항

- 영업시간, 메뉴판, 리뷰, 예약 및 결제 기능은 제외 (요구사항 명세서 준수)
- 네트워크 연결이 활성화된 환경에서만 작동
- 카카오 Local API 반경 2km 이내 검색 결과(최대 15개) 기준으로 AI 추천 수행
