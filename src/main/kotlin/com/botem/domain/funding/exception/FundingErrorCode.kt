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
    ACTIVE_FUNDING_LIMIT_EXCEEDED(
        HttpStatus.CONFLICT,
        "FUNDING_019",
        "진행 중인 펀딩은 최대 5개까지 개설할 수 있습니다.",
    ),
    FUNDING_CLOSED(HttpStatus.CONFLICT, "FUNDING_020", "마감된 펀딩에는 참여할 수 없습니다."),
    FUNDING_TARGET_REACHED(HttpStatus.CONFLICT, "FUNDING_021", "목표 금액을 이미 달성한 펀딩입니다."),
    CONTRIBUTION_AMOUNT_EXCEEDS_REMAINING(
        HttpStatus.CONFLICT,
        "FUNDING_022",
        "보탤 금액은 남은 목표 금액 이하여야 합니다.",
    ),
    CONTRIBUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "FUNDING_023", "참여 내역을 찾을 수 없습니다."),
    CONTRIBUTION_PARTICIPANT_ONLY(HttpStatus.FORBIDDEN, "FUNDING_024", "참여자 본인만 변경할 수 있습니다."),
    CONTRIBUTION_PASSWORD_MISMATCH(HttpStatus.FORBIDDEN, "FUNDING_025", "비밀번호가 일치하지 않습니다."),
    CONTRIBUTION_TRANSFER_NOT_MARKABLE(
        HttpStatus.CONFLICT,
        "FUNDING_026",
        "현재 상태에서는 송금 완료로 표시할 수 없습니다.",
    ),
    CONTRIBUTION_CONVERTED_TO_MESSAGE_ONLY(
        HttpStatus.CONFLICT,
        "FUNDING_028",
        "보태기가 마감돼서 메시지만 남았어요.",
    ),
    CONTRIBUTION_REQUEST_KEY_CONFLICT(
        HttpStatus.CONFLICT,
        "FUNDING_027",
        "이미 다른 요청에 사용된 요청 키입니다.",
    ),
    CONTRIBUTION_PASSWORD_LOCKED(
        HttpStatus.TOO_MANY_REQUESTS,
        "FUNDING_029",
        "비밀번호를 여러 번 틀렸어요. 10분 후 다시 시도해 주세요.",
    ),
    CONTRIBUTION_NOT_PENDING(
        HttpStatus.CONFLICT,
        "FUNDING_030",
        "입금 확인 대기 중인 참여만 처리할 수 있습니다.",
    ),
    FUNDING_RESULT_ALREADY_OPENED(
        HttpStatus.CONFLICT,
        "FUNDING_031",
        "결과를 확정한 펀딩은 입금 확인 상태를 바꿀 수 없습니다.",
    ),
    CONTRIBUTION_GUEST_IDENTITY_DUPLICATED(
        HttpStatus.CONFLICT,
        "FUNDING_032",
        "같은 이름으로 등록한 비회원 보탬이 이미 있습니다.",
    ),
    CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID(
        HttpStatus.UNAUTHORIZED,
        "FUNDING_033",
        "비회원 보탬 접근 토큰이 올바르지 않습니다.",
    ),
    CONTRIBUTION_GUEST_ACCESS_TOKEN_EXPIRED(
        HttpStatus.UNAUTHORIZED,
        "FUNDING_034",
        "비회원 보탬 접근 토큰이 만료되었습니다.",
    ),
    CONTRIBUTION_NOT_CANCELLABLE(
        HttpStatus.CONFLICT,
        "FUNDING_035",
        "송금 확인 중이거나 확인된 보탬은 삭제할 수 없습니다.",
    ),
    CONTRIBUTION_GUEST_NAME_DUPLICATED(
        HttpStatus.CONFLICT,
        "FUNDING_036",
        "이미 사용 중인 참여자 이름으로 변경할 수 없습니다.",
    ),
    FUNDING_EXTENSION_BEFORE_DEADLINE(
        HttpStatus.CONFLICT,
        "FUNDING_037",
        "펀딩이 마감된 후 기간을 연장할 수 있습니다.",
    ),
    FUNDING_EXTENSION_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        "FUNDING_038",
        "현재 상태에서는 펀딩 기간을 연장할 수 없습니다.",
    ),
    FUNDING_EXTENSION_LIMIT_EXCEEDED(
        HttpStatus.CONFLICT,
        "FUNDING_039",
        "펀딩 기간은 한 번만 연장할 수 있습니다.",
    ),
    FUNDING_EXTENSION_DEADLINE_INVALID(
        HttpStatus.BAD_REQUEST,
        "FUNDING_040",
        "새 마감일은 현재 시각과 기존 마감일 이후여야 합니다.",
    ),
    FUNDING_EXTENSION_PERIOD_EXCEEDED(
        HttpStatus.BAD_REQUEST,
        "FUNDING_041",
        "펀딩 기간은 최대 14일까지 연장할 수 있습니다.",
    ),
    FUNDING_EARLY_CLOSE_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        "FUNDING_042",
        "현재 상태에서는 펀딩을 조기 마감할 수 없습니다.",
    ),
    FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        "FUNDING_043",
        "현재 상태에서는 펀딩 상품을 변경할 수 없습니다.",
    ),
    SURPRISE_SELF_RECIPIENT_NOT_ALLOWED(
        HttpStatus.BAD_REQUEST,
        "FUNDING_044",
        "자기 자신에게는 서프라이즈 펀딩을 열 수 없습니다.",
    ),
    SURPRISE_FUNDING_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "FUNDING_045",
        "이미 이 친구를 위한 공개 전 서프라이즈 펀딩이 있습니다.",
    ),
    SURPRISE_NOT_REVEALED(HttpStatus.FORBIDDEN, "FUNDING_046", "아직 공개되지 않은 서프라이즈입니다."),
    SURPRISE_GIFT_PREPARATION_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        "FUNDING_047",
        "현재 상태에서는 서프라이즈 선물 준비를 진행할 수 없습니다.",
    ),
    CONTRIBUTION_NOT_EDITABLE(HttpStatus.CONFLICT, "FUNDING_048", "현재 펀딩 상태에서는 보탬을 수정할 수 없습니다."),
}
