package com.botem.domain.funding.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class FundingErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    GIFT_PURCHASE_CONFIRMATION_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "FUNDING_001",
        "선물 인증을 완료하려면 보탬 링크 구매 여부를 확인해야 합니다.",
    ),
    FUNDING_NOT_FOUND(HttpStatus.NOT_FOUND, "FUNDING_002", "펀딩을 찾을 수 없습니다."),
    FUNDING_OWNER_ONLY(HttpStatus.FORBIDDEN, "FUNDING_003", "펀딩 개설자만 접근할 수 있습니다."),
    BIRTHDAY_FUNDING_REQUIRED(HttpStatus.BAD_REQUEST, "FUNDING_004", "생일 펀딩에서만 사용할 수 있습니다."),
    FUNDING_NOT_ENDED(HttpStatus.CONFLICT, "FUNDING_005", "펀딩이 마감된 후 결과를 확인할 수 있습니다."),
    FUNDING_RESULT_NOT_OPENABLE(HttpStatus.CONFLICT, "FUNDING_006", "현재 상태에서는 펀딩 결과를 열 수 없습니다."),
    PENDING_CONTRIBUTION_EXISTS(
        HttpStatus.CONFLICT,
        "FUNDING_007",
        "입금 확인 대기 중인 참여를 모두 확인한 후 결과를 열 수 있습니다.",
    ),
    GIFT_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "FUNDING_008", "선물 인증 정보를 찾을 수 없습니다."),
    GIFT_VERIFICATION_RECIPIENT_ONLY(
        HttpStatus.FORBIDDEN,
        "FUNDING_009",
        "선물을 받는 사용자만 인증할 수 있습니다.",
    ),
    GIFT_VERIFICATION_NOT_COMPLETABLE(
        HttpStatus.CONFLICT,
        "FUNDING_010",
        "현재 상태에서는 선물 인증을 완료할 수 없습니다.",
    ),
    FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE(
        HttpStatus.CONFLICT,
        "FUNDING_011",
        "현재 펀딩 상태에서는 선물 인증을 완료할 수 없습니다.",
    ),
    GIFT_VERIFICATION_IMAGE_NOT_UPLOADED(
        HttpStatus.BAD_REQUEST,
        "FUNDING_012",
        "업로드된 선물 인증 이미지를 찾을 수 없습니다.",
    ),
    GIFT_VERIFICATION_IMAGE_CHECK_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "FUNDING_013",
        "선물 인증 이미지 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.",
    ),
    SURPRISE_FUNDING_REQUIRED(HttpStatus.BAD_REQUEST, "FUNDING_014", "서프라이즈 펀딩에서만 사용할 수 있습니다."),
    SURPRISE_RECIPIENT_ONLY(HttpStatus.FORBIDDEN, "FUNDING_015", "서프라이즈를 받는 사용자만 접근할 수 있습니다."),
    SURPRISE_REVEAL_NOT_AVAILABLE(
        HttpStatus.NOT_FOUND,
        "FUNDING_016",
        "공개된 서프라이즈를 찾을 수 없습니다.",
    ),
    SURPRISE_REVEAL_NOT_OPENABLE(
        HttpStatus.CONFLICT,
        "FUNDING_017",
        "현재 상태에서는 서프라이즈를 열 수 없습니다.",
    ),
    SURPRISE_NOT_OPENED(
        HttpStatus.CONFLICT,
        "FUNDING_018",
        "서프라이즈를 연 후 감사 카드를 보낼 수 있습니다.",
    ),
}
