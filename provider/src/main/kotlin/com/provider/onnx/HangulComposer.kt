package com.provider.onnx

/**
 * Hangul Jamo를 완성형 한글로 조합하는 유틸리티
 * PaddleOCR 한국어 모델은 자모(Jamo) 단위로 출력하므로 조합 필요
 */
object HangulComposer {
    
    // 초성 (19개)
    private val CHOSEONG = listOf(
        'ᄀ', 'ᄁ', 'ᄂ', 'ᄃ', 'ᄄ', 'ᄅ', 'ᄆ', 'ᄇ', 'ᄈ', 'ᄉ',
        'ᄊ', 'ᄋ', 'ᄌ', 'ᄍ', 'ᄎ', 'ᄏ', 'ᄐ', 'ᄑ', 'ᄒ'
    )
    
    // 중성 (21개)
    private val JUNGSEONG = listOf(
        'ᅡ', 'ᅢ', 'ᅣ', 'ᅤ', 'ᅥ', 'ᅦ', 'ᅧ', 'ᅨ', 'ᅩ', 'ᅪ',
        'ᅫ', 'ᅬ', 'ᅭ', 'ᅮ', 'ᅯ', 'ᅰ', 'ᅱ', 'ᅲ', 'ᅳ', 'ᅴ', 'ᅵ'
    )
    
    // 종성 (28개, 첫 번째는 없음)
    private val JONGSEONG = listOf(
        null, 'ᆨ', 'ᆩ', 'ᆪ', 'ᆫ', 'ᆬ', 'ᆭ', 'ᆮ', 'ᆯ', 'ᆰ',
        'ᆱ', 'ᆲ', 'ᆳ', 'ᆴ', 'ᆵ', 'ᆶ', 'ᆷ', 'ᆸ', 'ᆹ', 'ᆺ',
        'ᆻ', 'ᆼ', 'ᆽ', 'ᆾ', 'ᆿ', 'ᇀ', 'ᇁ', 'ᇂ'
    )
    
    // 호환 자모 매핑 (Compatibility Jamo -> Conjoining Jamo)
    private val COMPAT_CHOSEONG_MAP = mapOf(
        'ㄱ' to 'ᄀ', 'ㄲ' to 'ᄁ', 'ㄴ' to 'ᄂ', 'ㄷ' to 'ᄃ', 'ㄸ' to 'ᄄ',
        'ㄹ' to 'ᄅ', 'ㅁ' to 'ᄆ', 'ㅂ' to 'ᄇ', 'ㅃ' to 'ᄈ', 'ㅅ' to 'ᄉ',
        'ㅆ' to 'ᄊ', 'ㅇ' to 'ᄋ', 'ㅈ' to 'ᄌ', 'ㅉ' to 'ᄍ', 'ㅊ' to 'ᄎ',
        'ㅋ' to 'ᄏ', 'ㅌ' to 'ᄐ', 'ㅍ' to 'ᄑ', 'ㅎ' to 'ᄒ'
    )
    
    private val COMPAT_JUNGSEONG_MAP = mapOf(
        'ㅏ' to 'ᅡ', 'ㅐ' to 'ᅢ', 'ㅑ' to 'ᅣ', 'ㅒ' to 'ᅤ', 'ㅓ' to 'ᅥ',
        'ㅔ' to 'ᅦ', 'ㅕ' to 'ᅧ', 'ㅖ' to 'ᅨ', 'ㅗ' to 'ᅩ', 'ㅘ' to 'ᅪ',
        'ㅙ' to 'ᅫ', 'ㅚ' to 'ᅬ', 'ㅛ' to 'ᅭ', 'ㅜ' to 'ᅮ', 'ㅝ' to 'ᅯ',
        'ㅞ' to 'ᅰ', 'ㅟ' to 'ᅱ', 'ㅠ' to 'ᅲ', 'ㅡ' to 'ᅳ', 'ㅢ' to 'ᅴ', 'ㅣ' to 'ᅵ'
    )
    
