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

package io.github.rawvoid.protovia;

/**
 * Unchecked failure while encoding, decoding, looking up a codec, or validating a schema.
 *
 * @author Rawvoid
 */
public class ProtoException extends RuntimeException {

    public ProtoException(String message) {
        super(message);
    }

    public ProtoException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ProtoException invalidTag(int tag) {
        return new ProtoException("invalid tag " + tag);
    }

    public static ProtoException malformedVarint() {
        return new ProtoException("malformed varint");
    }

    public static ProtoException truncatedVarint() {
        return new ProtoException("truncated varint");
    }

    public static ProtoException truncated(int needed) {
        return new ProtoException("truncated message, needed " + needed + " bytes");
    }

    public static ProtoException truncated(String detail) {
        return new ProtoException("truncated message: " + detail);
    }

    public static ProtoException negativeSize() {
        return new ProtoException("negative length-delimited size");
    }
}
