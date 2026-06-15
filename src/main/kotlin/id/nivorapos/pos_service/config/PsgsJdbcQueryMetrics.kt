package id.nivorapos.pos_service.config

object PsgsJdbcQueryMetrics {

    data class Snapshot(
        val statementCount: Long,
        val totalTimeMs: Long,
        val slowestTimeMs: Long,
        val slowestSql: String?
    ) {
        val hasQueries: Boolean
            get() = statementCount > 0

        companion object {
            val EMPTY = Snapshot(0, 0, 0, null)
        }
    }

    private class MutableMetrics {
        var statementCount: Long = 0
        var totalTimeMs: Long = 0
        var slowestTimeMs: Long = 0
        var slowestSql: String? = null

        fun record(sql: String?, elapsedMs: Long) {
            statementCount += 1
            totalTimeMs += elapsedMs
            if (elapsedMs >= slowestTimeMs) {
                slowestTimeMs = elapsedMs
                slowestSql = sql
            }
        }

        fun snapshot(): Snapshot = Snapshot(
            statementCount = statementCount,
            totalTimeMs = totalTimeMs,
            slowestTimeMs = slowestTimeMs,
            slowestSql = slowestSql
        )
    }

    private val current = ThreadLocal<MutableMetrics>()

    fun beginRequest() {
        current.set(MutableMetrics())
    }

    fun record(sql: String?, elapsedMs: Long) {
        current.get()?.record(sql, elapsedMs)
    }

    fun snapshot(): Snapshot = current.get()?.snapshot() ?: Snapshot.EMPTY

    fun clear() {
        current.remove()
    }
}
