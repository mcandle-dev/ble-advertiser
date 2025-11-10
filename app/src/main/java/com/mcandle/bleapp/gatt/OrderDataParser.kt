package com.mcandle.bleapp.gatt

import android.util.Log
import java.nio.charset.Charset

/**
 * GATT Write 데이터 파싱 유틸리티
 *
 * 지원 포맷:
 * 1. Simple: "order_id=12345678"
 * 2. With params: "order_id=12345678&phone=1234&amount=15000"
 */
object OrderDataParser {

    private const val TAG = "OrderDataParser"

    data class OrderRequest(
        val orderId: String,
        val additionalData: Map<String, String>? = null
    )

    /**
     * ByteArray를 파싱하여 OrderRequest 생성
     *
     * @param data GATT Write로 받은 ByteArray
     * @return OrderRequest 객체
     * @throws IllegalArgumentException order_id가 없거나 형식이 잘못된 경우
     */
    fun parse(data: ByteArray): OrderRequest {
        val dataString = String(data, Charset.forName("UTF-8"))
        Log.d(TAG, "Parsing data: $dataString")

        // URL 파라미터 형식 파싱 (key=value&key=value)
        val params = try {
            dataString.split("&").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid parameter format: $it")
                }
                parts[0].trim() to parts[1].trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse data: $dataString", e)
            throw IllegalArgumentException("Invalid data format: ${e.message}")
        }

        // order_id는 필수
        val orderId = params["order_id"]
            ?: throw IllegalArgumentException("order_id is required")

        // order_id를 제외한 나머지 데이터
        val additionalData = params.filter { it.key != "order_id" }

        Log.d(TAG, "Parsed - orderId: $orderId, additionalData: $additionalData")

        return OrderRequest(
            orderId = orderId,
            additionalData = if (additionalData.isNotEmpty()) additionalData else null
        )
    }

    /**
     * 응답 데이터 생성
     *
     * @param success 성공 여부
     * @param message 응답 메시지
     * @return JSON 형식의 ByteArray
     */
    fun createResponse(success: Boolean, message: String = ""): ByteArray {
        val jsonResponse = """{"status": "${if (success) "success" else "error"}", "message": "$message"}"""
        return jsonResponse.toByteArray(Charset.forName("UTF-8"))
    }
}
