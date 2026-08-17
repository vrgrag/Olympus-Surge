package com.olympussurge.game.sanctum.lore

/**
 * Tiny LRU cache. Used by [com.olympussurge.game.atrium.appsflyer.ChariotAttribution]
 * to dedupe conversion-data callbacks so a re-delivered attribution
 * event doesn't spam our log or re-trigger the router.
 *
 * Fixed capacity of 8 entries — attribution deliveries are rare and a
 * bigger cache would just retain stale references.
 */
internal class FableCache<K, V>(private val capacity: Int = 8) {

    private val store: LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > capacity
        }
    }

    @Synchronized
    fun put(key: K, value: V) {
        store[key] = value
    }

    @Synchronized
    fun contains(key: K): Boolean = store.containsKey(key)

    @Synchronized
    fun get(key: K): V? = store[key]

    @Synchronized
    fun clear() = store.clear()
}
