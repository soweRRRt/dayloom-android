package com.sowerrrt.dayloom

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var habitsRepository: HabitsRepository

    @Inject
    lateinit var plannerRepository: PlannerRepository

    private val rootViewModel: RootViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private lateinit var jankStats: JankStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            supervisorScope {
                launch { runCatching { habitsRepository.loadAllHabits() } }
                launch { runCatching { plannerRepository.loadAllPlans() } }
            }
        }
        enableEdgeToEdge()
        setContent { DayloomApp(rootViewModel, updateViewModel) }
        jankStats =
            JankStats.createAndTrack(window) { frameData ->
                if (BuildConfig.DEBUG && frameData.isJank) {
                    Log.w(
                        JANK_LOG_TAG,
                        "Slow frame ${frameData.frameDurationUiNanos / 1_000_000f} ms; " +
                            frameData.states.joinToString { "${it.key}=${it.value}" },
                    )
                }
            }
    }

    override fun onResume() {
        super.onResume()
        if (::jankStats.isInitialized) jankStats.isTrackingEnabled = true
    }

    override fun onPause() {
        if (::jankStats.isInitialized) jankStats.isTrackingEnabled = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) rootViewModel.lock()
    }

    private companion object {
        const val JANK_LOG_TAG = "DayloomJank"
    }
}
