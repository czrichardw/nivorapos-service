package id.nivorapos.pos_service.service

import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.system.measureTimeMillis

@Component
class PsgsTokenAuthCacheCleanupJob(
    private val cacheService: PsgsTokenAuthCacheService,
    @Value("\${app.psgs-token-cache.cleanup-enabled:true}")
    private val cleanupEnabled: Boolean
) {

    private val log = LoggerFactory.getLogger(PsgsTokenAuthCacheCleanupJob::class.java)

    @Scheduled(fixedDelayString = "\${app.psgs-token-cache.cleanup-fixed-delay-ms:60000}")
    fun pruneExpiredCache() {
        if (!cleanupEnabled || !cacheService.isEnabled()) return

        var deleted = 0
        val durationMs = measureTimeMillis {
            deleted = cacheService.pruneExpired()
        }
        if (deleted > 0) {
            log.info(
                "psgs auth cache pruned",
                keyValue("event_action", "auth_cache_pruned"),
                keyValue("deleted_count", deleted)
            )
            log.debug(
                "psgs auth cache prune timing",
                keyValue("event_action", "auth_cache_prune_timing"),
                keyValue("deleted_count", deleted),
                keyValue("duration_ms", durationMs)
            )
        }
    }
}
