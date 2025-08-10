// File: app/src/main/java/com/insail/nmeagpsserver/LogStore.kt
package com.insail.nmeagpsserver

import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe in-memory ring buffer for system logs.
 * Keeps the last [capacity] lines for later display in SystemLogsActivity.
 */
object LogStore {

    private const val DEFAULT_CAPACITY = 500

    private val lock = ReentrantLock()
    private var capacity: Int = DEFAULT_CAPACITY
    private val buffer: Deque<String> = ArrayDeque(capacity)

    /**
     * Append a single log line. Trims to [capacity].
     */
    fun append(line: String) {
        val sanitized = line.trimEnd()
        if (sanitized.isEmpty()) return
        lock.withLock {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(sanitized)
        }
    }

    /**
     * Append multiple lines efficiently.
     */
    fun appendAll(lines: Iterable<String>) {
        lock.withLock {
            for (raw in lines) {
                val line = raw.trimEnd()
                if (line.isEmpty()) continue
                if (buffer.size >= capacity) buffer.removeFirst()
                buffer.addLast(line)
            }
        }
    }

    /**
     * Returns a snapshot copy of current lines (oldest → newest).
     */
    fun snapshot(): List<String> = lock.withLock { buffer.toList() }

    /**
     * Clears the buffer.
     */
    fun clear() = lock.withLock { buffer.clear() }

    /**
     * Optionally resize the ring buffer capacity at runtime.
     * If newCapacity < current size, oldest lines are dropped.
     */
    fun setCapacity(newCapacity: Int) {
        require(newCapacity > 0) { "Capacity must be > 0" }
        lock.withLock {
            if (newCapacity == capacity) return
            val current = ArrayList(buffer)
            capacity = newCapacity
            buffer.clear()
            val start = (current.size - capacity).coerceAtLeast(0)
            for (i in start until current.size) {
                buffer.addLast(current[i])
            }
        }
    }

    /**
     * Current capacity of the ring buffer.
     */
    fun getCapacity(): Int = lock.withLock { capacity }

    /**
     * Current number of stored lines.
     */
    fun size(): Int = lock.withLock { buffer.size }
}
