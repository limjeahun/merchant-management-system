package com.application.port.out

import com.common.event.OcrRequestEvent

interface OcrEventPort {
    fun publishEvent(event: OcrRequestEvent)
}