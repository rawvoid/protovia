package io.github.rawvoid.protovia.runtime;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link ProtoCodec} by convention {@code TypeName + "ProtoCodec.INSTANCE"},
 * with an optional manual {@link #register(Class, ProtoCodec) override}.
 */
public final class CodecLookup {

    private static final ConcurrentHashMap<Class<?>, ProtoCodec<?>> CACHE = new ConcurrentHashMap<>();

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
