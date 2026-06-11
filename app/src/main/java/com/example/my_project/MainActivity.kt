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
import android.webkit.JavascriptInterface
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
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var restaurantAdapter: RestaurantAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    // App state management
    private enum class AppState {
        PERMISSION_GUIDE,
        LOCATION_CONFIRM,
        WIZARD_PEOPLE,
        WIZARD_BUDGET,
        WIZARD_MOOD,
        WIZARD_SITUATION,
        FINAL_CONFIRM,
        RESULTS
    }

    private var currentState = AppState.PERMISSION_GUIDE

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "EatingPrefs"
        private const val KEY_KAKAO_API = "kakao_api_key"
        private const val KEY_KAKAO_JS = "kakao_js_key"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val CUSTOM_CHIP_ORIGINAL_TEXT = "✏️ 직접 입력"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupBottomSheet()
        setupRecyclerView()
        setupListeners()
        setupCustomInputChips()

        // Check permission state on launch and enter the correct screen
        checkInitialPermissionState()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        binding.btnTriggerRecommend.visibility = View.VISIBLE
                        binding.btnTriggerRecommend.alpha = 1f
                    }
                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        binding.btnTriggerRecommend.visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.btnTriggerRecommend.visibility = View.GONE
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (bottomSheetBehavior.isHideable && slideOffset < 0f) {
                    binding.btnTriggerRecommend.visibility = View.VISIBLE
                    binding.btnTriggerRecommend.alpha = 1f + slideOffset
                } else {
                    binding.btnTriggerRecommend.visibility = View.GONE
                }
            }
        })
    }

    private fun checkInitialPermissionState() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            currentState = AppState.LOCATION_CONFIRM
            updateUIForState(currentState)
            fetchLocation()
        } else {
            currentState = AppState.PERMISSION_GUIDE
            updateUIForState(currentState)
        }
    }

    private fun updateUIForState(state: AppState) {
        currentState = state
        
        // Hide all screens first
        binding.layoutPermissionGuide.visibility = View.GONE
        binding.layoutWizard.visibility = View.GONE
        binding.layoutFinalConfirm.visibility = View.GONE
        binding.cardLoading.visibility = View.GONE
        binding.layoutResults.visibility = View.GONE

        when (state) {
            AppState.PERMISSION_GUIDE -> {
                binding.layoutPermissionGuide.visibility = View.VISIBLE
                binding.btnTriggerRecommend.visibility = View.GONE
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            AppState.LOCATION_CONFIRM -> {
                binding.btnTriggerRecommend.visibility = View.VISIBLE
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            AppState.WIZARD_PEOPLE, AppState.WIZARD_BUDGET, AppState.WIZARD_MOOD, AppState.WIZARD_SITUATION -> {
                binding.layoutWizard.visibility = View.VISIBLE
                bottomSheetBehavior.peekHeight = 0
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                
                // Toggle sub-steps in the wizard
                binding.stepPeople.visibility = if (state == AppState.WIZARD_PEOPLE) View.VISIBLE else View.GONE
                binding.stepBudget.visibility = if (state == AppState.WIZARD_BUDGET) View.VISIBLE else View.GONE
                binding.stepMood.visibility = if (state == AppState.WIZARD_MOOD) View.VISIBLE else View.GONE
                binding.stepSituation.visibility = if (state == AppState.WIZARD_SITUATION) View.VISIBLE else View.GONE

                // Update Progress indicators
                when (state) {
                    AppState.WIZARD_PEOPLE -> {
                        binding.tvWizardProgressText.text = "👥 추천 조건 선택: 인원 수 (1 / 4 단계)"
                        binding.wizardProgressBar.progress = 1
                    }
                    AppState.WIZARD_BUDGET -> {
                        binding.tvWizardProgressText.text = "💰 추천 조건 선택: 예산 (2 / 4 단계)"
                        binding.wizardProgressBar.progress = 2
                    }
                    AppState.WIZARD_MOOD -> {
                        binding.tvWizardProgressText.text = "😋 추천 조건 선택: 기분 / 맛 (3 / 4 단계)"
                        binding.wizardProgressBar.progress = 3
                    }
                    AppState.WIZARD_SITUATION -> {
                        binding.tvWizardProgressText.text = "🍽️ 추천 조건 선택: 식사 상황 (4 / 4 단계)"
                        binding.wizardProgressBar.progress = 4
                    }
                    else -> {}
                }
            }
            AppState.FINAL_CONFIRM -> {
                populateSummary()
                binding.layoutFinalConfirm.visibility = View.VISIBLE
                bottomSheetBehavior.peekHeight = 0
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            AppState.RESULTS -> {
                binding.layoutResults.visibility = View.VISIBLE
                val density = resources.displayMetrics.density
                bottomSheetBehavior.peekHeight = (240 * density).toInt()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        // Scroll to the top of screen
        binding.bottomSheet.smoothScrollTo(0, 0)
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

        // Screen 1: Permission request
        binding.btnPermissionApprove.setOnClickListener {
            checkLocationPermissionsAndGet()
        }

        // Floating Recommend Trigger
        binding.btnTriggerRecommend.setOnClickListener {
            if (currentLatitude == null || currentLongitude == null) {
                Toast.makeText(this, "위치 정보를 가져오는 중입니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                fetchLocation()
                return@setOnClickListener
            }
            if (currentState == AppState.RESULTS) {
                val density = resources.displayMetrics.density
                bottomSheetBehavior.peekHeight = (240 * density).toInt()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                updateUIForState(AppState.WIZARD_PEOPLE)
            }
        }

        binding.btnLocationReFetch.setOnClickListener {
            fetchLocation()
        }

        binding.btnLocationManualSearch.setOnClickListener {
            showManualLocationSearchDialog()
        }

        // Screen 3: Wizard Navigation
        binding.btnWizardPrev.setOnClickListener {
            when (currentState) {
                AppState.WIZARD_PEOPLE -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
                AppState.WIZARD_BUDGET -> updateUIForState(AppState.WIZARD_PEOPLE)
                AppState.WIZARD_MOOD -> updateUIForState(AppState.WIZARD_BUDGET)
                AppState.WIZARD_SITUATION -> updateUIForState(AppState.WIZARD_MOOD)
                else -> {}
            }
        }

        binding.btnWizardNext.setOnClickListener {
            validateStepAndGoNext()
        }

        // Screen 4: Final Confirm
        binding.btnFinalRecommend.setOnClickListener {
            validateAndRecommend()
        }

        binding.btnFinalReset.setOnClickListener {
            updateUIForState(AppState.WIZARD_PEOPLE)
        }

        // Screen 5: Start Over
        binding.btnStartOver.setOnClickListener {
            binding.groupPeople.clearCheck()
            binding.groupBudget.clearCheck()
            binding.groupMood.clearCheck()
            binding.groupSituation.clearCheck()

            resetCustomChip(binding.chipPeopleCustom)
            resetCustomChip(binding.chipBudgetCustom)
            resetCustomChip(binding.chipMoodCustom)
            resetCustomChip(binding.chipSituationCustom)

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            updateUIForState(AppState.LOCATION_CONFIRM)
        }
    }

    private fun resetCustomChip(chip: Chip) {
        chip.tag = null
        chip.text = CUSTOM_CHIP_ORIGINAL_TEXT
    }

    private fun validateStepAndGoNext() {
        when (currentState) {
            AppState.WIZARD_PEOPLE -> {
                if (binding.groupPeople.checkedChipId == View.NO_ID) {
                    Toast.makeText(this, "식사 인원을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return
                }
                updateUIForState(AppState.WIZARD_BUDGET)
            }
            AppState.WIZARD_BUDGET -> {
                if (binding.groupBudget.checkedChipId == View.NO_ID) {
                    Toast.makeText(this, "예산을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return
                }
                updateUIForState(AppState.WIZARD_MOOD)
            }
            AppState.WIZARD_MOOD -> {
                if (binding.groupMood.checkedChipId == View.NO_ID) {
                    Toast.makeText(this, "기분 또는 맛의 종류를 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return
                }
                updateUIForState(AppState.WIZARD_SITUATION)
            }
            AppState.WIZARD_SITUATION -> {
                if (binding.groupSituation.checkedChipId == View.NO_ID) {
                    Toast.makeText(this, "식사 상황을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return
                }
                updateUIForState(AppState.FINAL_CONFIRM)
            }
            else -> {}
        }
    }

    private fun populateSummary() {
        val fullAddr = binding.tvConfirmedAddress.text.toString().split("\n").firstOrNull() ?: ""
        binding.tvSummaryLocation.text = fullAddr.replace("📍", "").trim()

        val peopleChipId = binding.groupPeople.checkedChipId
        binding.tvSummaryPeople.text = if (peopleChipId != View.NO_ID) findViewById<Chip>(peopleChipId).text.toString() else "선택 안 됨"

        val budgetChipId = binding.groupBudget.checkedChipId
        binding.tvSummaryBudget.text = if (budgetChipId != View.NO_ID) findViewById<Chip>(budgetChipId).text.toString() else "선택 안 됨"

        val moodChipId = binding.groupMood.checkedChipId
        binding.tvSummaryMood.text = if (moodChipId != View.NO_ID) findViewById<Chip>(moodChipId).text.toString() else "선택 안 됨"

        val situationChipId = binding.groupSituation.checkedChipId
        binding.tvSummarySituation.text = if (situationChipId != View.NO_ID) findViewById<Chip>(situationChipId).text.toString() else "선택 안 됨"
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
                    chip.tag = input
                    chip.text = input
                    group.check(chip.id)
                } else {
                    chip.tag = null
                    chip.text = CUSTOM_CHIP_ORIGINAL_TEXT
                    group.clearCheck()
                }
            }
            .setNegativeButton("취소") { _, _ ->
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
                dialog.window?.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
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
            updateUIForState(AppState.LOCATION_CONFIRM)
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
                updateUIForState(AppState.LOCATION_CONFIRM)
                fetchLocation()
            } else {
                Toast.makeText(this, "위치 권한이 거부되어 현재 위치 정보를 가져올 수 없습니다.", Toast.LENGTH_LONG).show()
                updateUIForState(AppState.PERMISSION_GUIDE)
            }
        }
    }

    private fun fetchLocation() {
        try {
            binding.tvConfirmedAddress.text = "현재 위치 정보 확인 중..."

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            requestFreshLocation()
        } catch (e: SecurityException) {
            binding.tvConfirmedAddress.text = "보안 오류: 위치 권한을 확인해 주세요."
        }
    }

    private fun requestFreshLocation() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1)
                .build()

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
                            binding.tvConfirmedAddress.text = "안내: 위치 정보를 검색할 수 없습니다. GPS를 켜주세요."
                        }
                    }
                },
                mainLooper
            )
        } catch (e: SecurityException) {
            binding.tvConfirmedAddress.text = "보안 오류: 위치 조회가 불가합니다."
        }
    }

    private fun updateLocationCoordinates(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        
        Toast.makeText(this, "현재 위치 정보를 성공적으로 불러왔습니다.", Toast.LENGTH_SHORT).show()

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
                    "5. 앱에서 '위치 다시 조회' 클릭"
                )
                .setPositiveButton("확인", null)
                .show()
        }

        convertCoordsToAddress(location.latitude, location.longitude)
        
        // Center the map view immediately on startup / location update
        loadMapInWebView(location.latitude, location.longitude, emptyList())
    }

    private fun convertCoordsToAddress(lat: Double, lng: Double) {
        val kakaoKey = sharedPreferences.getString(KEY_KAKAO_API, "") ?: ""
        if (kakaoKey.isEmpty()) {
            binding.tvConfirmedAddress.text = "📍 위도: ${String.format("%.4f", lat)}, 경도: ${String.format("%.4f", lng)}\n(카카오 REST API 키가 등록되지 않아 상세 주소를 가져올 수 없습니다.)"
            return
        }

        lifecycleScope.launch {
            try {
                val authHeader = "KakaoAK $kakaoKey"
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.kakaoService.coord2Address(
                        authorizationHeader = authHeader,
                        longitude = lng.toString(),
                        latitude = lat.toString()
                    )
                }
                val document = response.documents.firstOrNull()
                val roadAddress = document?.roadAddress?.addressName
                val baseAddress = document?.address?.addressName
                val addressText = roadAddress ?: baseAddress ?: "주소 정보가 존재하지 않습니다."
                
                binding.tvConfirmedAddress.text = "📍 $addressText\n(위도: ${String.format("%.4f", lat)}, 경도: ${String.format("%.4f", lng)})"
            } catch (e: Exception) {
                binding.tvConfirmedAddress.text = "📍 위도: ${String.format("%.4f", lat)}, 경도: ${String.format("%.4f", lng)}\n(주소 변환 실패: ${e.localizedMessage})"
            }
        }
    }

    private fun showManualLocationSearchDialog() {
        val kakaoKey = sharedPreferences.getString(KEY_KAKAO_API, "") ?: ""
        if (kakaoKey.isEmpty()) {
            Toast.makeText(this, "위치 검색을 위해 카카오 REST API 키를 먼저 설정해 주세요.", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }

        val searchEditText = EditText(this).apply {
            hint = "예) 홍대입구역, 강남역, 서교동 등"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        AlertDialog.Builder(this)
            .setTitle("🔎 위치 직접 검색")
            .setView(searchEditText)
            .setPositiveButton("검색") { _, _ ->
                val query = searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    performKeywordSearch(query, kakaoKey)
                } else {
                    Toast.makeText(this, "검색어를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performKeywordSearch(query: String, kakaoKey: String) {
        lifecycleScope.launch {
            try {
                val authHeader = "KakaoAK $kakaoKey"
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.kakaoService.searchKeyword(
                        authorizationHeader = authHeader,
                        query = query
                    )
                }

                val docs = response.documents
                if (docs.isEmpty()) {
                    Toast.makeText(this@MainActivity, "검색 결과가 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val itemNames = docs.map { doc ->
                    val road = doc.roadAddressName.ifEmpty { doc.addressName }
                    "[${doc.placeName}] $road"
                }.toTypedArray()

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("검색 위치 목록 선택")
                    .setItems(itemNames) { _, which ->
                        val selectedDoc = docs[which]
                        val lat = selectedDoc.y.toDoubleOrNull() ?: 0.0
                        val lng = selectedDoc.x.toDoubleOrNull() ?: 0.0
                        currentLatitude = lat
                        currentLongitude = lng

                        val road = selectedDoc.roadAddressName.ifEmpty { selectedDoc.addressName }
                        binding.tvConfirmedAddress.text = "📍 [${selectedDoc.placeName}] $road\n(위도: ${String.format("%.4f", lat)}, 경도: ${String.format("%.4f", lng)})"
                        Toast.makeText(this@MainActivity, "위치가 수동으로 설정되었습니다.", Toast.LENGTH_SHORT).show()
                        
                        // Center the map view on the manually searched location
                        loadMapInWebView(lat, lng, emptyList())
                    }
                    .setNegativeButton("취소", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "장소 검색 오류: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Settings Dialog ---
    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etKakaoKey = dialogView.findViewById<EditText>(R.id.et_kakao_key)
        val etKakaoJSKey = dialogView.findViewById<EditText>(R.id.et_kakao_js_key)
        val etGeminiKey = dialogView.findViewById<EditText>(R.id.et_gemini_key)

        // Load existing values
        etKakaoKey.setText(sharedPreferences.getString(KEY_KAKAO_API, ""))
        etKakaoJSKey.setText(sharedPreferences.getString(KEY_KAKAO_JS, ""))
        etGeminiKey.setText(sharedPreferences.getString(KEY_GEMINI_API, ""))

        AlertDialog.Builder(this)
            .setTitle("API 설정 🔑")
            .setView(dialogView)
            .setPositiveButton("저장") { dialog, _ ->
                val kakaoKey = etKakaoKey.text.toString().trim()
                val kakaoJSKey = etKakaoJSKey.text.toString().trim()
                val geminiKey = etGeminiKey.text.toString().trim()

                sharedPreferences.edit()
                    .putString(KEY_KAKAO_API, kakaoKey)
                    .putString(KEY_KAKAO_JS, kakaoJSKey)
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
        val kakaoKey = sharedPreferences.getString(KEY_KAKAO_API, "") ?: ""
        val geminiKey = sharedPreferences.getString(KEY_GEMINI_API, "") ?: ""

        if (kakaoKey.isEmpty() || geminiKey.isEmpty()) {
            Toast.makeText(this, "서비스 이용을 위해 API 설정에서 키를 먼저 등록해주세요.", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }

        val lat = currentLatitude
        val lng = currentLongitude
        if (lat == null || lng == null) {
            showValidationWarning("위치 정보가 필요합니다. 1단계에서 위치 조회를 완료해 주세요.")
            return
        }

        val peopleChipId = binding.groupPeople.checkedChipId
        val budgetChipId = binding.groupBudget.checkedChipId
        val moodChipId = binding.groupMood.checkedChipId
        val situationChipId = binding.groupSituation.checkedChipId

        if (peopleChipId == View.NO_ID || budgetChipId == View.NO_ID || moodChipId == View.NO_ID || situationChipId == View.NO_ID) {
            showValidationWarning("추천을 받으려면 모든 항목의 선택이 완료되어야 합니다.")
            return
        }

        val peopleText = findViewById<Chip>(peopleChipId).text.toString()
        val budgetText = findViewById<Chip>(budgetChipId).text.toString()
        val moodText = findViewById<Chip>(moodChipId).text.toString()
        val situationText = findViewById<Chip>(situationChipId).text.toString()

        // Switch to progress visibility
        binding.layoutFinalConfirm.visibility = View.GONE
        binding.cardLoading.visibility = View.VISIBLE
        binding.tvLoadingText.text = "주변 음식점을 검색하고 있습니다..."

        lifecycleScope.launch {
            try {
                // Fetch Nearby Restaurants
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

                // Filter within 2km radius
                val withinRadiusDocs = originalDocuments.filter { doc ->
                    val distanceMeters = doc.distance.toIntOrNull() ?: Int.MAX_VALUE
                    distanceMeters <= 2000
                }

                if (withinRadiusDocs.isEmpty()) {
                    showErrorState("반경 2km 내 음식점을 찾을 수 없습니다. 위치를 확인하거나 다시 시도해 주세요.")
                    return@launch
                }

                // Remove duplicates by name / id
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

                // Call Gemini API
                val prompt = buildGeminiPrompt(peopleText, budgetText, moodText, situationText, uniqueDocs)
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

                val recommendedItems = parseGeminiResponse(responseText)
                if (recommendedItems.isEmpty()) {
                    showErrorState("조건에 맞는 식당 추천 결과를 생성하지 못했습니다.")
                    return@launch
                }

                // Map results back to Kakao models
                val finalRecommendations = ArrayList<RecommendedRestaurant>()
                for (item in recommendedItems) {
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
                                url = matchedDoc.place_url,
                                latitude = matchedDoc.y.toDoubleOrNull() ?: 0.0,
                                longitude = matchedDoc.x.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                }

                // Fallback to top 3 from list if no exact match (hallucinations)
                if (finalRecommendations.isEmpty()) {
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
                                url = doc.place_url,
                                latitude = doc.y.toDoubleOrNull() ?: 0.0,
                                longitude = doc.x.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                }

                // Update UI state to RESULTS
                binding.cardLoading.visibility = View.GONE
                updateUIForState(AppState.RESULTS)
                restaurantAdapter.updateData(finalRecommendations)

                // Load Kakao Map WebView dynamically
                loadMapInWebView(lat, lng, finalRecommendations)

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
        binding.cardLoading.visibility = View.GONE
        updateUIForState(AppState.FINAL_CONFIRM)

        AlertDialog.Builder(this)
            .setTitle("안내 ⚠️")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showErrorState(errorMessage: String) {
        binding.cardLoading.visibility = View.GONE
        updateUIForState(AppState.FINAL_CONFIRM)

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

    // --- WebView Kakao Map Javascript Integration ---
    private data class MapRestaurantJson(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val index: Int
    )

    private fun loadMapInWebView(
        currentLat: Double,
        currentLng: Double,
        recommendedList: List<RecommendedRestaurant>
    ) {
        val jsKey = sharedPreferences.getString(KEY_KAKAO_JS, "") ?: ""
        if (jsKey.isEmpty()) {
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8"/>
                    <style>
                        body {
                            font-family: sans-serif;
                            padding: 20px;
                            text-align: center;
                            background-color: #FFEBE7;
                            color: #D84315;
                        }
                    </style>
                </head>
                <body>
                    <h3>⚠️ 카카오 JavaScript 키가 저장되어 있지 않습니다.</h3>
                    <p>상단의 설정(⚙️) 메뉴를 터치하여 카카오 JavaScript API 키를 입력해 주세요.</p>
                </body>
                </html>
            """.trimIndent()
            binding.webViewMap.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            return
        }

        val mapList = recommendedList.mapIndexed { idx, rest ->
            MapRestaurantJson(
                name = rest.name,
                latitude = rest.latitude,
                longitude = rest.longitude,
                index = idx
            )
        }
        val restaurantsJson = Gson().toJson(mapList)

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no">
                <style>
                    html, body, #map {
                        width: 100%;
                        height: 100%;
                        margin: 0;
                        padding: 0;
                        background-color: #F9F8F6;
                    }
                </style>
                <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=$jsKey&autoload=false"></script>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    console.log("Kakao Map HTML script is starting execution");
                    window.onerror = function(msg, url, line) {
                        console.error("JS Error: " + msg + " at " + url + ":" + line);
                        return false;
                    };
                    kakao.maps.load(function() {
                        try {
                            var mapContainer = document.getElementById('map'),
                                mapOption = { 
                                    center: new kakao.maps.LatLng($currentLat, $currentLng),
                                    level: 4
                                };

                            var map = new kakao.maps.Map(mapContainer, mapOption);

                            setTimeout(function() {
                                map.relayout();
                                map.setCenter(new kakao.maps.LatLng($currentLat, $currentLng));
                            }, 300);

                            // 1. Current User Location Marker
                            var currentLoc = new kakao.maps.LatLng($currentLat, $currentLng);
                            var currentMarker = new kakao.maps.Marker({
                                position: currentLoc,
                                map: map,
                                title: '내 위치'
                            });

                            var infowindow = new kakao.maps.InfoWindow({
                                content: '<div style="padding:5px;font-size:12px;text-align:center;width:150px;">📍 현재 내 위치</div>'
                            });
                            infowindow.open(map, currentMarker);

                            // 2. Recommended Restaurants Markers
                            var restaurants = $restaurantsJson;
                            var bounds = new kakao.maps.LatLngBounds();
                            bounds.extend(currentLoc);

                            restaurants.forEach(function(rest, index) {
                                var pos = new kakao.maps.LatLng(rest.latitude, rest.longitude);
                                bounds.extend(pos);

                                var marker = new kakao.maps.Marker({
                                    position: pos,
                                    map: map,
                                    title: rest.name
                                });

                                // Click event on marker: trigger Android detail dialog
                                kakao.maps.event.addListener(marker, 'click', function() {
                                    if (window.AndroidInterface && typeof window.AndroidInterface.onRestaurantMarkerClick === 'function') {
                                        window.AndroidInterface.onRestaurantMarkerClick(rest.index);
                                    }
                                });

                                var infowindowRest = new kakao.maps.InfoWindow({
                                    content: '<div style="padding:5px;font-size:12px;font-weight:bold;text-align:center;width:150px;">' + (index + 1) + '. ' + rest.name + '</div>'
                                });
                                infowindowRest.open(map, marker);
                            });

                            // Fit bounds
                            if (restaurants.length > 0) {
                                map.setBounds(bounds);
                            }
                        } catch (e) {
                            document.body.innerHTML = '<div style="padding:20px;color:red;text-align:center;"><h3>지도 로딩 실패</h3>' + e.message + '<br><br>카카오 개발자 플랫폼 Web 도메인에 &quot;http://localhost&quot;가 추가되었는지 꼭 확인하세요.</div>';
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.webViewMap.settings.javaScriptEnabled = true
        binding.webViewMap.settings.domStorageEnabled = true
        binding.webViewMap.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        binding.webViewMap.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        binding.webViewMap.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                android.util.Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }
        binding.webViewMap.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldInterceptRequest(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                android.util.Log.d("WebViewNetwork", "Requesting resource: ${request?.url}")
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                android.util.Log.e("WebViewClientError", "Error loading resource: ${request?.url}, Error: ${error?.description} (Code: ${error?.errorCode})")
            }

            override fun onReceivedHttpError(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                android.util.Log.e("WebViewClientError", "HTTP Error loading resource: ${request?.url}, Code: ${errorResponse?.statusCode}, Mime: ${errorResponse?.mimeType}")
            }

            override fun onReceivedSslError(
                view: android.webkit.WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                android.util.Log.e("WebViewClientError", "SSL Error loading resource: ${error?.url}, Primary error: ${error?.primaryError}")
                handler?.proceed() // Proceed to bypass SSL errors during debugging
            }
        }
        binding.webViewMap.addJavascriptInterface(object {
            @JavascriptInterface
            fun onRestaurantMarkerClick(index: Int) {
                runOnUiThread {
                    if (index in recommendedList.indices) {
                        showRestaurantDetailDialog(recommendedList[index])
                    }
                }
            }
        }, "AndroidInterface")

        binding.webViewMap.loadDataWithBaseURL("http://localhost", htmlContent, "text/html", "utf-8", null)
    }
}