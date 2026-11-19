package com.utopiaxc.tts2.engine

object SsmlUtils {

    /**
     * 清理 SSML 内容：确保 <speak> 标签带有正确的 xml:lang，
     * 确保 <voice> 标签带有正确的 name 属性。
     */
    fun sanitizeSsml(ssml: String, voiceName: String, locale: String): String {
        var result = ssml.trim()
        val speakIndex = result.indexOf("<speak", ignoreCase = true)
        if (speakIndex != -1) {
            val tagEndIndex = result.indexOf(">", speakIndex)
            if (tagEndIndex != -1) {
                val tagContent = result.substring(speakIndex, tagEndIndex)
                if (!tagContent.contains("xml:lang", ignoreCase = true)) {
                    val restOfSsml = result.substring(tagEndIndex + 1)
                    result = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" + restOfSsml
                }
            }
        }
        val voiceRegex = Regex("(?i)<voice([^>]*)>")
        result = voiceRegex.replace(result) { matchResult ->
            val attrs = matchResult.groupValues[1]
            if (!attrs.contains("name=", ignoreCase = true)) {
                "<voice$attrs name=\"$voiceName\">"
            } else {
                matchResult.value
            }
        }
        return result
    }

    /**
     * 转义 XML 特殊字符。
     */
    fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * 根据参数构建标准 Azure/Edge 格式的 SSML。
     */
    fun buildSsml(
        text: String,
        voiceName: String,
        locale: String,
        speed: Float,
        pitch: Float,
        volume: Float
    ): String {
        val ratePercent = Math.round((speed - 1.0f) * 100)
        val rateStr = (if (ratePercent >= 0) "+" else "") + ratePercent.toString() + "%"

        val pitchPercent = Math.round((pitch - 1.0f) * 100)
        val pitchStr = (if (pitchPercent >= 0) "+" else "") + pitchPercent.toString() + "%"

        val volumePercent = Math.round(volume * 100)
        val volumeStr = volumePercent.toString() + "%"

        val escapedText = escapeXml(text)

        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" +
                "<voice name='$voiceName'>" +
                "<prosody pitch='$pitchStr' rate='$rateStr' volume='$volumeStr'>" +
                escapedText +
                "</prosody>" +
                "</voice>" +
                "</speak>"
    }
}
