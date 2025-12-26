package com.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Merchant Management System API")
                .description("OCR 기반 가맹점 관리 시스템 API")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("Development Team")
                )
        )
}
