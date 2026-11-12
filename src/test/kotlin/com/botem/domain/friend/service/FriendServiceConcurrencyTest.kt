package com.botem.domain.friend.service

import com.botem.domain.friend.repository.FriendshipRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

// 동시 요청이 실제로 각자 커밋돼야 하므로 테스트 트랜잭션을 사용하지 않는다
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaAuditingConfig::class, FriendService::class, FriendServiceTest.FixedClockConfig::class)
class FriendServiceConcurrencyTest {

    @Autowired
    private lateinit var friendService: FriendService

    @Autowired
    private lateinit var friendshipRepository: FriendshipRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        friendshipRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `같은 두 회원이 동시에 서로 친구를 맺어도 중복 없이 한 쌍만 저장한다`() {
        val me = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val friend = userRepository.save(User(kakaoId = 2L, nickname = "민지", profileImageUrl = null))
        val requests = List(REQUEST_COUNT) { index ->
            if (index % 2 == 0) me.id to checkNotNull(friend.wishProfileKey) else friend.id to checkNotNull(me.wishProfileKey)
        }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(REQUEST_COUNT)

        val results = requests.map { (userId, key) ->
            executor.submit {
                start.await()
                friendService.addFriend(userId, key)
            }
        }
        start.countDown()
        results.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(2, friendshipRepository.count())
    }

    companion object {
        private const val REQUEST_COUNT = 6
    }
}
