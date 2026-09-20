package com.sowerrrt.dayloom

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) rootViewModel.lock()
    }
}
