package com.common.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 모든 클래스에서 사용할 수 있는 Logger 확장 함수
 *
 * 사용 예시:
 * ```
 * class MyClass {
 *     private val logger = getLogger()
 * }
 * ```
 */
fun Any.getLogger(): Logger {
    return LoggerFactory.getLogger(this::class.java.enclosingClass ?: this::class.java)
}
