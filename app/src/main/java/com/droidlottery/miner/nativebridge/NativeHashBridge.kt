package com.droidlottery.miner.nativebridge

/** JNI benchmark primitive. It does not connect to pools or submit shares. */
object NativeHashBridge {
    init { System.loadLibrary("droidlottery_native") }

    external fun benchmark(iterations: Int): Long
}
