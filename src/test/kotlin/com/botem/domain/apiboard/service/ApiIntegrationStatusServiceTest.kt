package com.botem.domain.apiboard.service

import com.botem.domain.apiboard.dto.UpdateApiIntegrationStatusRequest
import com.botem.domain.apiboard.entity.ApiIntegrationStatus
import com.botem.domain.apiboard.repository.ApiIntegrationStatusRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiIntegrationStatusServiceTest {

    private val repository = mock(ApiIntegrationStatusRepository::class.java)
    private val service = ApiIntegrationStatusService(repository)

    @Test
    fun `저장된 연동 상태를 반환한다`() {
        given(repository.findAll()).willReturn(listOf(ApiIntegrationStatus("GET", "/api/wishes", "정푸름", true)))

        val statuses = service.getStatuses()

        assertEquals(1, statuses.size)
        assertEquals("GET", statuses[0].method)
        assertEquals("정푸름", statuses[0].owner)
        assertTrue(statuses[0].integrated)
    }

    @Test
    fun `저장된 상태가 있으면 담당자와 연동 여부를 바꾼다`() {
        val status = ApiIntegrationStatus("GET", "/api/wishes", "정푸름")
        given(repository.findByHttpMethodAndPath("GET", "/api/wishes")).willReturn(status)

        val result = service.updateStatus(
            UpdateApiIntegrationStatusRequest("GET", "/api/wishes", owner = " 이준교 ", integrated = true, memo = "  "),
        )

        assertEquals("이준교", result.owner)
        assertTrue(result.integrated)
        assertNull(result.memo)
        verify(repository, never()).save(any(ApiIntegrationStatus::class.java))
    }

    @Test
    fun `스펙에 새로 생긴 API는 처음 수정할 때 상태를 만든다`() {
        given(repository.findByHttpMethodAndPath("POST", "/api/new")).willReturn(null)
        given(repository.save(any(ApiIntegrationStatus::class.java))).willAnswer { it.arguments[0] }

        val result = service.updateStatus(UpdateApiIntegrationStatusRequest("POST", "/api/new", memo = "프론트 확인 필요"))

        val saved = ArgumentCaptor.forClass(ApiIntegrationStatus::class.java)
        verify(repository).save(saved.capture())
        assertEquals("POST", saved.value.httpMethod)
        assertEquals("/api/new", saved.value.path)
        assertNull(result.owner)
        assertFalse(result.integrated)
        assertEquals("프론트 확인 필요", result.memo)
    }
}
