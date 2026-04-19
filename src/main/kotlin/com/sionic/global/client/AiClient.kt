package com.sionic.global.client

import com.sionic.global.client.dto.AiRequest
import com.sionic.global.client.dto.AiResponse

interface AiClient {
    fun complete(request: AiRequest): AiResponse

    fun stream(request: AiRequest, onDelta: (String) -> Unit): AiResponse =
        complete(request.copy(stream = false)).also { response ->
            if (response.content.isNotEmpty()) {
                onDelta(response.content)
            }
        }
}
