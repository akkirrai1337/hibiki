package org.akkirrai.beakokit.api.context

/**
 * A source's own persistent key/value store, as its script sees it.
 *
 * Extensions had nowhere to keep anything between calls, which is fine for a catalog and useless
 * for an account: a login is worth nothing if the token dies with the call that fetched it. The
 * store is owned by the host - the script asks for a value by name and never learns where it
 * lives, which is what lets the host decide how it is protected without every extension having to
 * care.
 *
 * Mirrors the desktop app's own storage global (hibiki-desktop `extensionCallStorage.ts` and
 * `extensionStorage.ts`) so one extension script runs unmodified on both. The two hosts differ in
 * how they get there: the desktop runs scripts in a worker thread and so hands over a snapshot and
 * collects the writes afterwards, while Rhino runs in-process and can write straight through.
 *
 * A script must never put a password in here - only what it needs to prove itself again later.
 */
interface ExtensionStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)

    companion object {
        /** Remembers nothing, for hosts and tests with nowhere to persist to. A script sees an
         * empty store that forgets every write, which is the honest shape for that case. */
        val NONE: ExtensionStorage = object : ExtensionStorage {
            override fun get(key: String): String? = null
            override fun set(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }
    }
}

/** An in-memory store. Useful in tests, where persistence is beside the point but a script still
 * has to be able to read back what it just wrote. */
class MapExtensionStorage(initial: Map<String, String> = emptyMap()) : ExtensionStorage {
    private val values = initial.toMutableMap()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    fun snapshot(): Map<String, String> = values.toMap()
}
