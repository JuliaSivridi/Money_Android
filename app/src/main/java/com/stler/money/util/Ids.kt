package com.stler.money.util

import java.util.UUID

/** See tech spec §16.4 — e.g. generateId("txn") -> "txn_a1b2c3d4". */
fun generateId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
