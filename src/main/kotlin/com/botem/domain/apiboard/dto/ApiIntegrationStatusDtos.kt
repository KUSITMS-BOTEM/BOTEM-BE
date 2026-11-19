package com.botem.domain.apiboard.dto

import com.botem.domain.apiboard.entity.ApiIntegrationStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateApiIntegrationStatusRequest(
    @field:Pattern(regexp = "GET|POST|PUT|PATCH|DELETE")
    val method: String,

    @field:NotBlank
    @field:Size(max = 255)
    val path: String,

    @field:Size(max = ApiIntegrationStatus.MAX_OWNER_LENGTH)
    val owner: String? = null,

    val integrated: Boolean = false,

    @field:Size(max = ApiIntegrationStatus.MAX_MEMO_LENGTH)
    val memo: String? = null,
)

data class ApiIntegrationStatusResponse(
    val method: String,
    val path: String,
    val owner: String?,
    val integrated: Boolean,
    val memo: String?,
) {
    companion object {
        fun from(status: ApiIntegrationStatus): ApiIntegrationStatusResponse =
            ApiIntegrationStatusResponse(
                method = status.httpMethod,
                path = status.path,
                owner = status.owner,
                integrated = status.integrated,
                memo = status.memo,
            )
    }
}
