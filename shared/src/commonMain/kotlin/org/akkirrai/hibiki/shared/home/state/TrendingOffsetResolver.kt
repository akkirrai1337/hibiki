package org.akkirrai.hibiki.shared.home.state

import kotlin.random.Random

fun resolveTrendingOffset(selectionSeed: Long, maxOffsetExclusive: Int): Int =
    Random(selectionSeed).nextInt(from = 0, until = maxOffsetExclusive)
