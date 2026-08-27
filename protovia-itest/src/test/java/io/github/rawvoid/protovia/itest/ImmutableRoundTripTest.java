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

package io.github.rawvoid.protovia.itest;

import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.itest.model.Address;
import io.github.rawvoid.protovia.itest.model.FactoryUser;
import io.github.rawvoid.protovia.itest.model.ImmutableUser;
import io.github.rawvoid.protovia.itest.model.SuperUser;
import io.github.rawvoid.protovia.itest.model.ValueUser;
import io.github.rawvoid.protovia.itest.model.internal.ImmutableUserProtoCodec;
import io.github.rawvoid.protovia.wire.ProtoReader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * @author Rawvoid
 */
class ImmutableRoundTripTest {

    @Test
    void handwrittenAllArgsRoundTrip() {
        ImmutableUser user = new ImmutableUser("Ada", 36, new Address("Paris", "Rue"), List.of("dev"));
        ImmutableUser back = Protovia.fromBytes(Protovia.toBytes(user), ImmutableUser.class);
        assertEquals(user, back);
    }

    @Test
    void handwrittenMergeReplacesAndAppendsWithoutMutatingExisting() {
        ImmutableUser existing = new ImmutableUser("Ada", 1, new Address("Paris", null), List.of("a"));
        ImmutableUser incoming = new ImmutableUser("Ada", 1, new Address(null, "Rue"), List.of("b"));
        ImmutableUser merged = ImmutableUserProtoCodec.INSTANCE.mergeFrom(
            new ProtoReader(Protovia.toBytes(incoming)), existing);
        assertEquals("Paris", merged.getAddress().city());
        assertEquals("Rue", merged.getAddress().street());
        assertEquals(List.of("a", "b"), merged.getTags());
        assertEquals(List.of("a"), existing.getTags());
        assertNotSame(existing, merged);
    }

    @Test
    void lombokValueBuilderRoundTrip() {
        ValueUser user = ValueUser.builder().name("Ada").age(36).build();
        ValueUser back = Protovia.fromBytes(Protovia.toBytes(user), ValueUser.class);
        assertEquals(user, back);
    }

    @Test
    void lombokStaticFactoryRoundTrip() {
        FactoryUser user = FactoryUser.of("Ada", 36);
        FactoryUser back = Protovia.fromBytes(Protovia.toBytes(user), FactoryUser.class);
        assertEquals(user, back);
    }

    @Test
    void superBuilderFlattensMixinFields() {
        SuperUser user = SuperUser.builder().id("u1").name("Ada").build();
        SuperUser back = Protovia.fromBytes(Protovia.toBytes(user), SuperUser.class);
        assertEquals("u1", back.getId());
        assertEquals("Ada", back.getName());
        assertEquals(user, back);
    }
}
