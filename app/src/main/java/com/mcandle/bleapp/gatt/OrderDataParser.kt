package com.mcandle.bleapp.gatt

import android.util.Log
import java.nio.charset.Charset

/**
 * GATT Write 데이터 파싱 유틸리티
 *
 * Scanner에서 전송된 데이터를 그대로 orderId로 사용
 * 예: "wdfgy" → orderId = "wdfgy"
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
     * @throws IllegalArgumentException 데이터가 비어있는 경우
     */
    fun parse(data: ByteArray): OrderRequest {
        val dataString = String(data, Charset.forName("UTF-8")).trim()
        Log.d(TAG, "Parsing data: $dataString")

        if (dataString.isEmpty()) {
            throw IllegalArgumentException("Data is empty")
        }

        // 넘어온 데이터를 그대로 orderId로 사용
        Log.d(TAG, "Parsed - orderId: $dataString")

        return OrderRequest(
            orderId = dataString,
            additionalData = null
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
