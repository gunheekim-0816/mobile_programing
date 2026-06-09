package com.example.my_project

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.my_project.data.*
import com.example.my_project.databinding.ActivityMainBinding
import com.example.my_project.network.RetrofitClient
import com.example.my_project.ui.RestaurantAdapter
import com.google.android.gms.location.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var restaurantAdapter: RestaurantAdapter

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "EatingPrefs"
        private const val KEY_KAKAO_API = "kakao_api_key"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val CUSTOM_CHIP_ORIGINAL_TEXT = "✏️ 직접 입력"
    }

    // 직접 입력 충 ID 세트 (렌더링 후 id가 확정되뮼 로 lazy 참조)
    private val customChipIds: Set<Int> by lazy {
        setOf(
            R.id.chip_people_custom,
            R.id.chip_budget_custom,
            R.id.chip_mood_custom,
            R.id.chip_situation_custom
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupRecyclerView()
        setupListeners()
        loadSavedLocationMessage()
    }

    private fun setupRecyclerView() {
        restaurantAdapter = RestaurantAdapter(emptyList()) { restaurant ->
            showRestaurantDetailDialog(restaurant)
        }
        binding.recyclerRestaurants.layoutManager = LinearLayoutManager(this)
        binding.recyclerRestaurants.adapter = restaurantAdapter
    }

    private fun setupListeners() {
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnGetLocation.setOnClickListener {
            checkLocationPermissionsAndGet()
        }

        binding.btnRecommend.setOnClickListener {
            validateAndRecommend()
        }

        setupCustomInputChips()
    }

    private fun setupCustomInputChips() {
        data class CustomChipConfig(val chip: Chip, val group: ChipGroup, val title: String, val hint: String)

        val configs = listOf(
            CustomChipConfig(
                chip = binding.chipPeopleCustom,
                group = binding.groupPeople,
                title = "포함 인원 입력",
                hint = "예) 12명, 20명 단체"
            ),
            CustomChipConfig(
                chip = binding.chipBudgetCustom,
                group = binding.groupBudget,
                title = "인당 예산 입력",
                hint = "예) 인당 15만원, 의놀리 없이"
            ),
            CustomChipConfig(
                chip = binding.chipMoodCustom,
                group = binding.groupMood,
                title = "먹고 싶은 음식 입력",
                hint = "예) 삼격살, 낙지확, 냉면"
            ),
            CustomChipConfig(
                chip = binding.chipSituationCustom,
                group = binding.groupSituation,
                title = "식사 상황 입력",
                hint = "예) 생일 파티, 졸업 기념, 동창회"
            )
        )

        for (config in configs) {
            config.chip.setOnClickListener {
                showCustomInputDialog(config.chip, config.group, config.title, config.hint)
            }
        }
    }

    private fun showCustomInputDialog(chip: Chip, group: ChipGroup, title: String, hint: String) {
        val editText = EditText(this).apply {
            this.hint = hint
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            // 이전에 입력한 값이 있으면 미리 채움기
            val prevValue = chip.tag as? String
            if (prevValue != null) setText(prevValue)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        AlertDialog.Builder(this)
            .setTitle("✏️ $title")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    chip.tag = input          // 나중에 다시 열때 사용
                    chip.text = input         // 충 텍스트를 입력값으로 교체
                    group.check(chip.id)      // 해당 충 선택
                } else {
                    // 빈 입력 → 선택 해제 + 텍스트 복원
                    chip.tag = null
                    chip.text = CUSTOM_CHIP_ORIGINAL_TEXT
                    group.clearCheck()
                }
            }
            .setNegativeButton("취소") { _, _ ->
                // 이전에 입력한 값이 없으면 선택 해제
                if (chip.tag == null) {
                    chip.text = CUSTOM_CHIP_ORIGINAL_TEXT
                    group.clearCheck()
                }
            }
            .setOnCancelListener {
                if (chip.tag == null) {
                    chip.text = CUSTOM_CHIP_ORIGINAL_TEXT
                    group.clearCheck()
                }
            }
            .show()
            .also { dialog ->
                // 키보드 자동 열기
                dialog.window?.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
            }
    }

    private fun loadSavedLocationMessage() {
        if (currentLatitude == null || currentLongitude == null) {
            binding.tvLocationStatus.text = "위치 정보가 필요합니다. '현재 위치 조회' 버튼을 눌러주세요."
        } else {
            binding.tvLocationStatus.text = "위치 획득 성공! 위도: $currentLatitude, 경도: $currentLongitude"
        }
    }

    // --- Permissions & Location Handling ---
    private fun checkLocationPermissionsAndGet() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            fetchLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                Toast.makeText(this, "위치 권한이 거부되어 현재 위치 정보를 가져올 수 없습니다.", Toast.LENGTH_LONG).show()
                binding.tvLocationStatus.text = "오류: 위치 권한 허용이 필요합니다."
            }
        }
    }

    private fun fetchLocation() {
        try {
            binding.tvLocationStatus.text = "현재 위치 정보 확인 중..."
            
            // Check permissions explicitly to satisfy lint
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    updateLocationCoordinates(location)
                } else {
                    // In some cases (e.g. fresh emulator), lastLocation is null. Request a single fresh update.
                    requestFreshLocation()
                }
            }.addOnFailureListener {
                binding.tvLocationStatus.text = "위치 정보를 가져오는데 실패했습니다: ${it.localizedMessage}"
                Toast.makeText(this, "위치 조회 실패. GPS 상태를 확인하세요.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            binding.tvLocationStatus.text = "보안 오류: 위치 권한을 확인해 주세요."
        }
    }

    private fun requestFreshLocation() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1)
                .build()

            // Check permissions explicitly to satisfy lint
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val lastLoc = locationResult.lastLocation
                        if (lastLoc != null) {
                            updateLocationCoordinates(lastLoc)
                        } else {
                            binding.tvLocationStatus.text = "안내: 위치 정보를 검색할 수 없습니다. GPS를 켜주세요."
                        }
                    }
                },
                mainLooper
            )
        } catch (e: SecurityException) {
            binding.tvLocationStatus.text = "보안 오류: 위치 조회가 불가합니다."
        }
    }

    private fun updateLocationCoordinates(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        binding.tvLocationStatus.text = "위치 획득 성공!\n위도: ${location.latitude}, 경도: ${location.longitude}"
        Toast.makeText(this, "현재 위치 정보를 성공적으로 불러왔습니다.", Toast.LENGTH_SHORT).show()

        // 한국 좌표 범위 체크 (위도 33~38, 경도 124~132)
        val isKorea = location.latitude in 33.0..38.5 && location.longitude in 124.0..132.0
        if (!isKorea) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ 위치 경고")
                .setMessage(
                    "현재 위치가 한국 외 지역(${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)})으로 감지되었습니다.\n\n" +
                    "에뮬레이터를 사용 중이라면 가상 위치를 한국으로 설정해야 합니다:\n\n" +
                    "📌 에뮬레이터 설정 방법:\n" +
                    "1. 에뮬레이터 우측 점 3개(⋮) 클릭\n" +
                    "2. [Location] 탭 선택\n" +
                    "3. 위도(Latitude): 37.5665\n" +
                    "   경도(Longitude): 126.9780 입력 (서울 기준)\n" +
                    "4. [Set Location] 버튼 클릭\n" +
                    "5. 앱에서 '현재 위치 조회' 다시 클릭"
                )
                .setPositiveButton("확인", null)
                .show()
        }
    }

    // --- Settings Dialog ---
    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etKakaoKey = dialogView.findViewById<EditText>(R.id.et_kakao_key)
        val etGeminiKey = dialogView.findViewById<EditText>(R.id.et_gemini_key)

        // Load existing values
        etKakaoKey.setText(sharedPreferences.getString(KEY_KAKAO_API, ""))
        etGeminiKey.setText(sharedPreferences.getString(KEY_GEMINI_API, ""))

        AlertDialog.Builder(this)
            .setTitle("API 설정 🔑")
            .setView(dialogView)
            .setPositiveButton("저장") { dialog, _ ->
                val kakaoKey = etKakaoKey.text.toString().trim()
                val geminiKey = etGeminiKey.text.toString().trim()

                sharedPreferences.edit()
                    .putString(KEY_KAKAO_API, kakaoKey)
                    .putString(KEY_GEMINI_API, geminiKey)
                    .apply()

                Toast.makeText(this, "API 키가 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // --- Recommendation Logic ---
    private fun validateAndRecommend() {
        // 1. Check API Keys
        val kakaoKey = sharedPreferences.getString(KEY_KAKAO_API, "") ?: ""
        val geminiKey = sharedPreferences.getString(KEY_GEMINI_API, "") ?: ""

        if (kakaoKey.isEmpty() || geminiKey.isEmpty()) {
            Toast.makeText(this, "서비스 이용을 위해 API 설정에서 키를 먼저 등록해주세요.", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }

        // 2. Check Location
        val lat = currentLatitude
        val lng = currentLongitude
        if (lat == null || lng == null) {
            showValidationWarning("현재 위치 정보가 없습니다.\n1단계에서 위치 조회 버튼을 클릭하여 위치 정보를 먼저 획득해주세요.")
            return
        }

        // 3. Check Chips
        val peopleChipId = binding.groupPeople.checkedChipId
        val budgetChipId = binding.groupBudget.checkedChipId
        val moodChipId = binding.groupMood.checkedChipId
        val situationChipId = binding.groupSituation.checkedChipId

        if (peopleChipId == View.NO_ID || budgetChipId == View.NO_ID || moodChipId == View.NO_ID || situationChipId == View.NO_ID) {
            val missingFields = ArrayList<String>()
            if (peopleChipId == View.NO_ID) missingFields.add("인원 수")
            if (budgetChipId == View.NO_ID) missingFields.add("예산")
            if (moodChipId == View.NO_ID) missingFields.add("기분/종류")
            if (situationChipId == View.NO_ID) missingFields.add("식사 상황")

            showValidationWarning("추천을 받으려면 다음 필수 조건을 모두 선택해야 합니다:\n-> ${missingFields.joinToString(", ")}")
            return
        }

        val peopleText = findViewById<Chip>(peopleChipId).text.toString()
        val budgetText = findViewById<Chip>(budgetChipId).text.toString()
        val moodText = findViewById<Chip>(moodChipId).text.toString()
        val situationText = findViewById<Chip>(situationChipId).text.toString()

        // Hide results, show progress
        binding.layoutResults.visibility = View.GONE
        binding.cardLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = "주변 음식점을 검색하고 있습니다..."

        // Run network requests asynchronously using coroutines
        lifecycleScope.launch {
            try {
                // Step 2.1: Fetch Restaurants from Kakao Local API
                val authHeader = "KakaoAK $kakaoKey"
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.kakaoService.getNearbyRestaurants(
                        authorizationHeader = authHeader,
                        longitude = lng.toString(),
                        latitude = lat.toString()
                    )
                }

                val originalDocuments = response.documents
                if (originalDocuments.isEmpty()) {
                    showErrorState("주변 음식점을 찾을 수 없습니다. 위치가 올바른지 확인하고 다시 시도하세요.")
                    return@launch
                }

                // Step 2.2: 반경 2km(2000m) 초과 결과 필터링 후 중복 제거
                val withinRadiusDocs = originalDocuments.filter { doc ->
                    val distanceMeters = doc.distance.toIntOrNull() ?: Int.MAX_VALUE
                    distanceMeters <= 2000
                }

                if (withinRadiusDocs.isEmpty()) {
                    showErrorState("반경 2km 내 음식점을 찾을 수 없습니다. 위치를 확인하거나 다시 시도해 주세요.")
                    return@launch
                }

                // Step 2.3: 이름/ID 기준 중복 제거
                val uniqueDocs = ArrayList<KakaoDocument>()
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

                binding.tvLoadingText.text = "반경 2km 내 ${uniqueDocs.size}개 음식점 중 AI가 최적 맛집을 선정 중..."

                // Step 2.4: Build Gemini Prompt
                val prompt = buildGeminiPrompt(peopleText, budgetText, moodText, situationText, uniqueDocs)

                // Request Gemini Recommendation
                val geminiRequest = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )

                val geminiResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.geminiService.generateRecommendation(
                        apiKey = geminiKey,
                        request = geminiRequest
                    )
                }

                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (responseText.isNullOrEmpty()) {
                    showErrorState("AI 추천 결과를 읽어오지 못했습니다. 다시 시도해 주세요.")
                    return@launch
                }

                // Parse the recommendation JSON from Gemini
                val recommendedItems = parseGeminiResponse(responseText)
                if (recommendedItems.isEmpty()) {
                    showErrorState("조건에 맞는 식당 추천 결과를 생성하지 못했습니다.")
                    return@launch
                }

                // Map Gemini recommendations back to Kakao restaurant data
                val finalRecommendations = ArrayList<RecommendedRestaurant>()
                for (item in recommendedItems) {
                    // Loose matching: Find best match in our Kakao response
                    val matchedDoc = uniqueDocs.find { doc ->
                        doc.place_name.equals(item.name, ignoreCase = true) ||
                                doc.place_name.contains(item.name, ignoreCase = true) ||
                                item.name.contains(doc.place_name, ignoreCase = true)
                    }

                    if (matchedDoc != null) {
                        finalRecommendations.add(
                            RecommendedRestaurant(
                                name = matchedDoc.place_name,
                                category = matchedDoc.category_name,
                                distance = matchedDoc.distance,
                                address = matchedDoc.road_address_name.ifEmpty { matchedDoc.address_name },
                                phone = matchedDoc.phone.ifEmpty { "전화번호 정보 없음" },
                                reason = item.reason,
                                url = matchedDoc.place_url
                            )
                        )
                    }
                }

                // If no exact match (Gemini Hallucination), fallback to displaying matching items or first items
                if (finalRecommendations.isEmpty()) {
                    // Fallback to top 3 from Kakao with a general reason
                    for (i in 0 until minOf(3, uniqueDocs.size)) {
                        val doc = uniqueDocs[i]
                        finalRecommendations.add(
                            RecommendedRestaurant(
                                name = doc.place_name,
                                category = doc.category_name,
                                distance = doc.distance,
                                address = doc.road_address_name.ifEmpty { doc.address_name },
                                phone = doc.phone.ifEmpty { "전화번호 정보 없음" },
                                reason = "현재 조건(인원 $peopleText, 예산 $budgetText, $moodText, $situationText)에 적절한 주변 인기 음식점입니다.",
                                url = doc.place_url
                            )
                        )
                    }
                }

                // Update UI with final recommendations
                binding.cardLoading.visibility = View.GONE
                binding.layoutResults.visibility = View.VISIBLE
                restaurantAdapter.updateData(finalRecommendations)

            } catch (e: Exception) {
                showErrorState("네트워크 오류 또는 API 요청 중 문제가 발생했습니다.\n\n[상세 내용]\n${e.localizedMessage}\n\nAPI 키와 네트워크 연결을 확인하고 다시 시도하세요.")
            }
        }
    }

    private fun buildGeminiPrompt(
        people: String,
        budget: String,
        mood: String,
        situation: String,
        docs: List<KakaoDocument>
    ): String {
        val sb = StringBuilder()
        sb.append("너는 맛집 추천 전문가야. 다음은 사용자의 현재 조건과 카카오 Local API로 검색된 반경 2km 이내 주변 음식점 목록이야.\n\n")
        sb.append("[선택 조건]\n")
        sb.append("- 인원 수: $people\n")
        sb.append("- 예산 (인당): $budget\n")
        sb.append("- 기분 및 맛: $mood\n")
        sb.append("- 식사 상황: $situation\n\n")

        sb.append("[반경 2km 이내 주변 음식점 목록 - 총 ${docs.size}개]\n")
        for (i in docs.indices) {
            val doc = docs[i]
            sb.append("${i + 1}. 이름: ${doc.place_name} | 카테고리: ${doc.category_name} | 거리: ${doc.distance}m | 주소: ${doc.address_name}\n")
        }
        sb.append("\n[중요 규칙]\n")
        sb.append("- 반드시 위의 '주변 음식점 목록'에 있는 음식점 중에서만 추천해야 해.\n")
        sb.append("- 목록에 없는 새로운 식당이나 서울, 인천, 대구 등 다른 도시의 식당은 절대 추천하면 안 돼.\n")
        sb.append("- 모든 추천 식당은 실제로 목록에 존재하는 이름을 그대로 사용해야 해.\n\n")
        sb.append("위 목록 중에서 사용자의 조건(예산, 기분, 상황)에 가장 적합한 음식점을 최대 3개 엄선해서 추천해줘.\n\n")
        sb.append("결과는 다음 형태의 JSON 배열(Array) 형식으로만 응답해줘. 추가적인 텍스트나 설명, ```json 마크다운 기호 등은 일절 제외하고 순수 JSON 데이터만 출력해줘:\n")
        sb.append("[\n")
        sb.append("  {\n")
        sb.append("    \"name\": \"식당 이름 (목록에 있는 정확한 이름 그대로 입력)\",\n")
        sb.append("    \"reason\": \"이 식당을 추천하는 구체적이고 설득력 있는 이유 (선택 조건인 예산, 기분, 상황과 직접 연관시켜 한국어로 1~2문장 기술)\"\n")
        sb.append("  }\n")
        sb.append("]\n")

        return sb.toString()
    }

    private fun parseGeminiResponse(rawText: String): List<RecommendationResultItem> {
        return try {
            // Clean up Gemini output to be absolutely safe
            var cleanedJson = rawText.trim()
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substring(7)
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substring(3)
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.length - 3)
            }
            cleanedJson = cleanedJson.trim()

            val type = object : TypeToken<List<RecommendationResultItem>>() {}.type
            Gson().fromJson(cleanedJson, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun showValidationWarning(message: String) {
        AlertDialog.Builder(this)
            .setTitle("안내 ⚠️")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showErrorState(errorMessage: String) {
        binding.cardLoading.visibility = View.GONE
        binding.layoutResults.visibility = View.GONE

        AlertDialog.Builder(this)
            .setTitle("오류 발생 ❌")
            .setMessage(errorMessage)
            .setPositiveButton("확인", null)
            .show()
    }

    // --- Details Dialog ---
    private fun showRestaurantDetailDialog(restaurant: RecommendedRestaurant) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(restaurant.name)

        val message = """
            📍 업종: ${restaurant.category.split(">").lastOrNull()?.trim() ?: restaurant.category}
            📏 거리: ${restaurant.distance}m
            🏠 주소: ${restaurant.address}
            📞 전화번호: ${restaurant.phone}
            
            💬 AI 추천 이유:
            ${restaurant.reason}
        """.trimIndent()

        builder.setMessage(message)
        builder.setPositiveButton("확인", null)

        if (restaurant.url.isNotEmpty()) {
            builder.setNeutralButton("카카오맵에서 보기") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(restaurant.url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "웹 브라우저를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.show()
    }
}