    /**
     * 자모 문자열을 완성형 한글로 조합
     */
    fun compose(jamo: String): String {
        if (jamo.isEmpty()) return jamo
        
        val result = StringBuilder()
        var cho: Int? = null
        var jung: Int? = null
        var jong: Int? = null
        
        for (c in jamo) {
            val choIndex = getChoseongIndex(c)
            val jungIndex = getJungseongIndex(c)
            val jongIndex = getJongseongIndex(c)
            
            when {
                choIndex != null -> {
                    // 초성이 나왔을 때
                    if (cho != null && jung != null) {
                        // 이전 글자 완성
                        result.append(composeSyllable(cho, jung, jong))
                        cho = choIndex
                        jung = null
                        jong = null
                    } else if (cho == null) {
                        cho = choIndex
                    } else {
                        // 초성만 있는 상태에서 또 초성
                        result.append(CHOSEONG[cho])
                        cho = choIndex
                    }
                }
                jungIndex != null -> {
                    // 중성이 나왔을 때
                    if (cho != null && jung == null) {
                        jung = jungIndex
                    } else if (cho != null && jung != null) {
                        // 종성 없이 다음 중성 -> 이전 글자 완성
                        result.append(composeSyllable(cho, jung, jong))
                        // 새 글자 시작 불가 (초성 없음)
                        result.append(JUNGSEONG[jungIndex])
                        cho = null
                        jung = null
                        jong = null
                    } else {
                        result.append(JUNGSEONG[jungIndex])
                    }
                }
                jongIndex != null -> {
                    // 종성이 나왔을 때
                    if (cho != null && jung != null && jong == null) {
                        jong = jongIndex
                    } else if (cho != null && jung != null && jong != null) {
                        // 이미 종성이 있으면 글자 완성 후 새 초성으로
                        result.append(composeSyllable(cho, jung, jong))
                        // 종성을 초성으로 변환 시도
                        val newCho = jongToChoMapping(jong)
                        if (newCho != null) {
                            cho = newCho
                            jung = null
                            jong = jongIndex
                        } else {
                            cho = null
                            jung = null
                            jong = null
                        }
                    } else {
                        // 종성만 단독으로
                        result.append(c)
                    }
                }
                else -> {
                    // 한글 자모가 아닌 문자
                    if (cho != null && jung != null) {
                        result.append(composeSyllable(cho, jung, jong))
                    } else if (cho != null) {
                        result.append(CHOSEONG[cho])
                    }
                    result.append(c)
                    cho = null
                    jung = null
                    jong = null
                }
            }
        }
        
        // 남은 글자 처리
        if (cho != null && jung != null) {
            result.append(composeSyllable(cho, jung, jong))
        } else if (cho != null) {
            result.append(CHOSEONG[cho])
        }
        
        return result.toString()
    }
    
    /**
     * 초성, 중성, 종성 인덱스로 완성형 한글 생성
     */
    private fun composeSyllable(cho: Int, jung: Int, jong: Int?): Char {
        // 한글 음절 공식: 0xAC00 + (초성 * 21 * 28) + (중성 * 28) + 종성
        val syllableCode = 0xAC00 + (cho * 21 * 28) + (jung * 28) + (jong ?: 0)
        return syllableCode.toChar()
    }
    
    private fun getChoseongIndex(c: Char): Int? {
        val idx = CHOSEONG.indexOf(c)
        if (idx >= 0) return idx
        val mapped = COMPAT_CHOSEONG_MAP[c]
        if (mapped != null) return CHOSEONG.indexOf(mapped)
        return null
    }
    
    private fun getJungseongIndex(c: Char): Int? {
        val idx = JUNGSEONG.indexOf(c)
        if (idx >= 0) return idx
        val mapped = COMPAT_JUNGSEONG_MAP[c]
        if (mapped != null) return JUNGSEONG.indexOf(mapped)
        return null
    }
    
    private fun getJongseongIndex(c: Char): Int? {
        for (i in 1 until JONGSEONG.size) {
            if (JONGSEONG[i] == c) return i
        }
        return null
    }
    
    private fun jongToChoMapping(jong: Int): Int? {
        // 종성 -> 초성 매핑 (일부만 가능)
        return when (jong) {
            1 -> 0  // ᆨ -> ᄀ
            4 -> 2  // ᆫ -> ᄂ
            7 -> 3  // ᆮ -> ᄃ
            8 -> 5  // ᆯ -> ᄅ
            16 -> 6 // ᆷ -> ᄆ
            17 -> 7 // ᆸ -> ᄇ
            19 -> 9 // ᆺ -> ᄉ
            21 -> 11 // ᆼ -> ᄋ
            22 -> 12 // ᆽ -> ᄌ
            23 -> 14 // ᆾ -> ᄎ
            24 -> 15 // ᆿ -> ᄏ
            25 -> 16 // ᇀ -> ᄐ
            26 -> 17 // ᇁ -> ᄑ
            27 -> 18 // ᇂ -> ᄒ
            else -> null
        }
    }
}
