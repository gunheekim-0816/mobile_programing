# FR-01: 현재 위치 조회

> **상태**: ✅ 구현 완료  
> **구현 파일**: `MainActivity.kt`

---

## 기능 설명

사용자는 현재 위치를 기준으로 주변 음식점을 검색할 수 있습니다.  
시스템은 위치 권한을 확인하고, 권한이 허용되면 사용자의 위치 정보를 가져옵니다.

---

## 수용 조건 및 구현 현황

| 조건 | 상태 | 구현 위치 |
|------|------|-----------|
| [1] 앱 실행 후 위치 권한을 요청한다 | ✅ 완료 | `checkLocationPermissionsAndGet()` → `ActivityCompat.requestPermissions()` |
| [2] 위치 권한이 허용되면 현재 위치 정보를 가져온다 | ✅ 완료 | `fetchLocation()` → `fusedLocationClient.lastLocation` |
| [3] 위치 정보를 가져오지 못하면 안내 메시지를 보여준다 | ✅ 완료 | `onRequestPermissionsResult()` → Toast + `tvLocationStatus` 텍스트 업데이트 |

---

## 구현 세부 사항

### 권한 흐름

```
btnGetLocation 클릭
    └─ checkLocationPermissionsAndGet()
           ├─ 권한 있음 → fetchLocation()
           └─ 권한 없음 → requestPermissions()
                              └─ onRequestPermissionsResult()
                                     ├─ 허용 → fetchLocation()
                                     └─ 거부 → Toast 안내 메시지
```

### 위치 획득 로직

- **1차 시도**: `fusedLocationClient.lastLocation` (캐시된 마지막 위치)
- **2차 시도**: lastLocation이 null이면 `requestFreshLocation()` 호출
  - `LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 1000).setMaxUpdates(1)` 으로 단발성 위치 업데이트 요청

### 한국 좌표 유효성 검사

위도 33.0~38.5, 경도 124.0~132.0 범위 이탈 시 에뮬레이터 설정 가이드 다이얼로그 표시  
(에뮬레이터 환경 대응)

---

## 관련 코드

- `MainActivity.kt` L194–L315
  - `checkLocationPermissionsAndGet()`: 권한 확인 및 요청
  - `fetchLocation()`: lastLocation 조회
  - `requestFreshLocation()`: 실시간 위치 업데이트 요청
  - `updateLocationCoordinates()`: UI 업데이트 + 한국 좌표 검증
