#include <jni.h>
#include <cstdint>
#include <cstring>

namespace {

constexpr uint32_t kInitialState[8] = {
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
};

constexpr uint32_t kRoundConstants[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
};

inline uint32_t rotate_right(uint32_t value, uint32_t count) {
    return (value >> count) | (value << (32 - count));
}

inline uint32_t read_be32(const uint8_t* input) {
    return (static_cast<uint32_t>(input[0]) << 24) | (static_cast<uint32_t>(input[1]) << 16) |
           (static_cast<uint32_t>(input[2]) << 8) | static_cast<uint32_t>(input[3]);
}

inline void write_be32(uint8_t* output, uint32_t value) {
    output[0] = static_cast<uint8_t>(value >> 24);
    output[1] = static_cast<uint8_t>(value >> 16);
    output[2] = static_cast<uint8_t>(value >> 8);
    output[3] = static_cast<uint8_t>(value);
}

void transform(uint32_t state[8], const uint8_t block[64]) {
    uint32_t words[64];
    for (int index = 0; index < 16; ++index) words[index] = read_be32(block + index * 4);
    for (int index = 16; index < 64; ++index) {
        const uint32_t s0 = rotate_right(words[index - 15], 7) ^ rotate_right(words[index - 15], 18) ^ (words[index - 15] >> 3);
        const uint32_t s1 = rotate_right(words[index - 2], 17) ^ rotate_right(words[index - 2], 19) ^ (words[index - 2] >> 10);
        words[index] = words[index - 16] + s0 + words[index - 7] + s1;
    }

    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
    for (int index = 0; index < 64; ++index) {
        const uint32_t s1 = rotate_right(e, 6) ^ rotate_right(e, 11) ^ rotate_right(e, 25);
        const uint32_t choose = (e & f) ^ (~e & g);
        const uint32_t temp1 = h + s1 + choose + kRoundConstants[index] + words[index];
        const uint32_t s0 = rotate_right(a, 2) ^ rotate_right(a, 13) ^ rotate_right(a, 22);
        const uint32_t majority = (a & b) ^ (a & c) ^ (b & c);
        const uint32_t temp2 = s0 + majority;
        h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2;
    }
    state[0] += a; state[1] += b; state[2] += c; state[3] += d;
    state[4] += e; state[5] += f; state[6] += g; state[7] += h;
}

void state_to_digest(const uint32_t state[8], uint8_t digest[32]) {
    for (int index = 0; index < 8; ++index) write_be32(digest + index * 4, state[index]);
}

bool meets_target(const uint8_t hash[32], const uint8_t target[32]) {
    for (int index = 0; index < 32; ++index) {
        const uint8_t value = hash[31 - index]; // Bitcoin compares the displayed (little-endian) hash.
        if (value != target[index]) return value < target[index];
    }
    return true;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_droidlottery_miner_nativebridge_NativeHashBridge_nativeFindShare(
        JNIEnv* env, jobject, jbyteArray header_array, jlong start_nonce, jint stride, jint count, jbyteArray target_array) {
    if (env->GetArrayLength(header_array) != 80 || env->GetArrayLength(target_array) != 32 || stride <= 0 || count <= 0) return -1;
    jbyte* header_input = env->GetByteArrayElements(header_array, nullptr);
    jbyte* target_input = env->GetByteArrayElements(target_array, nullptr);
    if (header_input == nullptr || target_input == nullptr) return -1;

    uint8_t first_block[64];
    uint8_t second_block[64] = {};
    uint8_t target[32];
    std::memcpy(first_block, header_input, 64);
    std::memcpy(second_block, header_input + 64, 16);
    std::memcpy(target, target_input, 32);
    second_block[16] = 0x80;
    second_block[62] = 0x02; // 80 bytes * 8 = 640 bits
    second_block[63] = 0x80;

    uint32_t midstate[8];
    std::memcpy(midstate, kInitialState, sizeof(midstate));
    transform(midstate, first_block);

    uint8_t second_hash_block[64] = {};
    second_hash_block[32] = 0x80;
    second_hash_block[62] = 0x01; // 32 bytes * 8 = 256 bits
    uint32_t nonce = static_cast<uint32_t>(start_nonce);
    jlong result = -1;

    for (int iteration = 0; iteration < count; ++iteration) {
        second_block[12] = static_cast<uint8_t>(nonce);
        second_block[13] = static_cast<uint8_t>(nonce >> 8);
        second_block[14] = static_cast<uint8_t>(nonce >> 16);
        second_block[15] = static_cast<uint8_t>(nonce >> 24);

        uint32_t first_state[8];
        std::memcpy(first_state, midstate, sizeof(first_state));
        transform(first_state, second_block);
        state_to_digest(first_state, second_hash_block);

        uint32_t second_state[8];
        std::memcpy(second_state, kInitialState, sizeof(second_state));
        transform(second_state, second_hash_block);
        uint8_t final_hash[32];
        state_to_digest(second_state, final_hash);
        if (meets_target(final_hash, target)) {
            result = static_cast<jlong>(nonce);
            break;
        }
        nonce += static_cast<uint32_t>(stride);
    }

    env->ReleaseByteArrayElements(header_array, header_input, JNI_ABORT);
    env->ReleaseByteArrayElements(target_array, target_input, JNI_ABORT);
    return result;
}
