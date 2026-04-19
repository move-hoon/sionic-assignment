package com.sionic.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {

    @Bean
    fun aiRestClient(
        @Value("\${ai.base-url:https://api.openai.com}")
        baseUrl: String,
    ): RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(timeoutRequestFactory())
        .build()

    private fun timeoutRequestFactory(): SimpleClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(60))
        }
}
