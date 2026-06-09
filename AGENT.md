# AGENT.md — 먹킷리스트 (Eating Recommendation App)

> **작성자**: 김건희 / 20211903  
> **과목**: 모바일프로그래밍  
> **버전**: eating_v3  
> **최종 수정**: 2026-06-09

---

## 프로젝트 개요

현재 위치 기반으로 주변 음식점을 검색하고, 사용자의 **기분 · 예산 · 인원 · 상황**을 입력받아 **Gemini AI**가 최적의 식당을 추천해주는 Android 앱입니다.

여행자, 출장자, 낯선 지역 방문자처럼 어디서 먹을지 고민하는 사용자에게 빠르고 맥락 있는 맛집 추천을 제공합니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 | Kotlin |
| 최소 SDK | API 26 (Android 8.0) |
| 레이아웃 | XML + View Binding |
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
│   ├── MainActivity.kt          # 메인 화면 — 위치 조회, 조건 선택, 추천 실행
│   ├── data/
│   │   └── DataClasses.kt       # 카카오 API / Gemini API / UI 데이터 클래스
│   ├── network/
│   │   └── ApiService.kt        # Retrofit 인터페이스 및 RetrofitClient 싱글턴
│   └── ui/
│       └── RestaurantAdapter.kt # RecyclerView 어댑터 (추천 결과 목록)
├── app/src/main/res/
│   ├── layout/
│   │   ├── activity_main.xml    # 메인 레이아웃
│   │   ├── item_restaurant.xml  # 추천 결과 카드 아이템
│   │   └── dialog_settings.xml  # API 키 설정 다이얼로그
│   └── values/
│       ├── colors.xml
│       ├── strings.xml
│       └── themes.xml
├── AGENT.md                     # 이 파일
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

1. **카카오 Local API 키 발급**
   - [Kakao Developers](https://developers.kakao.com) → 앱 생성 → REST API 키 복사

2. **Google Gemini API 키 발급**
   - [Google AI Studio](https://aistudio.google.com) → Get API Key

### 앱 실행 순서

1. Android Studio에서 프로젝트 열기 (`Mprogram/` 폴더)
2. 에뮬레이터 또는 실기기 연결
3. **에뮬레이터 사용 시**: 에뮬레이터 우측 `⋮` → Location → 위도 `37.5665` / 경도 `126.9780` 설정 → Set Location
4. 앱 실행 후 우상단 ⚙️ 버튼 → API 키 입력 후 저장
5. `현재 위치 조회` 버튼 → 위치 권한 허용
6. 조건(인원/예산/기분/상황) 선택
7. `AI 추천 받기` 버튼 클릭

---

## 주요 설계 결정

| 결정 | 이유 |
|------|------|
| API 키를 앱 내 SharedPreferences에 저장 | 하드코딩 방지, 사용자가 직접 관리 |
| 반경 2km 필터링 + 중복 제거 | 비기능 요구사항 NFR-3.2 [3][4] 준수 |
| Gemini 응답 실패 시 Kakao 상위 3개 폴백 | 앱 비정상 종료 방지 (NFR-3.2 [1]) |
| 위치가 한국 외일 경우 경고 다이얼로그 | 에뮬레이터 환경 사용자 가이드 |
| Gemini 프롬프트에 목록 내 식당만 추천 강제 | 환각(Hallucination) 방지 (NFR-3.2 [4]) |

---

## 알려진 제한사항

- 영업시간, 메뉴판, 리뷰, 배달 기능은 의도적으로 제외 (요구사항 명세 참조)
- 로그인 / 회원가입 / 결제 / 예약 기능 없음
- 네트워크 연결이 필요한 환경에서만 동작
- 카카오 Local API 검색 결과 최대 15개 기준으로 추천
