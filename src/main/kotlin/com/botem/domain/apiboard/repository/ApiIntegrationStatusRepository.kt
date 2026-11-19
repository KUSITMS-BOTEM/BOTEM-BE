package com.botem.domain.apiboard.repository

import com.botem.domain.apiboard.entity.ApiIntegrationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ApiIntegrationStatusRepository : JpaRepository<ApiIntegrationStatus, Long> {

    fun findByHttpMethodAndPath(httpMethod: String, path: String): ApiIntegrationStatus?
}
