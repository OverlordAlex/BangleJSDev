package com.itsabugnotafeature.scrolltimesync.data.entity

import org.json.JSONArray
import org.json.JSONObject

enum class InsightKind {
    SLEEP_HR_CORRELATION,
    SLEEP_CONSISTENCY_HR,
    ACTIVITY_SLEEP_QUALITY,
    LATE_ACTIVITY_IMPACT,
    SEDENTARY_STREAK,
    RESTING_HR_TREND,
    SLEEP_QUALITY_TREND,
    MULTI_METRIC_WELLNESS,
}

data class WeeklyInsight(
    val kind: InsightKind,
    val magnitude: Float,
    val direction: Int,
    val displayText: String,
    val confidence: Float?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("magnitude", magnitude.toDouble())
        put("direction", direction)
        put("displayText", displayText)
        if (confidence != null) put("confidence", confidence.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): WeeklyInsight = WeeklyInsight(
            kind = InsightKind.valueOf(json.getString("kind")),
            magnitude = json.getDouble("magnitude").toFloat(),
            direction = json.getInt("direction"),
            displayText = json.getString("displayText"),
            confidence = if (json.has("confidence")) json.getDouble("confidence").toFloat() else null,
        )

        fun listToJson(insights: List<WeeklyInsight>): String {
            val array = JSONArray()
            insights.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String?): List<WeeklyInsight> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
