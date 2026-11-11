package com.botem.global.property

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "s3")
data class S3Properties(
    @field:NotBlank
    val bucket: String,

    @field:NotBlank
    val region: String,
) {
    val publicBaseUrl: String
        get() = "https://$bucket.s3.$region.amazonaws.com"
}
