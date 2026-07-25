package org.akkirrai.hibiki.shared.collection

/**
 * Groups items by a predefined, ordered set of keys.
 * Missing keys are retained with an empty list so callers can render stable
 * sections even when a category has no items.
 */
fun <K, T> groupItemsByKeys(
    items: Iterable<T>,
    keys: Iterable<K>,
    keyOf: (T) -> K,
): Map<K, List<T>> {
    val grouped = items.groupBy(keyOf)
    return keys.associateWith { key -> grouped[key].orEmpty() }
}
