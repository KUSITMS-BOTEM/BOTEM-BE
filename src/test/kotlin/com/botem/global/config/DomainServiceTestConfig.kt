package com.botem.global.config

import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.product.service.RecommendedProductService
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.ProfileImageService
import com.botem.domain.user.service.UserService
import com.botem.domain.wish.repository.WishRepository
import com.botem.domain.wish.service.WishService
import com.botem.global.property.WishProfileProperties
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

// @DataJpaTest에서 다른 도메인의 잠금·저장을 위임받는 서비스를 실제 DB와 함께 쓰기 위한 설정.
// S3 등 이 테스트들과 무관한 의존성만 목으로 채운다
@TestConfiguration
class DomainServiceTestConfig {

    @Bean
    fun userService(userRepository: UserRepository): UserService =
        UserService(userRepository, mock(WishProfileProperties::class.java), mock(ProfileImageService::class.java))

    @Bean
    fun wishService(wishRepository: WishRepository, fundingRepository: FundingRepository): WishService =
        WishService(wishRepository, fundingRepository, mock(RecommendedProductService::class.java))
}
