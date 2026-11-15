package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.CompleteGiftVerificationRequest
import com.botem.domain.funding.dto.request.CreateGiftVerificationImageUploadUrlRequest
import com.botem.domain.funding.dto.response.GiftVerificationImageUploadUrlResponse
import com.botem.domain.funding.dto.response.GiftVerificationResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Gift Verification", description = "선물 인증 API")
interface GiftVerificationControllerDocs {

    @Operation(
        summary = "선물 인증 이미지 업로드 URL 발급",
        description = """
            선물 인증 이미지를 S3에 직접 업로드할 수 있는 Presigned PUT URL을 발급합니다.
            생일 펀딩은 개설자, 서프라이즈 펀딩은 실제 수신자만 요청할 수 있습니다.
            응답의 uploadUrl로 요청과 동일한 Content-Type과 Content-Length 헤더를 사용해 이미지를 PUT한 뒤,
            응답의 imageUrl을 선물 인증 완료 요청에 전달합니다. uploadUrl은 5분 동안 유효합니다.
            JPEG, PNG, WebP 형식과 1바이트 이상 5MB 이하의 파일만 지원합니다.
        """,
    )
    @RequestBody(
        content = [Content(examples = [ExampleObject(value = IMAGE_UPLOAD_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "업로드 URL 발급 성공",
            content = [Content(examples = [ExampleObject(value = IMAGE_UPLOAD_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "지원하지 않는 이미지 형식 또는 파일 크기 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "선물 수신자가 아님",
            content = [Content(examples = [ExampleObject(value = RECIPIENT_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "인증 또는 펀딩 정보 없음",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_008",
                            summary = "선물 인증 없음",
                            value = VERIFICATION_NOT_FOUND_EXAMPLE,
                        ),
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "업로드 URL을 발급할 수 없는 인증 상태",
            content = [Content(examples = [ExampleObject(value = VERIFICATION_NOT_COMPLETABLE_EXAMPLE)])],
        ),
    )
    fun createImageUploadUrl(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "선물 인증 ID") giftVerificationId: Long,
        request: CreateGiftVerificationImageUploadUrlRequest,
    ): ApiResponse<GiftVerificationImageUploadUrlResponse>

    @Operation(
        summary = "선물 인증 완료",
        description = """
            S3에 업로드한 인증 이미지와 감사 메시지를 저장하고 선물 인증 및 펀딩을 완료합니다.
            imageUrl은 이미지 업로드 URL 발급 API의 사용자·인증 ID 경로에 실제 업로드가 완료된 값만 허용합니다.
            완료 처리 전에 S3 객체 존재 여부를 확인하며, 객체가 없거나 확인할 수 없으면 상태를 변경하지 않습니다.
            purchasedThroughBotemLink가 true여야 완료할 수 있으며, 다른 상품을 받은 경우 replacementProductName을 전달합니다.
            이미 완료한 인증에 다시 요청하면 내용을 변경하지 않고 기존 완료 결과를 반환합니다.
        """,
    )
    @RequestBody(
        content = [Content(examples = [ExampleObject(value = COMPLETE_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "인증 완료 또는 기존 완료 결과 반환",
            content = [Content(examples = [ExampleObject(value = COMPLETE_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "잘못된 이미지 URL, 구매 확인 누락 또는 요청 값 오류",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_001",
                            summary = "구매 확인 누락",
                            value = PURCHASE_CONFIRMATION_REQUIRED_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_012",
                            summary = "업로드된 이미지 없음",
                            value = IMAGE_NOT_UPLOADED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "선물 수신자가 아님",
            content = [Content(examples = [ExampleObject(value = RECIPIENT_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "인증 또는 펀딩 정보 없음",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_008",
                            summary = "선물 인증 없음",
                            value = VERIFICATION_NOT_FOUND_EXAMPLE,
                        ),
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "선물 인증 또는 펀딩을 완료할 수 없는 상태",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_010",
                            summary = "인증 완료 불가 상태",
                            value = VERIFICATION_NOT_COMPLETABLE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_011",
                            summary = "펀딩 완료 불가 상태",
                            value = FUNDING_NOT_COMPLETABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "503",
            description = "S3 이미지 확인 일시 실패",
            content = [Content(examples = [ExampleObject(value = IMAGE_CHECK_UNAVAILABLE_EXAMPLE)])],
        ),
    )
    fun complete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "선물 인증 ID") giftVerificationId: Long,
        request: CompleteGiftVerificationRequest,
    ): ApiResponse<GiftVerificationResponse>

    companion object {
        private const val IMAGE_UPLOAD_REQUEST_EXAMPLE = """
            {
              "contentType": "image/jpeg",
              "contentLength": 2457600
            }
        """
        private const val IMAGE_UPLOAD_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "uploadUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
                "imageUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
              },
              "error": null
            }
        """
        private const val COMPLETE_REQUEST_EXAMPLE = """
            {
              "imageUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg",
              "thankYouMessage": "함께 보태줘서 고마워!",
              "purchasedThroughBotemLink": true,
              "replacementProductName": null
            }
        """
        private const val COMPLETE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "giftVerificationId": 12,
                "fundingId": 7,
                "status": "COMPLETED",
                "imageUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg",
                "thankYouMessage": "함께 보태줘서 고마워!",
                "purchasedThroughBotemLink": true,
                "replacementProductName": null,
                "completedAt": "2026-11-15T12:00:00"
              },
              "error": null
            }
        """
        private const val PURCHASE_CONFIRMATION_REQUIRED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_001",
                "message": "선물 인증을 완료하려면 보탬 링크 구매 여부를 확인해야 합니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_002",
                "message": "펀딩을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val VERIFICATION_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_008",
                "message": "선물 인증 정보를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val RECIPIENT_ONLY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_009",
                "message": "선물을 받는 사용자만 인증할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val VERIFICATION_NOT_COMPLETABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_010",
                "message": "현재 상태에서는 선물 인증을 완료할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_NOT_COMPLETABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_011",
                "message": "현재 펀딩 상태에서는 선물 인증을 완료할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val IMAGE_NOT_UPLOADED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_012",
                "message": "업로드된 선물 인증 이미지를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val IMAGE_CHECK_UNAVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_013",
                "message": "선물 인증 이미지 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                "detail": null
              }
            }
        """
    }
}
