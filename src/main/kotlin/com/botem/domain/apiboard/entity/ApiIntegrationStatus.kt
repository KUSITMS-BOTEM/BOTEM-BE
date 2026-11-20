package com.botem.domain.apiboard.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// API 목록은 OpenAPI 스펙에서 읽고, 코드로 알 수 없는 백엔드·프론트 담당자와 연동 여부만 여기 저장한다
@Entity
@Table(
    name = "api_integration_statuses",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_api_integration_statuses_endpoint", columnNames = ["http_method", "path"]),
    ],
)
class ApiIntegrationStatus(
    @Column(name = "http_method", nullable = false, updatable = false, length = 10)
    val httpMethod: String,

    @Column(nullable = false, updatable = false, length = 255)
    val path: String,

    owner: String? = null,

    integrated: Boolean = false,

    frontendOwner: String? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(length = MAX_OWNER_LENGTH)
    var owner: String? = owner
        protected set

    @Column(nullable = false)
    var integrated: Boolean = integrated
        protected set

    @Column(length = MAX_OWNER_LENGTH)
    var frontendOwner: String? = frontendOwner
        protected set

    fun update(owner: String?, integrated: Boolean, frontendOwner: String?) {
        this.owner = owner
        this.integrated = integrated
        this.frontendOwner = frontendOwner
    }

    companion object {
        const val MAX_OWNER_LENGTH = 20
    }
}
