package com.stler.money.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Matches the real PWA's `dateUtils.ts#currentTimeHHMM` — current wall-clock time as "HH:mm",
 * used to stamp a newly created transaction (never a picker; the PWA has none either). */
fun currentTimeHHMM(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

/** Formats an ISO instant string (e.g. TransactionDraftEntity.createdAt) as local "HH:mm" —
 * display-only, informational (see DraftRow); never fed back into a saved Transaction's own
 * `time`, which always uses [currentTimeHHMM] at the moment of the real Save. */
fun formatInstantHHMM(iso: String): String =
    runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
