package com.stler.money.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Matches the real PWA's `dateUtils.ts#currentTimeHHMM` — current wall-clock time as "HH:mm",
 * used to stamp a newly created transaction (never a picker; the PWA has none either). */
fun currentTimeHHMM(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
