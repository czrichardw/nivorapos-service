package id.nivorapos.pos_service.config

import id.nivorapos.pos_service.dto.response.ApiResponse
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn(
            "request rejected",
            keyValue("event_action", "exception_handled"),
            keyValue("status_code", HttpStatus.BAD_REQUEST.value()),
            keyValue("exception_class", e.javaClass.name)
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.message ?: "An error occurred"))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthException(e: AuthenticationException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn(
            "authentication exception handled",
            keyValue("event_action", "exception_handled"),
            keyValue("status_code", HttpStatus.UNAUTHORIZED.value()),
            keyValue("exception_class", e.javaClass.name)
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(e.message ?: "Authentication failed"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error(
            "unhandled exception",
            keyValue("event_action", "exception_handled"),
            keyValue("status_code", HttpStatus.INTERNAL_SERVER_ERROR.value()),
            keyValue("exception_class", e.javaClass.name),
            e
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(e.message ?: "Internal server error"))
    }
}
