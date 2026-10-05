#include <jni.h>
#include <cstdint>

// A deterministic CPU-work benchmark used to validate the NDK/JNI pipeline.
// Production SHA-256d job execution belongs in a separately reviewed engine.
extern "C" JNIEXPORT jlong JNICALL
Java_com_droidlottery_miner_nativebridge_NativeHashBridge_benchmark(
        JNIEnv *, jobject, jint iterations) {
    uint64_t state = 0x6a09e667f3bcc909ULL;
    for (jint index = 0; index < iterations; ++index) {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
    }
    return static_cast<jlong>(state);
}
