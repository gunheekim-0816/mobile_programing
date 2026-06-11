# GitHub Issues 자동 생성 스크립트
# 실행 방법: PowerShell에서 .\create_issues.ps1 실행

if ($env:GITHUB_TOKEN) {
    $TOKEN_PLAIN = $env:GITHUB_TOKEN
} else {
    $TOKEN = Read-Host "GitHub Personal Access Token을 입력하세요" -AsSecureString
    $TOKEN_PLAIN = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($TOKEN)
    )
}

$OWNER = "gunheekim-0816"
$REPO  = "mobile_programing"
$BASE  = "https://api.github.com/repos/$OWNER/$REPO/issues"
$HEADERS = @{
    Authorization = "token $TOKEN_PLAIN"
    Accept        = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}

$issues = @(
    @{
        title  = "fix: 에뮬레이터에서 위치가 미국으로 표시되는 문제"
        body   = @"
## 문제 설명
Android 에뮬레이터에서 Location을 서울(37.5665, 126.9780)로 설정해도, ``fusedLocationClient.lastLocation``이 이전에 캐시된 미국 기본 좌표를 반환하여 카카오 API가 주변 음식점을 찾지 못하는 문제 발생.

## 재현 단계
1. 에뮬레이터에서 Extended Controls → Location → 서울 좌표 설정
2. 앱에서 '현재 위치 조회' 버튼 클릭
3. 위도/경도가 미국 좌표(37.xx, -122.xx)로 표시됨
4. 카카오 API가 미국 기준으로 검색하여 결과 없음

## 원인
``lastLocation``은 기기가 마지막으로 기록한 위치를 캐시로 반환함. 에뮬레이터 초기 상태에서는 미국 마운틴뷰(Google 본사 근처)가 기본 캐시로 설정되어 있음.

## 해결 방법
``lastLocation`` 대신 ``requestFreshLocation()``을 직접 호출하여 항상 새로운 위치 정보를 요청하도록 ``fetchLocation()`` 수정.

```kotlin
// 변경 전: lastLocation 캐시 사용
fusedLocationClient.lastLocation.addOnSuccessListener { ... }

// 변경 후: 항상 새 위치 요청
requestFreshLocation()
```

## 상태
Fixes: 커밋 236e18f 에서 수정 완료
"@
        labels = @("bug", "location")
    },
    @{
        title  = "fix: Gemini API 모델 요청량 초과로 gemini-2.5-flash로 변경"
        body   = @"
## 문제 설명
초기 구현에서 ``gemini-1.5-flash`` (또는 ``gemini-3.5-flash``) 모델을 사용했으나, 무료 티어 요청량(RPM) 한도 초과로 ``429 Too Many Requests`` 오류가 빈번하게 발생.

## 재현 단계
1. 추천 요청을 연속으로 여러 번 실행
2. ``429 Resource has been exhausted`` 오류 발생
3. AI 추천 결과를 받지 못하고 오류 다이얼로그 표시

## 원인
Gemini API 무료 티어의 분당 요청 수(RPM) 제한 초과.

## 해결 방법
``ApiService.kt``에서 모델을 ``gemini-2.5-flash``로 변경하여 더 안정적인 무료 할당량 범위 내에서 동작하도록 수정.

```kotlin
// 변경 전
@POST("v1/models/gemini-1.5-flash:generateContent")

// 변경 후
@POST("v1/models/gemini-2.5-flash:generateContent")
```

## 상태
해결 완료 (ApiService.kt 반영됨)
"@
        labels = @("bug", "api")
    },
    @{
        title  = "fix: Gemini가 카카오 목록에 없는 식당을 추천하는 환각(Hallucination) 문제"
        body   = @"
## 문제 설명
Gemini API가 카카오 Local API로 받아온 음식점 목록 외의 존재하지 않거나 다른 지역의 식당을 추천하는 환각(Hallucination) 현상 발생. 추천 결과가 실제 주변 음식점과 매칭되지 않아 앱이 빈 결과를 보여주는 문제.

## 재현 단계
1. 카카오 API로 주변 음식점 목록 수신
2. Gemini에게 추천 요청
3. Gemini가 목록에 없는 "강남구 유명 맛집" 등 전혀 다른 식당을 반환
4. 매칭 실패 → 추천 결과 0개 표시

## 원인
- LLM의 고유한 환각 특성
- 프롬프트에 제약 조건이 불명확하게 기술됨

## 해결 방법
1. 프롬프트에 강력한 제약 조건 명시:
   - "반드시 위의 목록에 있는 음식점 중에서만 추천해야 해"
   - "목록에 없는 식당은 절대 추천하면 안 돼"
2. 매칭 실패 시 폴백(Fallback) 로직 추가: Kakao 상위 3개 음식점으로 자동 대체

```kotlin
// 폴백 로직
if (finalRecommendations.isEmpty()) {
    for (i in 0 until minOf(3, uniqueDocs.size)) {
        // Kakao 결과 상위 3개로 대체 추천
    }
}
```

## 상태
해결 완료 (MainActivity.kt 반영됨)
"@
        labels = @("bug", "ai", "gemini")
    },
    @{
        title  = "fix: 카카오 API 응답에서 중복 음식점이 표시되는 문제"
        body   = @"
## 문제 설명
카카오 Local API 응답에서 같은 음식점이 다른 ID 또는 약간 다른 이름으로 중복 반환되어, Gemini 프롬프트 및 추천 결과 목록에 동일한 식당이 여러 번 표시되는 문제.

## 재현 단계
1. 프랜차이즈가 많은 지역에서 음식점 검색
2. "맥도날드 강남점", "맥도날드강남" 등 동일 식당이 중복 표시
3. Gemini 프롬프트 내 목록이 불필요하게 길어짐

## 원인
카카오 API가 동일 업체를 다른 branch ID로 반환하는 경우 존재.

## 해결 방법
``HashSet``을 사용하여 ``place_name``(정규화)과 ``id`` 기준으로 중복 제거:

```kotlin
val seenNames = HashSet<String>()
val seenIds = HashSet<String>()
for (doc in withinRadiusDocs) {
    val normalizedName = doc.place_name.trim()
    if (!seenNames.contains(normalizedName) && !seenIds.contains(doc.id)) {
        seenNames.add(normalizedName)
        seenIds.add(doc.id)
        uniqueDocs.add(doc)
    }
}
```

## 상태
해결 완료 (MainActivity.kt 반영됨)
"@
        labels = @("bug", "kakao-api")
    },
    @{
        title  = "question: API 키 보안 — SharedPreferences 평문 저장 방식 검토 필요"
        body   = @"
## 현재 방식
카카오 API 키와 Gemini API 키를 ``SharedPreferences``에 평문(암호화 없이) 저장하고 있음.

```kotlin
sharedPreferences.edit()
    .putString(KEY_KAKAO_API, kakaoKey)
    .putString(KEY_GEMINI_API, geminiKey)
    .apply()
```

## 문제점
- ``SharedPreferences``는 기기 루팅 시 외부에서 파일 접근 가능
- 앱 내부 저장소(/data/data/패키지명/shared_prefs/)에 XML 형태로 키가 노출될 수 있음
- 실제 배포 앱에서는 보안 취약점이 될 수 있음

## 개선 방향 (검토 중)
- Android Jetpack의 ``EncryptedSharedPreferences`` 사용
- 또는 서버사이드 프록시를 통해 API 키를 클라이언트에 노출하지 않는 방식
- BuildConfig에 키를 넣는 방식도 있으나 디컴파일 시 노출 위험 존재

## 상태
현재 학습/개발용으로 유지 중. 실제 배포 전 개선 필요.
"@
        labels = @("question", "security")
    }
)

Write-Host "`n총 $($issues.Count)개 이슈를 생성합니다...`n" -ForegroundColor Cyan

foreach ($issue in $issues) {
    $body = @{
        title  = $issue.title
        body   = $issue.body
        labels = $issue.labels
    } | ConvertTo-Json -Depth 5

    try {
        $response = Invoke-RestMethod -Uri $BASE -Method Post -Headers $HEADERS -Body $body -ContentType "application/json; charset=utf-8"
        Write-Host "✅ 이슈 생성됨: #$($response.number) — $($response.title)" -ForegroundColor Green
        Write-Host "   URL: $($response.html_url)`n"
    } catch {
        Write-Host "❌ 실패: $($issue.title)" -ForegroundColor Red
        Write-Host "   오류: $_`n"
    }
}

Write-Host "완료!" -ForegroundColor Cyan
