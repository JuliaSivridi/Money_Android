package com.stler.money.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.domain.model.Category
import com.stler.money.ui.icons.CategoryIconView
import com.stler.money.ui.theme.ControlShape
import com.stler.money.ui.theme.IconSizes
import com.stler.money.ui.util.ErrorSnackbarEffect
import com.stler.money.ui.util.ShimmerListPlaceholder
import com.stler.money.util.formatAmount
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class CategoryTab { EXPENSES, INCOME }

/**
 * Expenses/Income tab toggle with per-tab limit totals — real structure ported
 * from `CategoriesPage.tsx`. Row tap filters Transactions by category
 * (Android-only deviation from the PWA, where the row tap opens edit — see
 * the matching deviation on Accounts); the trailing `⋮` menu is the
 * Edit/Delete entry point instead. Drag-to-reorder uses `sh.calvin.reorderable`,
 * the same library + `ReorderableItem`/`draggableHandle()` idiom and the same
 * "mutate a local optimistic copy per frame, persist once on drop" performance
 * pattern as the Tasks Android sibling's `FolderScreen`.
 *
 * `sortOrder` is global across both tabs (real `CategoriesPage.tsx`'s
 * `handleDragEnd` reorders the *full* `categories` array even though only one
 * tab's `visibleCategories` is rendered/draggable) — so a drop within one tab
 * is translated into a single move within the full category list before
 * calling `CategoryRepository.reorder()`, not a per-tab reorder.
 */
@Composable
fun CategoriesScreen(
    onCategoryClick: (Category) -> Unit,
    onEditCategory: (Category) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
    formViewModel: CategoryFormViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(CategoryTab.EXPENSES) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    val visible = categories.filter { if (tab == CategoryTab.EXPENSES) it.isExpense else it.isIncome }
        .sortedBy { it.sortOrder }
    val tabTotal = { t: CategoryTab ->
        categories.filter { if (t == CategoryTab.EXPENSES) it.isExpense else it.isIncome }
            .sumOf { if (t == CategoryTab.EXPENSES) it.expenseLimit else it.incomeLimit }
    }

    // Optimistic drag copy of the visible tab — mirrors Tasks Android's FolderScreen:
    // onMove only mutates this (pure UI, no DB write per frame); the actual
    // `reorder()` call happens once, on drop.
    var localList by remember(visible) { mutableStateOf(visible) }
    val pendingReorder = remember { object { var draggedId: String = ""; var toIdx: Int = -1 } }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val mutable = localList.toMutableList()
        val moved = mutable.removeAt(from.index)
        mutable.add(to.index, moved)
        localList = mutable
        pendingReorder.draggedId = moved.id
        pendingReorder.toIdx = to.index
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            CategoryTab.entries.forEachIndexed { index, t ->
                val total = tabTotal(t)
                SegmentedButton(
                    selected = tab == t,
                    onClick = { tab = t },
                    shape = SegmentedButtonDefaults.itemShape(index, CategoryTab.entries.size, baseShape = ControlShape),
                    icon = {},
                ) {
                    Text(
                        buildString {
                            append(if (t == CategoryTab.EXPENSES) "Expenses" else "Income")
                            if (total > 0) append("  ${formatAmount(total, baseCurrency)}/mo")
                        },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (visible.isEmpty() && isLoading) {
            ShimmerListPlaceholder(modifier = Modifier.fillMaxSize())
        } else if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Text(
                        "No ${if (tab == CategoryTab.EXPENSES) "expense" else "income"} categories yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                items(localList, key = { it.id }) { category ->
                    ReorderableItem(reorderableState, key = category.id) { isDragging ->
                        // Persist on drop (isDragging true → false) — only the dragged item's
                        // effect fires here; the rest stay false throughout the gesture.
                        LaunchedEffect(isDragging) {
                            if (!isDragging && pendingReorder.draggedId.isNotEmpty() && pendingReorder.toIdx >= 0) {
                                val draggedId = pendingReorder.draggedId
                                val targetId = visible.getOrNull(pendingReorder.toIdx)?.id
                                if (targetId != null && targetId != draggedId) {
                                    val fullOrder = categories.sortedBy { it.sortOrder }.map { it.id }.toMutableList()
                                    val oldIdx = fullOrder.indexOf(draggedId)
                                    val newIdx = fullOrder.indexOf(targetId)
                                    if (oldIdx >= 0 && newIdx >= 0 && oldIdx != newIdx) {
                                        fullOrder.removeAt(oldIdx)
                                        fullOrder.add(newIdx, draggedId)
                                        viewModel.reorder(fullOrder)
                                    }
                                }
                                pendingReorder.draggedId = ""
                                pendingReorder.toIdx = -1
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier = Modifier.size(20.dp).padding(start = 4.dp).draggableHandle(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CategoryRow(
                                category, tab, baseCurrency,
                                onClick = { onCategoryClick(category) },
                                onEdit = { onEditCategory(category) },
                                onDeleteRequest = { deletingCategory = category },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    deletingCategory?.let { category ->
        DeleteCategoryDialog(
            category = category,
            otherCategories = categories.filter { it.id != category.id },
            onConfirm = { transferToId ->
                val target = category
                deletingCategory = null
                formViewModel.deleteWithTransfer(target.id, transferToId) {}
            },
            onCancel = { deletingCategory = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    category: Category,
    tab: CategoryTab,
    baseCurrency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(category.color)) }.getOrDefault(Color.Gray)
    // Each tab shows its own limit: expense_limit on Expenses, income_limit on Income — matches PWA.
    val limit = if (tab == CategoryTab.EXPENSES) category.expenseLimit else category.incomeLimit
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconView(iconName = category.icon, color = color, size = IconSizes.lg)
        Text(category.name, modifier = Modifier.padding(start = 12.dp).weight(1f))
        if (limit > 0) {
            Text(
                "${formatAmount(limit, baseCurrency)}/mo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Category options")
        }
    }

    if (menuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text("Edit") },
                    leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { menuOpen = false; onEdit() },
                )
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { menuOpen = false; onDeleteRequest() },
                )
            }
        }
    }
}
