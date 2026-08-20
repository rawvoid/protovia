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

package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/**
 * {@code google.protobuf.UInt32Value}. Java {@code int} on the wire as uint32.
 *
 * @param value wrapped uint32 bits
 * @author Rawvoid
 */
public record UInt32Value(int value) {
    public static final ProtoCodec<UInt32Value> INSTANCE = WrapperCodec.uint32();
}
