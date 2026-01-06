package com.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

    @Bean
    fun corsFilter(): CorsFilter {
        val config =
                CorsConfiguration().apply {
                    // 개발 환경: localhost 허용
                    allowedOrigins = listOf("http://localhost:3000", "http://127.0.0.1:3000")
                    // 허용할 HTTP 메서드
                    allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    // 허용할 헤더
                    allowedHeaders = listOf("*")
                    // 자격 증명(쿠키 등) 허용
                    allowCredentials = true
                    // Preflight 캐시 시간 (1시간)
                    maxAge = 3600L
                }

        val source =
                UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }

        return CorsFilter(source)
    }
}
