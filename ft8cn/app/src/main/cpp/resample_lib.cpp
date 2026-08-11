#include <jni.h>

#include <cstdint>
#include <vector>

extern "C" {
#include "third_party/libsamplerate/samplerate.h"
}

namespace {

constexpr float kPositiveClip = 0.999999f;
constexpr float kNegativeClip = -0.999999f;

void short2Float(const short *src16, float *dst32, int len) {
    for (int i = 0; i < len; ++i) {
        dst32[i] = src16[i] / 32768.0f;
    }
}

void float2Short(const float *src32, short *dst16, int len) {
    for (int i = 0; i < len; ++i) {
        if (src32[i] > kPositiveClip) {
            dst16[i] = 32767;
        } else if (src32[i] < kNegativeClip) {
            // Preserve the v0.93 binary's observable clipping value.
            dst16[i] = -32766;
        } else {
            dst16[i] = static_cast<short>(src32[i] * 32767.0f);
        }
    }
}

int outputSizeFor(jsize inputSize, jint inputRate, jint outputRate) {
    if (inputSize <= 0 || inputRate <= 0 || outputRate <= 0) {
        return 0;
    }
    const float rate = static_cast<float>(outputRate) / static_cast<float>(inputRate);
    return static_cast<int>(static_cast<float>(inputSize) * rate);
}

std::vector<float> resampleLinear(const float *input,
                                  int inputSize,
                                  int inputRate,
                                  int outputRate,
                                  int channels) {
    const int outputSize = outputSizeFor(inputSize, inputRate, outputRate);
    std::vector<float> output(static_cast<size_t>(outputSize), 0.0f);
    if (input == nullptr || outputSize == 0 || channels <= 0) {
        return output;
    }

    const float rate = static_cast<float>(outputRate) / static_cast<float>(inputRate);
    SRC_DATA data{};
    data.data_in = input;
    data.data_out = output.data();
    data.input_frames = inputSize;
    data.output_frames = outputSize;
    data.src_ratio = rate;

    // The v0.93 JNI wrappers ignore both the return code and output_frames_gen,
    // and return the complete zero-initialized destination buffer.
    (void) src_simple(&data, SRC_LINEAR, channels);
    return output;
}

std::vector<float> readFloatInput(JNIEnv *env, jfloatArray inputData) {
    if (inputData == nullptr) {
        return {};
    }
    const jsize size = env->GetArrayLength(inputData);
    std::vector<float> input(static_cast<size_t>(size));
    if (size > 0) {
        env->GetFloatArrayRegion(inputData, 0, size, input.data());
    }
    return input;
}

std::vector<float> readShortInput(JNIEnv *env, jshortArray inputData) {
    if (inputData == nullptr) {
        return {};
    }
    const jsize size = env->GetArrayLength(inputData);
    std::vector<short> input16(static_cast<size_t>(size));
    std::vector<float> input32(static_cast<size_t>(size));
    if (size > 0) {
        env->GetShortArrayRegion(inputData, 0, size, input16.data());
        short2Float(input16.data(), input32.data(), size);
    }
    return input32;
}

jfloatArray newFloatArray(JNIEnv *env, const std::vector<float> &values) {
    const jsize size = static_cast<jsize>(values.size());
    jfloatArray result = env->NewFloatArray(size);
    if (result != nullptr && size > 0) {
        env->SetFloatArrayRegion(result, 0, size, values.data());
    }
    return result;
}

jshortArray newShortArray(JNIEnv *env, const std::vector<float> &values) {
    const jsize size = static_cast<jsize>(values.size());
    std::vector<short> converted(static_cast<size_t>(size));
    if (size > 0) {
        float2Short(values.data(), converted.data(), size);
    }
    jshortArray result = env->NewShortArray(size);
    if (result != nullptr && size > 0) {
        env->SetShortArrayRegion(result, 0, size, converted.data());
    }
    return result;
}

jbyteArray newUnsignedPcm8Array(JNIEnv *env, const std::vector<float> &values) {
    const jsize size = static_cast<jsize>(values.size());
    std::vector<short> converted16(static_cast<size_t>(size));
    std::vector<jbyte> converted8(static_cast<size_t>(size));
    if (size > 0) {
        float2Short(values.data(), converted16.data(), size);
        for (jsize i = 0; i < size; ++i) {
            const auto highByte = static_cast<uint8_t>(
                    static_cast<uint16_t>(converted16[static_cast<size_t>(i)]) >> 8U);
            converted8[static_cast<size_t>(i)] = static_cast<jbyte>(highByte ^ 0x80U);
        }
    }
    jbyteArray result = env->NewByteArray(size);
    if (result != nullptr && size > 0) {
        env->SetByteArrayRegion(result, 0, size, converted8.data());
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_bg7yoz_ft8cn_wave_FT8Resample_get8Resample32(
        JNIEnv *env, jclass, jfloatArray inputData, jint inputRate, jint outputRate, jint channels) {
    const std::vector<float> input = readFloatInput(env, inputData);
    return newUnsignedPcm8Array(
            env, resampleLinear(input.data(), static_cast<int>(input.size()),
                                inputRate, outputRate, channels));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_bg7yoz_ft8cn_wave_FT8Resample_get8Resample16(
        JNIEnv *env, jclass, jshortArray inputData, jint inputRate, jint outputRate, jint channels) {
    const std::vector<float> input = readShortInput(env, inputData);
    return newUnsignedPcm8Array(
            env, resampleLinear(input.data(), static_cast<int>(input.size()),
                                inputRate, outputRate, channels));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_bg7yoz_ft8cn_wave_FT8Resample_get32Resample32(
        JNIEnv *env, jclass, jfloatArray inputData, jint inputRate, jint outputRate, jint) {
    const std::vector<float> input = readFloatInput(env, inputData);
    return newFloatArray(env, resampleLinear(input.data(), static_cast<int>(input.size()),
                                              inputRate, outputRate, 1));
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_bg7yoz_ft8cn_wave_FT8Resample_get16Resample32(
        JNIEnv *env, jclass, jfloatArray inputData, jint inputRate, jint outputRate, jint channels) {
    const std::vector<float> input = readFloatInput(env, inputData);
    return newShortArray(env, resampleLinear(input.data(), static_cast<int>(input.size()),
                                              inputRate, outputRate, channels));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_bg7yoz_ft8cn_wave_FT8Resample_get32Resample16(
        JNIEnv *env, jclass, jshortArray inputData, jint inputRate, jint outputRate, jint) {
    const std::vector<float> input = readShortInput(env, inputData);
    return newFloatArray(env, resampleLinear(input.data(), static_cast<int>(input.size()),
                                              inputRate, outputRate, 1));
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_bg7yoz_ft8cn_wave_FT8Resample_get16Resample16(
        JNIEnv *env, jclass, jshortArray inputData, jint inputRate, jint outputRate, jint) {
    const std::vector<float> input = readShortInput(env, inputData);
    return newShortArray(env, resampleLinear(input.data(), static_cast<int>(input.size()),
                                              inputRate, outputRate, 1));
}
