package com.stler.money.sync

/**
 * Current synchronization status, used by the TopAppBar icon — tech spec §7.
 *
 * Idle    -> cloud-check icon
 * Pending -> cloud + badge with pending count
 * Syncing -> spinning icon
 */
sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Pending(val count: Int) : SyncState()
}
