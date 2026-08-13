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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link ProtoCodec} by convention {@code TypeName + "ProtoCodec.INSTANCE"},
 * with an optional manual {@link #register(Class, ProtoCodec) override}.
 * Well-known types ({@code Instant}, {@code Duration}, {@link ProtoAny}, wrappers)
 * are registered up front.
 *
 * @author Rawvoid
 */
public final class CodecLookup {

    private static final ConcurrentHashMap<Class<?>, ProtoCodec<?>> CACHE = new ConcurrentHashMap<>();

    static {
        registerBuiltins();
    }

    private CodecLookup() {
    }

    /**
     * @param type entity class
     * @return cached or newly loaded codec
     */
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

    /**
     * Replaces any previous mapping for {@code type}.
     *
     * @param type  entity class
     * @param codec codec to use
     */
    public static <T> void register(Class<T> type, ProtoCodec<T> codec) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        CACHE.put(type, codec);
    }

    /**
     * Drops manual registrations and reloads well-known builtins.
     */
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
