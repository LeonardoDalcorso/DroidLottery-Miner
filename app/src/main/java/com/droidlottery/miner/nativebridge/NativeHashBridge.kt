package com.droidlottery.miner.nativebridge

/** JNI SHA-256d worker. Network and share submission remain in Kotlin. */
object NativeHashBridge {
    private val available: Boolean = runCatching { System.loadLibrary("droidlottery_native") }.isSuccess

    /** Returns a nonce, -1 when no hash meets the target, or null if the native library is unavailable. */
    fun findShare(header: ByteArray, startNonce: Long, stride: Int, count: Int, target: ByteArray): Long? =
        if (available) nativeFindShare(header, startNonce, stride, count, target) else null

    private external fun nativeFindShare(
        header: ByteArray,
        startNonce: Long,
        stride: Int,
        count: Int,
        target: ByteArray,
    ): Long
}
