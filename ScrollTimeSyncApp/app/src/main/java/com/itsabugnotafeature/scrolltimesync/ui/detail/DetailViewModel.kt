package com.itsabugnotafeature.scrolltimesync.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import com.itsabugnotafeature.scrolltimesync.data.entity.DailySummaryEntity
import com.itsabugnotafeature.scrolltimesync.data.entity.HealthRecordEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class TimePreset { DAY, WEEK, MONTH, CUSTOM }

data class DetailState(
    val preset: TimePreset = TimePreset.WEEK,
    val startDate: LocalDate = LocalDate.now().minusDays(7),
    val endDate: LocalDate = LocalDate.now(),
    val records: List<HealthRecordEntity> = emptyList(),
    val summaries: List<DailySummaryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val useRecords: Boolean = false,
    val dayLabel: String = "",
    val canGoForward: Boolean = false,
)

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository.getInstance(application)

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private var collectionJob: Job? = null
    private var currentDataType: String = ""

    fun loadPreset(dataType: String, preset: TimePreset) {
        currentDataType = dataType
        val now = LocalDate.now()
        val (start, end) = when (preset) {
            TimePreset.DAY -> now.minusDays(1) to now.minusDays(1)
            TimePreset.WEEK -> now.minusDays(7) to now
            TimePreset.MONTH -> now.minusDays(30) to now
            TimePreset.CUSTOM -> return
        }
        loadRange(dataType, start, end, preset)
    }

    fun navigateDay(offset: Int) {
        val current = _state.value
        if (current.preset != TimePreset.DAY) return
        val newDate = current.startDate.plusDays(offset.toLong())
        if (newDate.isAfter(LocalDate.now())) return
        loadRange(currentDataType, newDate, newDate, TimePreset.DAY)
    }

    fun loadRange(
        dataType: String,
        startDate: LocalDate,
        endDate: LocalDate,
        preset: TimePreset = TimePreset.CUSTOM,
    ) {
        currentDataType = dataType
        collectionJob?.cancel()
        val daySpan = endDate.toEpochDay() - startDate.toEpochDay()
        val useRecords = daySpan <= 2

        val dayLabel = if (preset == TimePreset.DAY) {
            val dayOfWeek = startDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val formatter = DateTimeFormatter.ofPattern("MMMM d")
            "$dayOfWeek, ${startDate.format(formatter)}"
        } else ""

        val canGoForward = preset == TimePreset.DAY && startDate.isBefore(LocalDate.now())

        _state.value = DetailState(
            preset = preset,
            startDate = startDate,
            endDate = endDate,
            isLoading = true,
            useRecords = useRecords,
            dayLabel = dayLabel,
            canGoForward = canGoForward,
        )

        val zone = ZoneId.systemDefault()

        collectionJob = viewModelScope.launch {
            if (useRecords) {
                val startEpoch = startDate.atStartOfDay(zone).toEpochSecond()
                val endEpoch = endDate.plusDays(1).atStartOfDay(zone).toEpochSecond()
                repository.getRecordsBetween(startEpoch, endEpoch).collect { records ->
                    _state.value = _state.value.copy(
                        records = records,
                        isLoading = false,
                    )
                }
            } else {
                val startStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val endStr = endDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                repository.getSummariesBetween(startStr, endStr).collect { summaries ->
                    _state.value = _state.value.copy(
                        summaries = summaries,
                        isLoading = false,
                    )
                }
            }
        }
    }
}
