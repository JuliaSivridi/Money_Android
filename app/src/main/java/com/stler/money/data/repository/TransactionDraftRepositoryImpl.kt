package com.stler.money.data.repository

import com.stler.money.data.local.dao.TransactionDraftDao
import com.stler.money.data.local.entity.TransactionDraftEntity
import com.stler.money.data.local.entity.toDomain
import com.stler.money.domain.model.TransactionDraft
import com.stler.money.util.generateId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class TransactionDraftRepositoryImpl @Inject constructor(
    private val dao: TransactionDraftDao,
) : TransactionDraftRepository {

    override fun observeAll(): Flow<List<TransactionDraft>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun insertIfNotDuplicate(parsed: ParsedDraft): Boolean {
        val sinceIso = Instant.now().minus(DEDUP_WINDOW).toString()
        val existing = dao.findRecentDuplicate(
            sourcePackage = parsed.sourcePackage,
            notificationKey = parsed.notificationKey,
            textHash = parsed.textHash,
            sinceIso = sinceIso,
        )
        if (existing != null) return false

        dao.insert(
            TransactionDraftEntity(
                id = generateId("draft"),
                sourcePackage = parsed.sourcePackage,
                sourceLabel = parsed.sourceLabel,
                notificationKey = parsed.notificationKey,
                textHash = parsed.textHash,
                amount = parsed.amount,
                currency = parsed.currency,
                date = parsed.date,
                createdAt = Instant.now().toString(),
            ),
        )
        return true
    }

    private companion object {
        val DEDUP_WINDOW: java.time.Duration = java.time.Duration.ofMinutes(5)
    }
}
