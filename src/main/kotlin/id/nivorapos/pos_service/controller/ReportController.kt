package id.nivorapos.pos_service.controller

import id.nivorapos.pos_service.dto.response.ApiResponse
import id.nivorapos.pos_service.dto.response.SummaryReportResponse
import id.nivorapos.pos_service.service.ReportService
import id.nivorapos.pos_service.util.DateParam
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pos/summary-report")
class ReportController(
    private val reportService: ReportService
) {

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    fun summaryReport(
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<ApiResponse<SummaryReportResponse>> {
        return try {
            ResponseEntity.ok(reportService.summaryReport(DateParam.parseStart(startDate), DateParam.parseEnd(endDate)))
        } catch (e: Exception) {
            ResponseEntity.status(400).body(ApiResponse.error(e.message ?: "Failed to retrieve report"))
        }
    }
}
