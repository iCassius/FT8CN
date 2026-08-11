package com.bg7yoz.ft8cn.nativebaseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bg7yoz.ft8cn.BuildConfig;
import com.bg7yoz.ft8cn.FT8Common;
import com.bg7yoz.ft8cn.Ft8Message;
import com.bg7yoz.ft8cn.ft8listener.A91List;
import com.bg7yoz.ft8cn.ft8listener.FT8SignalListener;
import com.bg7yoz.ft8cn.ft8listener.ReBuildSignal;
import com.bg7yoz.ft8cn.ft8signal.FT8Package;
import com.bg7yoz.ft8cn.ft8transmit.GenerateFT8;
import com.bg7yoz.ft8cn.ui.SpectrumFragment;
import com.bg7yoz.ft8cn.ui.SpectrumView;
import com.bg7yoz.ft8cn.wave.FT8Resample;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Captures deterministic behavior from the currently packaged production libft8cn.so.
 *
 * <p>The output is an oracle, not a hand-written expectation. A rebuilt library is run through
 * the same inputs and compared with scripts/native_baseline/compare_oracle.py. No microphone,
 * radio, CAT, PTT, network, or user data is used.</p>
 */
@RunWith(AndroidJUnit4.class)
public class NativeOracleInstrumentationTest {
    static final String SCHEMA = "ft8cn-native-behavior-oracle-v2";
    static final String OUTPUT_FILE = "native-behavior-oracle-v2.json";
    static final String FIXTURE_OUTPUT_FILE = "native-decoder-mixed-v1.pcm16le";
    private static final long FIXED_UTC_MILLIS = 1710000000000L;
    private static final int SAMPLE_RATE = FT8Common.SAMPLE_RATE;
    private static final int SLOT_SAMPLES = FT8Common.FT8_SLOT_TIME * SAMPLE_RATE;
    private static final int SIGNAL_OFFSET_SAMPLES = 6000;
    private static final int SECOND_SIGNAL_OFFSET_SAMPLES = 7200;
    private static final String STANDARD_MESSAGE = "CQ K1ABC FN20";
    private static final String SECOND_STANDARD_MESSAGE = "CQ W9XYZ EN50";
    private static final String FREE_TEXT_MESSAGE = "TNX BOB 73 GL";
    private static final String DECODER_FIXTURE_ASSET =
            "nativebaseline/native-decoder-mixed-v1.pcm16le";
    private static final String DECODER_FIXTURE_SHA256 =
            "a6df8dbcb21f9ac67d72a2a2d39d45e3da846193b370f8108c806b140262a6be";
    private static final String FIRST_PAYLOAD_HEX = "000000204def1a8a14080000";
    private static final String SECOND_PAYLOAD_HEX = "000000206149dc0859880000";
    private static final int MAX_DECODER_CANDIDATES = 120;

    @Test
    public void captureProductionNativeOracle() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        JSONObject snapshot = captureSnapshot(context);
        File output = new File(context.getFilesDir(), OUTPUT_FILE);
        writeUtf8(output, snapshot.toString(2) + "\n");

