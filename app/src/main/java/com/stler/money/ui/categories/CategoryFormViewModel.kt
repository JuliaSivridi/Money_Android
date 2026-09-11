package com.stler.money.ui.categories

import androidx.lifecycle.viewModelScope
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.domain.model.Category
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs [CategoryFormSheet] — real `CategoryModal.tsx`, including delete-with-transfer. */
@HiltViewModel
class CategoryFormViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : BaseViewModel() {

    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(category: Category, isEdit: Boolean, onDone: () -> Unit) = safeLaunch {
        if (isEdit) categoryRepository.updateCategory(category) else categoryRepository.createCategory(category)
        onDone()
    }

    /** See tech spec §16.7 — every referencing transaction gets [transferToId] instead. */
    fun deleteWithTransfer(id: String, transferToId: String, onDone: () -> Unit) = safeLaunch {
        categoryRepository.deleteCategoryWithTransfer(id, transferToId)
        onDone()
    }
}
