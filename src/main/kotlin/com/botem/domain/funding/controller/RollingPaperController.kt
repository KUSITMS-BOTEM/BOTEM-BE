package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.RollingPaperControllerDocs
import com.botem.domain.funding.dto.response.RollingPaperResponse
import com.botem.domain.funding.service.RollingPaperService
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings")
class RollingPaperController(
    private val rollingPaperService: RollingPaperService,
) : RollingPaperControllerDocs {

    @GetMapping("/{fundingId}/rolling-paper")
    override fun getRollingPaper(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<RollingPaperResponse> =
        ApiResponse.success(rollingPaperService.getRollingPaper(userId, fundingId))
}
