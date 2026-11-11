package com.botem.global.config

import com.botem.global.property.S3Properties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
class S3Config {

    // 자격 증명은 기본 체인(환경변수, ~/.aws, EC2 인스턴스 프로파일)에서 서명 시점에 조회한다
    @Bean
    fun s3Presigner(s3Properties: S3Properties): S3Presigner =
        S3Presigner.builder()
            .region(Region.of(s3Properties.region))
            .build()

    @Bean
    fun s3Client(s3Properties: S3Properties): S3Client =
        S3Client.builder()
            .region(Region.of(s3Properties.region))
            .build()
}
