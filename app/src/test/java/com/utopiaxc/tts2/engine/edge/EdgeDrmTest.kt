package com.utopiaxc.tts2.engine.edge

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 固定时间向量来自 edge-tts 上游实现（rany2/edge-tts，Sec-MS-GEC v2）。
 * 用于防止 SHA-256 / CS64 移植错误导致握手 403。
 */
class EdgeDrmTest {
    @Test
    fun secMsGecV2_matchesUpstreamVectors() {
        val vectors = mapOf(
            1794886897L to "289F517C7BBFDD21-933402A2C49A58A4A65A4B3A8065E3BB72EB588FFC2FDB372243FB973C42E749",
            1700000000L to "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2"
        )
        for ((unixSeconds, expected) in vectors) {
            assertEquals(expected, EdgeDrm.generateSecMsGecHeaderAt(unixSeconds))
        }
    }
}
