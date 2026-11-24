/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.runtime;

import io.github.rawvoid.protovia.ProtoAny;
import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wkt.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link ProtoCodec} by convention {@code package + ".internal." + TypeName + "ProtoCodec.INSTANCE"},
 * with an optional manual {@link #register(Class, ProtoCodec) override}.
 * Generated and well-known codecs are cached per-class via {@link ClassValue} so dynamically loaded
 * classes can be unloaded cleanly, while well-known builtins ({@code Instant}, {@code Duration},
 * {@link ProtoAny}, wrappers) live in an immutable table that is never cleared.
 *
 * @author Rawvoid
 */
public final class CodecLookup {

    private static final Map<Class<?>, ProtoCodec<?>> BUILTINS = Map.ofEntries(
        Map.entry(Instant.class, TimestampCodec.INSTANCE),
        Map.entry(Duration.class, DurationCodec.INSTANCE),
        Map.entry(ProtoAny.class, AnyCodec.INSTANCE),
        Map.entry(DoubleValue.class, DoubleValue.INSTANCE),
        Map.entry(FloatValue.class, FloatValue.INSTANCE),
        Map.entry(Int64Value.class, Int64Value.INSTANCE),
        Map.entry(UInt64Value.class, UInt64Value.INSTANCE),
        Map.entry(Int32Value.class, Int32Value.INSTANCE),
        Map.entry(UInt32Value.class, UInt32Value.INSTANCE),
        Map.entry(BoolValue.class, BoolValue.INSTANCE),
        Map.entry(StringValue.class, StringValue.INSTANCE),
        Map.entry(BytesValue.class, BytesValue.INSTANCE)
    );

    private static final ClassValue<ProtoCodec<?>> GENERATED = new ClassValue<>() {
        @Override
        protected ProtoCodec<?> computeValue(Class<?> type) {
            ProtoCodec<?> builtin = BUILTINS.get(type);
            return builtin != null ? builtin : loadGenerated(type);
        }
    };

    private static final ConcurrentHashMap<Class<?>, ProtoCodec<?>> OVERRIDES = new ConcurrentHashMap<>();
    private static final Object OVERRIDE_LOCK = new Object();
    private static volatile boolean hasOverrides;

    private CodecLookup() {
    }

    /**
     * @param type entity class
     * @return cached or newly loaded codec
     */
    @SuppressWarnings("unchecked")
    public static <T> ProtoCodec<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (hasOverrides) {
            ProtoCodec<?> override = OVERRIDES.get(type);
            if (override != null) {
                return (ProtoCodec<T>) override;
            }
        }
        return (ProtoCodec<T>) GENERATED.get(type);
    }

    /**
     * Replaces any previous top-level lookup mapping for {@code type}. Affects {@link #get(Class)}
     * (and callers such as {@code Protovia.*} and {@code ProtoConfig.*}, including {@code unpack});
     * generated codecs call child {@code XxxProtoCodec.INSTANCE} directly for static dispatch.
     *
     * @param type  entity class
     * @param codec codec to use
     */
    public static <T> void register(Class<T> type, ProtoCodec<T> codec) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        synchronized (OVERRIDE_LOCK) {
            OVERRIDES.put(type, codec);
            hasOverrides = true;
        }
    }

    /**
     * Drops manual registrations while keeping immutable well-known builtins intact.
     */
    public static void clear() {
        synchronized (OVERRIDE_LOCK) {
            OVERRIDES.clear();
            hasOverrides = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ProtoCodec<T> loadGenerated(Class<T> type) {
        String pkg = type.getPackageName();
        String simpleBinaryName = pkg.isEmpty() ? type.getName() : type.getName().substring(pkg.length() + 1);
        String codecName = pkg.isEmpty()
            ? "internal." + simpleBinaryName + "ProtoCodec"
            : pkg + ".internal." + simpleBinaryName + "ProtoCodec";
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
