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

package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.STRING)
public final class CountingKeyAdapter implements ProtoAdapter<CountingKey, String> {

    public static final CountingKeyAdapter INSTANCE = new CountingKeyAdapter();
    private static final AtomicInteger TO_WIRE_CALLS = new AtomicInteger();

    private CountingKeyAdapter() {
    }

    public static void reset() {
        TO_WIRE_CALLS.set(0);
    }

    public static int toWireCalls() {
        return TO_WIRE_CALLS.get();
    }

    @Override
    public String toWire(CountingKey value) {
        TO_WIRE_CALLS.incrementAndGet();
        return value.raw();
    }

    @Override
    public CountingKey fromWire(String wire) {
        return new CountingKey(wire);
    }
}
