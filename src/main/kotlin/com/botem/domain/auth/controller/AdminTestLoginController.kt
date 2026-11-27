package com.botem.domain.auth.controller

import com.botem.domain.auth.controller.docs.AdminTestLoginControllerDocs
import com.botem.domain.auth.dto.request.AdminTestLoginRequest
import com.botem.domain.auth.dto.response.AccessTokenResponse
import com.botem.domain.auth.service.AdminTestLoginService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/test-login")
class AdminTestLoginController(
    private val adminTestLoginService: AdminTestLoginService,
) : AdminTestLoginControllerDocs {

    @PostMapping
    override fun login(
        @Valid @RequestBody request: AdminTestLoginRequest,
    ): ApiResponse<AccessTokenResponse> =
        ApiResponse.success(adminTestLoginService.issueAccessToken(request.email))
}
