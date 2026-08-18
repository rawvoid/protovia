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

package io.github.rawvoid.protovia.adapter;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Opt-in {@link InetAddress} as proto3 {@code bytes} (4 bytes for IPv4, 16 bytes for IPv6 in network byte order).
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.BYTES)
public final class InetAddressBytes implements ProtoAdapter<InetAddress, byte[]> {

    public static final InetAddressBytes INSTANCE = new InetAddressBytes();

    private InetAddressBytes() {
    }

    @Override
    public byte[] toWire(InetAddress value) {
        return value.getAddress();
    }

    @Override
    public InetAddress fromWire(byte[] wire) {
        try {
            return InetAddress.getByAddress(wire);
        } catch (UnknownHostException e) {
            throw new ProtoException("invalid IP address bytes: " + (wire == null ? 0 : wire.length) + " bytes", e);
        }
    }
}
