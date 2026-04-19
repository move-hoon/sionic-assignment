package com.sionic.global.client

import com.sionic.global.client.dto.AiRequest
import com.sionic.global.client.dto.AiResponse

fun interface AiClient {
    fun complete(request: AiRequest): AiResponse
}