        assertEquals(SCHEMA, snapshot.getString("schema"));
        assertEquals(31, snapshot.getJSONArray("declared_native_contract").length());
        assertEquals(6, snapshot.getJSONObject("resample").length());
        assertEquals(8, snapshot.getJSONObject("spectrum").length());
        JSONObject floatDecode = snapshot.getJSONObject("decode").getJSONObject("float_input");
        assertTrue("synthetic oracle signal must produce a valid FT8 decode",
                floatDecode.getJSONObject("before_subtract").getJSONArray("valid_messages").length() > 0);
        assertTrue("DecoderGetA91 and subtract must be exercised",
                floatDecode.getBoolean("subtract_exercised"));
        assertTrue("decoded A91 values must retain one of the frozen fixture payloads",
                floatDecode.getBoolean("decoded_a91_payloads_match_fixture_inputs"));
        assertTrue("deep decode mode must be exercised",
                floatDecode.getBoolean("deep_mode_exercised"));
        assertTrue("oracle file was not written", output.isFile() && output.length() > 0);
    }

    /**
     * Maintainer-only bootstrap for the immutable decoder input fixture.
     *
     * <p>This is deliberately skipped unless the explicit instrumentation argument is present.
     * It must only be run against the approved v0.93 production prebuilt library, never against a
     * reconstruction candidate.</p>
     */
    @Test
    public void captureProductionDecoderFixture() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("production fixture capture was not explicitly authorized",
                "YES".equals(arguments.getString("allow_production_fixture_capture")));
        Context context = ApplicationProvider.getApplicationContext();
        ProductionFixture fixture = synthesizeProductionFixture();
        File output = new File(context.getFilesDir(), FIXTURE_OUTPUT_FILE);
        try (FileOutputStream stream = new FileOutputStream(output, false)) {
            stream.write(fixture.pcm16le);
        }
        JSONObject metadata = new JSONObject();
        metadata.put("schema", "ft8cn-native-decoder-fixture-v1");
        metadata.put("fixture_file", FIXTURE_OUTPUT_FILE);
        metadata.put("fixture_sha256", sha256(fixture.pcm16le));
        metadata.put("sample_rate", SAMPLE_RATE);
        metadata.put("sample_count", SLOT_SAMPLES);
        metadata.put("first_message", STANDARD_MESSAGE);
        metadata.put("first_payload_hex", fixture.firstPayloadHex);
        metadata.put("second_message", SECOND_STANDARD_MESSAGE);
        metadata.put("second_payload_hex", fixture.secondPayloadHex);
        writeUtf8(new File(context.getFilesDir(), FIXTURE_OUTPUT_FILE + ".json"),
                metadata.toString(2) + "\n");
        assertEquals(SLOT_SAMPLES * 2, output.length());
    }

    private static JSONObject captureSnapshot(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", SCHEMA);
        root.put("metadata", captureMetadata(context));
        root.put("declared_native_contract", captureDeclaredNativeContract());

        JSONObject packEncode = capturePackAndEncode();
        root.put("hashes", captureHashes());
        root.put("pack_encode", packEncode);
        root.put("resample", captureResample());
        root.put("spectrum", captureSpectrum(context));
        root.put("decode", captureDecode());
        return root;
    }

    private static JSONObject captureMetadata(Context context) throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("application_id", BuildConfig.APPLICATION_ID);
        metadata.put("version_name", BuildConfig.VERSION_NAME);
        metadata.put("version_code", BuildConfig.VERSION_CODE);
        metadata.put("oracle_inputs", "frozen-public-pcm-and-synthetic-v2");

        String nativeAbi = currentProcessAbi();
        JSONObject environment = new JSONObject();
        environment.put("native_abi", nativeAbi);
        environment.put("page_size", Os.sysconf(OsConstants._SC_PAGESIZE));
        metadata.put("environment", environment);

        JSONObject source = new JSONObject();
        source.put("git_commit", BuildConfig.ORACLE_GIT_COMMIT);
        source.put("git_dirty", parseBuildBoolean(BuildConfig.ORACLE_GIT_DIRTY));
        source.put("build_variant", BuildConfig.BUILD_TYPE);
        source.put("native_candidate", BuildConfig.NATIVE_CANDIDATE);
        metadata.put("source", source);

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        String targetApk = context.getApplicationInfo().sourceDir;
        String testApk = testContext.getApplicationInfo().sourceDir;
        JSONObject artifacts = new JSONObject();
        artifacts.put("target_apk_sha256", sha256(new File(targetApk)));
        artifacts.put("test_apk_sha256", sha256(new File(testApk)));
        artifacts.put("native_library_sha256", sha256ApkNativeLibrary(targetApk, nativeAbi));
        metadata.put("artifacts", artifacts);

        JSONObject nonAuthoritative = new JSONObject();
        nonAuthoritative.put("sdk_int", Build.VERSION.SDK_INT);
        nonAuthoritative.put("device", Build.DEVICE);
        nonAuthoritative.put("build_fingerprint", Build.FINGERPRINT);
        nonAuthoritative.put("supported_abis", new JSONArray(Build.SUPPORTED_ABIS));
        metadata.put("non_authoritative_environment", nonAuthoritative);
        return metadata;
    }

    private static JSONArray captureDeclaredNativeContract() throws JSONException {
        Class<?>[] owners = {
                FT8Package.class,
                GenerateFT8.class,
                FT8SignalListener.class,
                ReBuildSignal.class,
                FT8Resample.class,
                SpectrumFragment.class,
                SpectrumView.class
        };
        List<Method> methods = new ArrayList<>();
        for (Class<?> owner : owners) {
            for (Method method : owner.getDeclaredMethods()) {
                if (Modifier.isNative(method.getModifiers())) {
                    methods.add(method);
                }
            }
        }
        Collections.sort(methods, Comparator.comparing(NativeOracleInstrumentationTest::methodKey));
        JSONArray result = new JSONArray();
        for (Method method : methods) {
            JSONObject item = new JSONObject();
            item.put("owner", method.getDeclaringClass().getName());
            item.put("name", method.getName());
            item.put("static", Modifier.isStatic(method.getModifiers()));
            item.put("return_type", typeName(method.getReturnType()));
            JSONArray parameters = new JSONArray();
            for (Class<?> parameter : method.getParameterTypes()) {
                parameters.put(typeName(parameter));
            }
            item.put("parameter_types", parameters);
            result.put(item);
        }
        return result;
    }

    private static String methodKey(Method method) {
        StringBuilder result = new StringBuilder();
        result.append(method.getDeclaringClass().getName()).append('#').append(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) {
            result.append(typeName(parameter)).append(';');
        }
        return result.append(')').toString();
    }

    private static String typeName(Class<?> type) {
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        return type.getName();
    }

    private static JSONObject captureHashes() throws JSONException {
        JSONObject result = new JSONObject();
        String[] callsigns = {"K1ABC", "W9XYZ", "PJ4/K1ABC", "BG7YOZ"};
        for (String callsign : callsigns) {
            JSONObject hashes = new JSONObject();
            hashes.put("hash10", FT8Package.getHash10(callsign));
            hashes.put("hash12", FT8Package.getHash12(callsign));
            hashes.put("hash22", FT8Package.getHash22(callsign));
            result.put(callsign, hashes);
        }
        return result;
    }

    private static JSONObject capturePackAndEncode() throws Exception {
        Method packFreeText = nativeMethod(
                GenerateFT8.class, "packFreeTextTo77", String.class, byte[].class);
        Method pack77 = nativeMethod(GenerateFT8.class, "pack77", String.class, byte[].class);
        Method encode = nativeMethod(GenerateFT8.class, "ft8_encode", byte[].class, byte[].class);
        Method synth = nativeMethod(
                GenerateFT8.class,
                "synth_gfsk",
                byte[].class,
                int.class,
                float.class,
                float.class,
                float.class,
                int.class,
                float[].class,
                int.class);

        byte[] freePayload = new byte[GenerateFT8.FTX_LDPC_K_BYTES];
        int freeResult = (Integer) invoke(packFreeText, null, FREE_TEXT_MESSAGE, freePayload);
        byte[] standardPayload = new byte[GenerateFT8.FTX_LDPC_K_BYTES];
        int standardResult = (Integer) invoke(pack77, null, STANDARD_MESSAGE, standardPayload);

        byte[] tones = new byte[GenerateFT8.num_tones];
        invoke(encode, null, standardPayload, tones);
        int signalLength = Math.round(GenerateFT8.num_tones * GenerateFT8.symbol_period * SAMPLE_RATE);
        float[] signal = new float[signalLength];
        invoke(synth, null, tones, tones.length, 1000.0f, 2.0f,
                GenerateFT8.symbol_period, SAMPLE_RATE, signal, 0);

        JSONObject result = new JSONObject();
        result.put("free_text_input", FREE_TEXT_MESSAGE);
        result.put("free_text_result", freeResult);
        result.put("free_text_payload_hex", hex(freePayload));
        result.put("standard_input", STANDARD_MESSAGE);
        result.put("standard_result", standardResult);
        result.put("standard_payload_hex", hex(standardPayload));
        result.put("tones", unsignedByteArray(tones));
        result.put("signal_profile", floatSignalProfile(signal, 257));
        return result;
    }

    private static JSONObject captureResample() throws JSONException {
        short[] shortInput = new short[257];
        float[] floatInput = new float[257];
        for (int i = 0; i < shortInput.length; i++) {
            double value = 11000.0 * Math.sin((2.0 * Math.PI * 17.0 * i) / shortInput.length)
                    + 3500.0 * Math.cos((2.0 * Math.PI * 41.0 * i) / shortInput.length);
            shortInput[i] = (short) Math.round(value);
            floatInput[i] = shortInput[i] / 32768.0f;
        }

        JSONObject result = new JSONObject();
        result.put("get16Resample16_7812_to_12000",
                shortArray(FT8Resample.get16Resample16(shortInput, 7812, 12000, 1)));
        result.put("get32Resample16_7812_to_12000",
                floatArray(FT8Resample.get32Resample16(shortInput, 7812, 12000, 1)));
        result.put("get16Resample32_12000_to_8000",
                shortArray(FT8Resample.get16Resample32(floatInput, 12000, 8000, 1)));
        result.put("get32Resample32_12000_to_8000",
                floatArray(FT8Resample.get32Resample32(floatInput, 12000, 8000, 1)));
        result.put("get8Resample16_12000_to_8000",
                unsignedByteArray(FT8Resample.get8Resample16(shortInput, 12000, 8000, 1)));
        result.put("get8Resample32_24000_to_7812",
                unsignedByteArray(FT8Resample.get8Resample32(floatInput, 24000, 7812, 1)));
        return result;
    }

    private static JSONObject captureSpectrum(Context context) throws Exception {
        // Native spectrum code processes complete 1,920-sample FT8 symbol blocks.
        final int fftLength = 1920;
        int[] integerInput = new int[fftLength];
        float[] floatInput = new float[fftLength];
        for (int i = 0; i < fftLength; i++) {
            double value = 0.55 * Math.sin((2.0 * Math.PI * 32.0 * i) / fftLength)
                    + 0.20 * Math.cos((2.0 * Math.PI * 127.0 * i) / fftLength);
            floatInput[i] = (float) value;
            integerInput[i] = Math.round(floatInput[i] * 32767.0f);
        }

        SpectrumFragment fragment = new SpectrumFragment();
        AtomicReference<SpectrumView> viewRef = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> viewRef.set(new SpectrumView(context)));
        SpectrumView view = viewRef.get();

        int[] fragmentInt = new int[fftLength / 2];
        int[] fragmentFloat = new int[fftLength / 2];
        int[] fragmentRawInt = new int[fftLength / 2];
        int[] fragmentRawFloat = new int[fftLength / 2];
        fragment.getFFTData(integerInput, fragmentInt);
        fragment.getFFTDataFloat(floatInput, fragmentFloat);
        fragment.getFFTDataRaw(integerInput, fragmentRawInt);
        fragment.getFFTDataRawFloat(floatInput, fragmentRawFloat);

        int[] viewInt = new int[fftLength / 2];
        int[] viewFloat = new int[fftLength / 2];
        int[] viewRawInt = new int[fftLength / 2];
        int[] viewRawFloat = new int[fftLength / 2];
        view.getFFTData(integerInput, viewInt);
        view.getFFTDataFloat(floatInput, viewFloat);
        view.getFFTDataRaw(integerInput, viewRawInt);
        view.getFFTDataRawFloat(floatInput, viewRawFloat);

        JSONObject result = new JSONObject();
        result.put("SpectrumFragment_getFFTData", intArray(fragmentInt));
        result.put("SpectrumFragment_getFFTDataFloat", intArray(fragmentFloat));
        result.put("SpectrumFragment_getFFTDataRaw", intArray(fragmentRawInt));
        result.put("SpectrumFragment_getFFTDataRawFloat", intArray(fragmentRawFloat));
        result.put("SpectrumView_getFFTData", intArray(viewInt));
        result.put("SpectrumView_getFFTDataFloat", intArray(viewFloat));
        result.put("SpectrumView_getFFTDataRaw", intArray(viewRawInt));
        result.put("SpectrumView_getFFTDataRawFloat", intArray(viewRawFloat));
        return result;
    }

    private static JSONObject captureDecode() throws Exception {
        PcmFixture fixture = loadDecoderFixture();

        FT8SignalListener listener = new FT8SignalListener(null, null);
        try {
            JSONObject result = new JSONObject();
            result.put("fixture_asset", DECODER_FIXTURE_ASSET);
            result.put("fixture_sha256", sha256(fixture.pcm16le));
            result.put("fixture_messages", new JSONArray()
                    .put(STANDARD_MESSAGE)
                    .put(SECOND_STANDARD_MESSAGE));
            result.put("waveform_profile", floatSignalProfile(fixture.floatSamples, 257));
            result.put("float_input", decodeFloatAndSubtract(listener, fixture.floatSamples));
            result.put("integer_input", decodeInteger(listener, fixture.integerSamples));
            return result;
        } finally {
            listener.stopListen();
        }
    }

    private static JSONObject decodeFloatAndSubtract(
            FT8SignalListener listener, float[] waveform) throws JSONException {
        long decoder = listener.InitDecoder(FIXED_UTC_MILLIS, SAMPLE_RATE, waveform.length, true);
        if (decoder == 0) {
            throw new AssertionError("InitDecoder returned a null handle for float input");
        }
        try {
            listener.DecoderMonitorPressFloat(waveform, decoder);
            listener.setDecodeMode(decoder, false);
            DecodePass before = decodePass(listener, decoder);

            JSONObject result = new JSONObject();
            result.put("before_subtract", before.json);
            boolean subtractExercised = before.subtractionInputs.size() > 0;
            result.put("subtract_exercised", subtractExercised);
            if (subtractExercised) {
                result.put("decoded_a91_payloads_match_fixture_inputs",
                        payloadsMatchFixtureInputs(before.subtractionInputs));
                ReBuildSignal.subtractSignal(decoder, before.subtractionInputs);
                listener.setDecodeMode(decoder, false);
                DecodePass afterSubtract = decodePass(listener, decoder);
                result.put("after_subtract", afterSubtract.json);
                boolean semanticEffectObserved = !before.json.toString()
                        .equals(afterSubtract.json.toString());
                result.put("subtract_semantic_effect_observed", semanticEffectObserved);
                result.put("subtract_coverage", semanticEffectObserved
                        ? "semantic-delta-observed"
                        : "call-safety-only-production-showed-no-observable-delta");
            } else {
                result.put("decoded_a91_payloads_match_fixture_inputs", false);
                result.put("after_subtract", JSONObject.NULL);
                result.put("subtract_semantic_effect_observed", false);
                result.put("subtract_coverage", "not-exercised");
            }

            listener.DecoderFt8Reset(decoder, FIXED_UTC_MILLIS, waveform.length);
            listener.DecoderMonitorPressFloat(waveform, decoder);
            listener.setDecodeMode(decoder, true);
            result.put("deep_mode_exercised", true);
            result.put("after_reset_deep", decodePass(listener, decoder).json);
            return result;
        } finally {
            listener.DeleteDecoder(decoder);
        }
    }

    private static JSONObject decodeInteger(FT8SignalListener listener, int[] waveform) throws JSONException {
        long decoder = listener.InitDecoder(FIXED_UTC_MILLIS, SAMPLE_RATE, waveform.length, true);
        if (decoder == 0) {
            throw new AssertionError("InitDecoder returned a null handle for integer input");
        }
        try {
            listener.DecoderMonitorPress(waveform, decoder);
            listener.setDecodeMode(decoder, false);
            return decodePass(listener, decoder).json;
        } finally {
            listener.DeleteDecoder(decoder);
        }
    }

    private static DecodePass decodePass(FT8SignalListener listener, long decoder) throws JSONException {
        int candidateCount = listener.DecoderFt8FindSync(decoder);
        if (candidateCount < 0 || candidateCount > MAX_DECODER_CANDIDATES) {
            throw new AssertionError("DecoderFt8FindSync returned out-of-range count: "
                    + candidateCount);
        }
        List<DecodedRecord> records = new ArrayList<>();
        A91List subtractionInputs = new A91List();
        for (int index = 0; index < candidateCount; index++) {
            Ft8Message message = new Ft8Message(FT8Common.FT8_MODE);
            message.utcTime = FIXED_UTC_MILLIS;
            if (listener.DecoderFt8Analysis(index, decoder, message) && message.isValid) {
                byte[] payload = listener.DecoderGetA91(decoder);
                if (payload == null || payload.length != GenerateFT8.FTX_LDPC_K_BYTES) {
                    throw new AssertionError("DecoderGetA91 returned invalid payload length: "
                            + (payload == null ? "null" : payload.length));
                }
                DecodedRecord record = new DecodedRecord(message, payload);
                records.add(record);
                subtractionInputs.add(payload, message.freq_hz, message.time_sec);
            }
        }
        Collections.sort(records, Comparator.comparing(DecodedRecord::sortKey));
        JSONArray valid = new JSONArray();
        for (DecodedRecord record : records) {
            valid.put(record.json);
        }
        JSONObject json = new JSONObject();
        json.put("candidate_count", candidateCount);
        json.put("valid_messages", valid);
        return new DecodePass(json, subtractionInputs);
    }

    private static Method nativeMethod(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw error;
        }
    }

    private static ProductionFixture synthesizeProductionFixture() throws Exception {
        Method pack77 = nativeMethod(GenerateFT8.class, "pack77", String.class, byte[].class);
        Method encode = nativeMethod(GenerateFT8.class, "ft8_encode", byte[].class, byte[].class);
        Method synth = nativeMethod(
                GenerateFT8.class,
                "synth_gfsk",
                byte[].class,
                int.class,
                float.class,
                float.class,
                float.class,
                int.class,
                float[].class,
                int.class);

        SynthesizedMessage first = synthesizeMessage(
                pack77, encode, synth, STANDARD_MESSAGE, 1000.0f, SIGNAL_OFFSET_SAMPLES);
        SynthesizedMessage second = synthesizeMessage(
                pack77, encode, synth, SECOND_STANDARD_MESSAGE, 1300.0f,
                SECOND_SIGNAL_OFFSET_SAMPLES);
        byte[] pcm16le = new byte[SLOT_SAMPLES * 2];
        ByteBuffer output = ByteBuffer.wrap(pcm16le).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < SLOT_SAMPLES; i++) {
            float mixed = 0.45f * (first.waveform[i] + second.waveform[i]);
            mixed = Math.max(-1.0f, Math.min(1.0f, mixed));
            output.putShort((short) Math.round(mixed * 32767.0f));
        }
        return new ProductionFixture(
                pcm16le, hex(first.payload), hex(second.payload), null, null);
    }

    private static SynthesizedMessage synthesizeMessage(
            Method pack77,
            Method encode,
            Method synth,
            String message,
            float frequency,
            int offset) throws Exception {
        byte[] payload = new byte[GenerateFT8.FTX_LDPC_K_BYTES];
        int packResult = (Integer) invoke(pack77, null, message, payload);
        if (packResult < 0) {
            throw new AssertionError("production fixture message could not be packed: " + message);
        }
        byte[] tones = new byte[GenerateFT8.num_tones];
        invoke(encode, null, payload, tones);
        float[] waveform = new float[SLOT_SAMPLES];
        invoke(synth, null, tones, tones.length, frequency, 2.0f,
                GenerateFT8.symbol_period, SAMPLE_RATE, waveform, offset);
        return new SynthesizedMessage(payload, waveform);
    }

    private static PcmFixture loadDecoderFixture() throws Exception {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        byte[] pcm16le;
        try (InputStream input = testContext.getAssets().open(DECODER_FIXTURE_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream(SLOT_SAMPLES * 2)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            pcm16le = output.toByteArray();
        }
        if (pcm16le.length != SLOT_SAMPLES * 2) {
            throw new AssertionError("decoder fixture length mismatch: " + pcm16le.length);
        }
        String digest = sha256(pcm16le);
        if (!DECODER_FIXTURE_SHA256.equals(digest)) {
            throw new AssertionError("decoder fixture SHA-256 mismatch: " + digest);
        }
        int[] integerSamples = new int[SLOT_SAMPLES];
        float[] floatSamples = new float[SLOT_SAMPLES];
        ByteBuffer input = ByteBuffer.wrap(pcm16le).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < SLOT_SAMPLES; i++) {
            short sample = input.getShort();
            integerSamples[i] = sample;
            floatSamples[i] = sample / 32768.0f;
        }
        return new PcmFixture(pcm16le, FIRST_PAYLOAD_HEX, SECOND_PAYLOAD_HEX,
                integerSamples, floatSamples);
    }

    private static boolean payloadsMatchFixtureInputs(A91List values) {
        byte[] first = parseHex(FIRST_PAYLOAD_HEX);
        byte[] second = parseHex(SECOND_PAYLOAD_HEX);
        for (A91List.A91 value : values.list) {
            if (!equalBitPrefix(first, value.a91, 77)
                    && !equalBitPrefix(second, value.a91, 77)) {
                return false;
            }
        }
        return values.size() > 0;
    }

    private static boolean parseBuildBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new AssertionError("oracle build provenance was not supplied by capture_oracle.ps1");
    }

    private static String currentProcessAbi() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        switch (architecture) {
            case "x86_64":
            case "amd64":
                return "x86_64";
            case "aarch64":
            case "arm64":
                return "arm64-v8a";
            case "x86":
            case "i686":
                return "x86";
            case "arm":
            case "armv7l":
                return "armeabi-v7a";
            default:
                throw new AssertionError("unsupported process architecture: " + architecture);
        }
    }

    private static String sha256ApkNativeLibrary(String apkPath, String abi) throws Exception {
        try (ZipFile archive = new ZipFile(apkPath)) {
            ZipEntry entry = archive.getEntry("lib/" + abi + "/libft8cn.so");
            if (entry == null) {
                throw new AssertionError("target APK has no libft8cn.so for process ABI " + abi);
            }
            try (InputStream input = archive.getInputStream(entry)) {
                return sha256(input);
            }
        }
    }

    private static String sha256(File file) throws Exception {
        try (InputStream input = new java.io.FileInputStream(file)) {
            return sha256(input);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(data));
    }

    private static String sha256(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static JSONObject floatSignalProfile(float[] values, int probeCount) throws Exception {
        if (values == null) {
            throw new AssertionError("native float array result was null");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        double sum = 0.0;
        double squareSum = 0.0;
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new AssertionError("native float result contains a non-finite value");
            }
            bytes.clear();
            bytes.putFloat(value);
            digest.update(bytes.array());
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            sum += value;
            squareSum += value * (double) value;
        }

        JSONArray probes = new JSONArray();
        int actualProbeCount = Math.min(probeCount, values.length);
        for (int i = 0; i < actualProbeCount; i++) {
            int index = actualProbeCount == 1 ? 0
                    : (int) (((long) i * (values.length - 1)) / (actualProbeCount - 1));
            JSONObject probe = new JSONObject();
            probe.put("index", index);
            probe.put("value", values[index]);
            probes.put(probe);
        }

        JSONObject profile = new JSONObject();
        profile.put("length", values.length);
        profile.put("raw_float32_le_sha256", hex(digest.digest()));
        profile.put("minimum", values.length == 0 ? 0.0 : minimum);
        profile.put("maximum", values.length == 0 ? 0.0 : maximum);
        profile.put("mean", values.length == 0 ? 0.0 : sum / values.length);
        profile.put("rms", values.length == 0 ? 0.0 : Math.sqrt(squareSum / values.length));
        profile.put("probes", probes);
        return profile;
    }

    private static JSONArray floatArray(float[] values) throws JSONException {
        if (values == null) {
            throw new AssertionError("native float array result was null");
        }
        JSONArray result = new JSONArray();
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new AssertionError("native float result contains a non-finite value");
            }
            result.put(value);
        }
        return result;
    }

    private static JSONArray shortArray(short[] values) throws JSONException {
        if (values == null) {
            throw new AssertionError("native short array result was null");
        }
        JSONArray result = new JSONArray();
        for (short value : values) {
            result.put(value);
        }
        return result;
    }

    private static JSONArray intArray(int[] values) throws JSONException {
        if (values == null) {
            throw new AssertionError("native int array result was null");
        }
        JSONArray result = new JSONArray();
        for (int value : values) {
            result.put(value);
        }
        return result;
    }

    private static JSONArray unsignedByteArray(byte[] values) throws JSONException {
        if (values == null) {
            throw new AssertionError("native byte array result was null");
        }
        JSONArray result = new JSONArray();
        for (byte value : values) {
            result.put(value & 0xff);
        }
        return result;
    }

    private static byte[] parseHex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("odd-length hex string");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static boolean equalBitPrefix(byte[] left, byte[] right, int bitCount) {
        if (left == null || right == null || bitCount < 0
                || left.length * 8 < bitCount || right.length * 8 < bitCount) {
            return false;
        }
        int fullBytes = bitCount / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        int remainingBits = bitCount % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return (left[fullBytes] & mask) == (right[fullBytes] & mask);
    }

    private static String hex(byte[] data) {
        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte value : data) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void writeUtf8(File path, String value) throws Exception {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(path, false), StandardCharsets.UTF_8)) {
            writer.write(value);
        }
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static final class DecodePass {
        final JSONObject json;
        final A91List subtractionInputs;

        DecodePass(JSONObject json, A91List subtractionInputs) {
            this.json = json;
            this.subtractionInputs = subtractionInputs;
        }
    }

    private static class PcmFixture {
        final byte[] pcm16le;
        final String firstPayloadHex;
        final String secondPayloadHex;
        final int[] integerSamples;
        final float[] floatSamples;

        PcmFixture(
                byte[] pcm16le,
                String firstPayloadHex,
                String secondPayloadHex,
                int[] integerSamples,
                float[] floatSamples) {
            this.pcm16le = pcm16le;
            this.firstPayloadHex = firstPayloadHex;
            this.secondPayloadHex = secondPayloadHex;
            this.integerSamples = integerSamples;
            this.floatSamples = floatSamples;
        }
    }

    private static final class ProductionFixture extends PcmFixture {
        ProductionFixture(
                byte[] pcm16le,
                String firstPayloadHex,
                String secondPayloadHex,
                int[] integerSamples,
                float[] floatSamples) {
            super(pcm16le, firstPayloadHex, secondPayloadHex, integerSamples, floatSamples);
        }
    }

    private static final class SynthesizedMessage {
        final byte[] payload;
        final float[] waveform;

        SynthesizedMessage(byte[] payload, float[] waveform) {
            this.payload = payload;
            this.waveform = waveform;
        }
    }

    private static final class DecodedRecord {
        final JSONObject json;
        final String text;
        final float frequency;
        final float time;

        DecodedRecord(Ft8Message message, byte[] payload) throws JSONException {
            text = message.getMessageText();
            frequency = message.freq_hz;
            time = message.time_sec;
            json = new JSONObject();
            json.put("text", text);
            json.put("callsign_to", nullable(message.callsignTo));
            json.put("callsign_from", nullable(message.callsignFrom));
            json.put("extra_info", nullable(message.extraInfo));
            json.put("modifier", nullable(message.modifier));
            json.put("i3", message.i3);
            json.put("n3", message.n3);
            json.put("snr", message.snr);
            json.put("time_sec", message.time_sec);
            json.put("freq_hz", message.freq_hz);
            json.put("score", message.score);
            json.put("message_hash", message.messageHash);
            json.put("a91_hex", payload == null ? JSONObject.NULL : hex(payload));
        }

        String sortKey() {
            return text + "\u0000" + frequency + "\u0000" + time;
        }
    }
}
