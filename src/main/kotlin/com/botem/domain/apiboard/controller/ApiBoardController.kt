package com.botem.domain.apiboard.controller

import com.botem.domain.apiboard.dto.ApiIntegrationStatusResponse
import com.botem.domain.apiboard.dto.UpdateApiIntegrationStatusRequest
import com.botem.domain.apiboard.service.ApiIntegrationStatusService
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody

// 개발용 API 연동 현황 보드. 서비스 API가 아니므로 스펙에서 숨기고 Swagger와 같은 Basic 인증으로 막는다
@Hidden
@Controller
class ApiBoardController(
    private val apiIntegrationStatusService: ApiIntegrationStatusService,
) {

    @GetMapping("/api-board")
    fun page(): String = "forward:/api-board/index.html"

    @ResponseBody
    @GetMapping("/api-board/statuses")
    fun getStatuses(): ApiResponse<List<ApiIntegrationStatusResponse>> =
        ApiResponse.success(apiIntegrationStatusService.getStatuses())

    @ResponseBody
    @PutMapping("/api-board/statuses")
    fun updateStatus(
        @Valid @RequestBody request: UpdateApiIntegrationStatusRequest,
    ): ApiResponse<ApiIntegrationStatusResponse> =
        ApiResponse.success(apiIntegrationStatusService.updateStatus(request))
}
