package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.CreateRollingPaperAttachmentUploadUrlRequest
import com.botem.domain.funding.dto.response.RollingPaperAttachmentUploadUrlResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Rolling Paper Attachment", description = "롤링페이퍼 첨부 파일 API")
interface RollingPaperAttachmentControllerDocs {

    @Operation(
        summary = "롤링페이퍼 첨부 파일 업로드 URL 발급",
        description = """
            사진 또는 음성 파일을 S3에 직접 업로드할 수 있는 Presigned PUT URL을 발급합니다.
            로그인하지 않은 참여자도 진행 중인 펀딩에 첨부 파일을 올릴 수 있습니다.
            응답의 uploadUrl로 요청과 동일한 Content-Type, Content-Length, If-None-Match: * 헤더를 사용해 파일을 PUT한 뒤,
            응답의 fileUrl을 보태기 또는 보탬 수정 요청의 첨부 필드에 전달합니다. uploadUrl은 5분 동안 유효합니다.
            같은 uploadUrl로 한 번 업로드한 뒤에는 파일을 교체할 수 없습니다.
            사진은 JPEG, PNG, WebP 형식과 5MB 이하, 음성은 M4A, MP3, WebM, Ogg 형식과 10MB 이하를 지원합니다.
        """,
    )
    @RequestBody(
        content = [Content(examples = [ExampleObject(value = UPLOAD_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "업로드 URL 발급 성공",
            content = [Content(examples = [ExampleObject(value = UPLOAD_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "첨부 종류, 파일 형식 또는 파일 크기 오류"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음",
        ),
        SwaggerApiResponse(responseCode = "403", description = "공개 전 서프라이즈"),
        SwaggerApiResponse(responseCode = "409", description = "첨부할 수 없는 펀딩 상태"),
    )
    fun createUploadUrl(
        @Parameter(hidden = true) userId: Long?,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: CreateRollingPaperAttachmentUploadUrlRequest,
    ): ApiResponse<RollingPaperAttachmentUploadUrlResponse>

    companion object {
        private const val UPLOAD_REQUEST_EXAMPLE = """
            {
              "attachmentType": "PHOTO",
              "contentType": "image/jpeg",
              "contentLength": 2457600
            }
        """
        private const val UPLOAD_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "attachmentType": "PHOTO",
                "uploadUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/rolling-paper/7/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
                "fileUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/rolling-paper/7/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
              },
              "error": null
            }
        """
    }
}
