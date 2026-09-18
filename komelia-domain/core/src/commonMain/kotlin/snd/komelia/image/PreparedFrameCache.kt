package snd.komelia.image

/** Accessed under the owning image's mutex. Each entry exclusively owns its speculative pixels. */
internal class PreparedFrameCache<K, V>(private val dispose: (V) -> Unit) {
    private data class Entry<V>(val frame: V, val reservation: ReaderPrefetchBudget.Reservation)
    private val entries = LinkedHashMap<K, Entry<V>>()

    fun contains(key: K): Boolean = key in entries

    fun take(key: K): V? = entries.remove(key)?.let {
        it.reservation.release()
        it.frame
    }

    fun put(key: K, frame: V, reservation: ReaderPrefetchBudget.Reservation) {
        remove(key)
        while (entries.size >= MAX_FRAMES) remove(entries.keys.first())
        entries[key] = Entry(frame, reservation)
    }

    fun retain(keys: Set<K>) = entries.keys.filter { it !in keys }.forEach(::remove)
    fun clear() = entries.keys.toList().forEach(::remove)

    private fun remove(key: K) {
        val entry = entries.remove(key) ?: return
        try { dispose(entry.frame) } finally { entry.reservation.release() }
    }

    private companion object { const val MAX_FRAMES = 2 }
}
