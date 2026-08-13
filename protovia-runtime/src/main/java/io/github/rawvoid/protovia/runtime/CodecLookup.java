package io.github.rawvoid.protovia.runtime;

import io.github.rawvoid.protovia.ProtoAny;
import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wkt.AnyCodec;
import io.github.rawvoid.protovia.wkt.BoolValue;
import io.github.rawvoid.protovia.wkt.BytesValue;
import io.github.rawvoid.protovia.wkt.DoubleValue;
import io.github.rawvoid.protovia.wkt.DurationCodec;
import io.github.rawvoid.protovia.wkt.FloatValue;
import io.github.rawvoid.protovia.wkt.Int32Value;
import io.github.rawvoid.protovia.wkt.Int64Value;
import io.github.rawvoid.protovia.wkt.StringValue;
import io.github.rawvoid.protovia.wkt.TimestampCodec;
import io.github.rawvoid.protovia.wkt.UInt32Value;
import io.github.rawvoid.protovia.wkt.UInt64Value;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link ProtoCodec} by convention {@code TypeName + "ProtoCodec.INSTANCE"},
 * with an optional manual {@link #register(Class, ProtoCodec) override}.
 * Well-known types ({@code Instant}, {@code Duration}, {@link ProtoAny}, wrappers)
 * are registered up front.
 */
public final class CodecLookup {

    private static final ConcurrentHashMap<Class<?>, ProtoCodec<?>> CACHE = new ConcurrentHashMap<>();

    static {
        registerBuiltins();
    }

    private CodecLookup() {
    }

    @SuppressWarnings("unchecked")
    public static <T> ProtoCodec<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        ProtoCodec<?> cached = CACHE.get(type);
        if (cached != null) {
            return (ProtoCodec<T>) cached;
        }
        ProtoCodec<T> loaded = loadGenerated(type);
        ProtoCodec<?> existing = CACHE.putIfAbsent(type, loaded);
        return (ProtoCodec<T>) (existing != null ? existing : loaded);
    }

    public static <T> void register(Class<T> type, ProtoCodec<T> codec) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        CACHE.put(type, codec);
    }

    public static void clear() {
        CACHE.clear();
        registerBuiltins();
    }

    private static void registerBuiltins() {
        CACHE.put(Instant.class, TimestampCodec.INSTANCE);
        CACHE.put(Duration.class, DurationCodec.INSTANCE);
        CACHE.put(ProtoAny.class, AnyCodec.INSTANCE);
        CACHE.put(DoubleValue.class, DoubleValue.INSTANCE);
        CACHE.put(FloatValue.class, FloatValue.INSTANCE);
        CACHE.put(Int64Value.class, Int64Value.INSTANCE);
        CACHE.put(UInt64Value.class, UInt64Value.INSTANCE);
        CACHE.put(Int32Value.class, Int32Value.INSTANCE);
        CACHE.put(UInt32Value.class, UInt32Value.INSTANCE);
        CACHE.put(BoolValue.class, BoolValue.INSTANCE);
        CACHE.put(StringValue.class, StringValue.INSTANCE);
        CACHE.put(BytesValue.class, BytesValue.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static <T> ProtoCodec<T> loadGenerated(Class<T> type) {
        String codecName = type.getName() + "ProtoCodec";
        try {
            Class<?> codecClass = Class.forName(codecName, true, type.getClassLoader());
            Field instance = codecClass.getField("INSTANCE");
            Object value = instance.get(null);
            if (!(value instanceof ProtoCodec<?> codec)) {
                throw new ProtoException(codecName + ".INSTANCE is not a ProtoCodec");
            }
            return (ProtoCodec<T>) codec;
        } catch (ClassNotFoundException e) {
            throw new ProtoException(
                    "No ProtoCodec for " + type.getName()
                            + ". Annotate the type with @ProtoMessage and enable protovia-processor.",
                    e);
        } catch (ReflectiveOperationException e) {
            throw new ProtoException("Failed to load " + codecName, e);
        }
    }
}
