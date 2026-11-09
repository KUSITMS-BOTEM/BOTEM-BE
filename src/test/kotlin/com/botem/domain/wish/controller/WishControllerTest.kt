package com.botem.domain.wish.controller

import com.botem.domain.wish.dto.response.WishPageResponse
import com.botem.domain.wish.dto.response.WishResponse
import com.botem.domain.wish.entity.WishStatus
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.service.WishService
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [WishController::class])
class WishControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var wishService: WishService

    @Test
    fun `페이지를 지정하지 않으면 첫 페이지 10개로 내 위시 목록을 조회한다`() {
        given(wishService.getWishes(USER_ID, 0, 10)).willReturn(
            WishPageResponse(
                content = listOf(wishResponse(status = WishStatus.FUNDING, fundingId = 7L)),
                page = 0,
                size = 10,
                totalElements = 1L,
                hasNext = false,
            ),
        )

        mockMvc.perform(get("/api/wishes").with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].id").value(WISH_ID))
            .andExpect(jsonPath("$.data.content[0].status").value("FUNDING"))
            .andExpect(jsonPath("$.data.content[0].fundingId").value(7L))
            .andExpect(jsonPath("$.data.hasNext").value(false))

        verify(wishService).getWishes(USER_ID, 0, 10)
    }

    @Test
    fun `내 위시 상세를 조회한다`() {
        given(wishService.getWish(USER_ID, WISH_ID)).willReturn(wishResponse())

        mockMvc.perform(get("/api/wishes/$WISH_ID").with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.fundingId").doesNotExist())
    }

    @Test
    fun `위시를 삭제한다`() {
        mockMvc.perform(delete("/api/wishes/$WISH_ID").with(loggedIn()).with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(wishService).deleteWish(USER_ID, WISH_ID)
    }

    @Test
    fun `펀딩 중인 위시를 삭제하면 409를 반환한다`() {
        willThrow(BusinessException(WishErrorCode.WISH_IN_FUNDING))
            .given(wishService).deleteWish(USER_ID, WISH_ID)

        mockMvc.perform(delete("/api/wishes/$WISH_ID").with(loggedIn()).with(csrf()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("WISH_003"))
    }

    @Test
    fun `인증 없이 위시 목록을 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/wishes"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(wishService)
    }

    private fun loggedIn() = authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    private fun wishResponse(
        status: WishStatus = WishStatus.AVAILABLE,
        fundingId: Long? = null,
    ): WishResponse =
        WishResponse(
            id = WISH_ID,
            productName = "에어팟 프로 3",
            productImageUrl = "https://example.com/airpods.jpg",
            price = 350_000L,
            productUrl = "https://link.coupang.com/a/example",
            status = status,
            fundingId = fundingId,
        )

    companion object {
        private const val USER_ID = 1L
        private const val WISH_ID = 21L
    }
}
