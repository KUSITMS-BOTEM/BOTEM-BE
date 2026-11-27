package com.botem.domain.user.service

import com.botem.domain.user.repository.USER_STATUS_CACHE
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserStatusCacheEvictor {

    // 탈퇴가 커밋된 뒤 지워야, 롤백된 탈퇴 때문에 캐시만 비거나 커밋 전 값이 다시 캐시되지 않는다
    @TransactionalEventListener
    @CacheEvict(cacheNames = [USER_STATUS_CACHE], key = "#p0.userId")
    fun evict(event: UserWithdrawnEvent) = Unit
}
