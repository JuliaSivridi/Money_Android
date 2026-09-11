package com.stler.money.ui.categories

import androidx.lifecycle.viewModelScope
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.ExchangeRateRepository
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs [CategoriesScreen] — tech spec §8.3. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    exchangeRateRepository: ExchangeRateRepository,
) : BaseViewModel() {

    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** For formatting per-category and per-tab limit totals — matches PWA's CategoriesPage. */
    val baseCurrency = exchangeRateRepository.baseCurrency

    /**
     * True only until the first real Room emission arrives — drives the shimmer-vs-real-
     * empty-state choice. `SyncState.Syncing` was tried first and was the wrong signal: on
     * a warm install Room already has cached data, so nothing is ever "empty AND syncing"
     * even though there's still a real (if brief) gap, every cold start, before this
     * `StateFlow` moves past its own initial value.
     */
    val isLoading = categoryRepository.observeAll()
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** [orderedIds] is the complete category list (both tabs) in its new global sortOrder — §8.3. */
    fun reorder(orderedIds: List<String>) = safeLaunch {
        categoryRepository.reorder(orderedIds)
    }
}
