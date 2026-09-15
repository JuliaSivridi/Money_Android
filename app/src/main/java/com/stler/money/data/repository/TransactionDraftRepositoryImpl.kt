package com.stler.money.data.repository

import com.stler.money.data.local.dao.TransactionDraftDao
import com.stler.money.data.local.entity.TransactionDraftEntity
import com.stler.money.data.local.entity.toDomain
import com.stler.money.domain.model.TransactionDraft
import com.stler.money.util.generateId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class TransactionDraftRepositoryImpl @Inject constructor(
    private val dao: TransactionDraftDao,
    private val accountRepository: AccountRepository,
) : TransactionDraftRepository {

    override fun observeAll(): Flow<List<TransactionDraft>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun recordNotification(parsed: ParsedDraft): Boolean {
        // Exactly one recognizing Account resolves it; zero or more than one is ambiguous
        // (left blank — the user picks manually in the form, same as an unmapped source).
        val resolvedAccountId = accountRepository.accountIdsForNotificationPackage(parsed.sourcePackage)
            .singleOrNull().orEmpty()

        val sameSourceSince = Instant.now().minus(SAME_SOURCE_WINDOW).toString()
        val sameSourceExisting = dao.findRecentDuplicate(
            sourcePackage = parsed.sourcePackage,
            notificationKey = parsed.notificationKey,
            textHash = parsed.textHash,
            sinceIso = sameSourceSince,
        )
        if (sameSourceExisting != null) return false

        // A different source (e.g. a wallet app and the bank's own app) can report the
        // same real-world transaction within seconds of each other — matched by
        // amount+currency alone, since understanding each source's own phrasing well
        // enough to correlate them any more precisely isn't possible without real samples.
        val crossSourceSince = Instant.now().minus(CROSS_SOURCE_WINDOW).toString()
        val crossSourceExisting = dao.findRecentSameAmount(parsed.amount, parsed.currency, crossSourceSince)
        if (crossSourceExisting != null) {
            if (crossSourceExisting.accountId.isBlank() && resolvedAccountId.isNotBlank()) {
                dao.updateAccountId(crossSourceExisting.id, resolvedAccountId)
            }
            return false
        }

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
                accountId = resolvedAccountId,
            ),
        )
        return true
    }

    private companion object {
        val SAME_SOURCE_WINDOW: Duration = Duration.ofMinutes(5)

        // Tighter than SAME_SOURCE_WINDOW — a fuzzier match (amount+currency only, no
        // notification identity), so kept to how close together two apps would plausibly
        // both notify about one real tap-to-pay, not how long a draft stays "recent".
        val CROSS_SOURCE_WINDOW: Duration = Duration.ofMinutes(2)
    }
}
