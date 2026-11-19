package com.botem.domain.apiboard.service

import com.botem.domain.apiboard.dto.ApiIntegrationStatusResponse
import com.botem.domain.apiboard.dto.UpdateApiIntegrationStatusRequest
import com.botem.domain.apiboard.entity.ApiIntegrationStatus
import com.botem.domain.apiboard.repository.ApiIntegrationStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApiIntegrationStatusService(
    private val apiIntegrationStatusRepository: ApiIntegrationStatusRepository,
) {

    @Transactional(readOnly = true)
    fun getStatuses(): List<ApiIntegrationStatusResponse> =
        apiIntegrationStatusRepository.findAll().map(ApiIntegrationStatusResponse::from)

    // 스펙에 새로 생긴 API는 저장된 상태가 없으므로 처음 수정할 때 만든다
    @Transactional
    fun updateStatus(request: UpdateApiIntegrationStatusRequest): ApiIntegrationStatusResponse {
        val owner = request.owner?.trim()?.takeIf(String::isNotEmpty)
        val memo = request.memo?.trim()?.takeIf(String::isNotEmpty)
        val status = apiIntegrationStatusRepository.findByHttpMethodAndPath(request.method, request.path)
            ?.also { it.update(owner, request.integrated, memo) }
            ?: apiIntegrationStatusRepository.save(
                ApiIntegrationStatus(
                    httpMethod = request.method,
                    path = request.path,
                    owner = owner,
                    integrated = request.integrated,
                    memo = memo,
                ),
            )

        return ApiIntegrationStatusResponse.from(status)
    }
}
