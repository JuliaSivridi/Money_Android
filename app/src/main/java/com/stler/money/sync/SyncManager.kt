package com.stler.money.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.stler.money.data.local.dao.SyncQueueDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Live state for the TopAppBar sync icon — tech spec §7. */
    val syncState: Flow<SyncState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME),
        syncQueueDao.observePendingCount(),
    ) { periodicInfos, manualInfos, pendingCount ->
        val isRunning = (periodicInfos + manualInfos).any { it.state == WorkInfo.State.RUNNING }
        when {
            isRunning -> SyncState.Syncing
            pendingCount > 0 -> SyncState.Pending(pendingCount)
            else -> SyncState.Idle
        }
    }

    /** Schedules the 30-minute periodic sync if not already scheduled. Called from MoneyApplication.onCreate(). */
    fun initialize() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .build(),
        )
    }

    /** Triggers an immediate one-off sync (manual button or after sign-in). */
    fun triggerSync() {
        workManager.enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .build(),
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "StlerMoneyPeriodicSync"
        private const val MANUAL_WORK_NAME = "StlerMoneyManualSync"
    }
}
