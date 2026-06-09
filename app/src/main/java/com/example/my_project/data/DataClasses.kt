package com.example.my_project.data

import com.google.gson.annotations.SerializedName

// --- Kakao Local API Data Models ---
data class KakaoSearchResponse(
    @SerializedName("documents") val documents: List<KakaoDocument>,
    @SerializedName("meta") val meta: KakaoMeta
)

data class KakaoDocument(
    @SerializedName("id") val id: String,
    @SerializedName("place_name") val place_name: String,
    @SerializedName("category_name") val category_name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("address_name") val address_name: String,
    @SerializedName("road_address_name") val road_address_name: String,
    @SerializedName("x") val x: String,
    @SerializedName("y") val y: String,
    @SerializedName("place_url") val place_url: String,
    @SerializedName("distance") val distance: String
)

data class KakaoMeta(
    @SerializedName("is_end") val isEnd: Boolean,
    @SerializedName("total_count") val totalCount: Int
)

// --- Gemini API Data Models ---
data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text") val text: String
)

data class GeminiGenerationConfig(
    @SerializedName("responseMimeType") val responseMimeType: String
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent
)

// --- Local UI Models ---
data class RecommendationResultItem(
    @SerializedName("name") val name: String,
    @SerializedName("reason") val reason: String
)

data class RecommendedRestaurant(
    val name: String,
    val category: String,
    val distance: String,
    val address: String,
    val phone: String,
    val reason: String,
    val url: String
)
