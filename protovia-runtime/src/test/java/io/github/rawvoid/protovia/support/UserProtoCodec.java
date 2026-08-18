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

package io.github.rawvoid.protovia.support;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.WireType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written codec used to lock the runtime contract before APT exists.
 *
 * @author Rawvoid
 */
public final class UserProtoCodec implements ProtoCodec<User> {

    public static final UserProtoCodec INSTANCE = new UserProtoCodec();

    private UserProtoCodec() {
    }

    @Override
    public Class<User> type() {
        return User.class;
    }

    @Override
    public int computeSize(User value) {
        int size = 0;
        String name = value.getName();
        if (name != null && !name.isEmpty()) {
            size += CodedSize.string(1, name);
        }
        int age = value.getAge();
        if (age != 0) {
            size += CodedSize.int32(2, age);
        }
        List<String> tags = value.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null) {
                    throw new ProtoException("null element in field tags");
                }
                size += CodedSize.string(3, tag);
            }
        }
        User.Address address = value.getAddress();
        if (address != null) {
            size += CodedSize.message(4, AddressProtoCodec.INSTANCE, address);
        }
        Map<String, Integer> scores = value.getScores();
        if (scores != null && !scores.isEmpty()) {
            for (Map.Entry<String, Integer> e : scores.entrySet()) {
                size += mapEntrySize(5, e.getKey(), e.getValue());
            }
        }
        Integer level = value.getLevel();
        if (level != null) {
            size += CodedSize.int32(6, level);
        }
        return size;
    }

    @Override
    public void writeTo(ProtoWriter writer, User value) {
        String name = value.getName();
        if (name != null && !name.isEmpty()) {
            writer.writeString(1, name);
        }
        int age = value.getAge();
        if (age != 0) {
            writer.writeInt32(2, age);
        }
        List<String> tags = value.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null) {
                    throw new ProtoException("null element in field tags");
                }
                writer.writeString(3, tag);
            }
        }
        User.Address address = value.getAddress();
        if (address != null) {
            writer.writeMessage(4, AddressProtoCodec.INSTANCE, address);
        }
        Map<String, Integer> scores = value.getScores();
        if (scores != null && !scores.isEmpty()) {
            for (Map.Entry<String, Integer> e : scores.entrySet()) {
                writeMapEntry(writer, 5, e.getKey(), e.getValue());
            }
        }
        Integer level = value.getLevel();
        if (level != null) {
            writer.writeInt32(6, level);
        }
    }

    @Override
    public User readFrom(ProtoReader reader) {
        User msg = new User();
        int tag;
        while ((tag = reader.readTag()) != 0) {
            switch (tag) {
                case 10 -> msg.setName(reader.readString());
                case 16 -> msg.setAge(reader.readInt32());
                case 26 -> {
                    if (msg.getTags() == null) {
                        msg.setTags(new ArrayList<>());
                    }
                    msg.getTags().add(reader.readString());
                }
                case 34 -> msg.setAddress(reader.readMessage(AddressProtoCodec.INSTANCE));
                case 42 -> {
                    if (msg.getScores() == null) {
                        msg.setScores(new LinkedHashMap<>());
                    }
                    readMapEntry(reader, msg.getScores());
                }
                case 48 -> msg.setLevel(reader.readInt32());
                default -> reader.skipField();
            }
        }
        return msg;
    }

    private static int mapEntrySize(int fieldNumber, String key, Integer value) {
        if (key == null || value == null) {
            throw new ProtoException("map entry cannot contain null");
        }
        int entry = 0;
        if (!key.isEmpty()) {
            entry += CodedSize.string(1, key);
        }
        if (value != 0) {
            entry += CodedSize.int32(2, value);
        }
        return CodedSize.lengthDelimited(fieldNumber, entry);
    }

    private static void writeMapEntry(ProtoWriter writer, int fieldNumber, String key, Integer value) {
        if (key == null || value == null) {
            throw new ProtoException("map entry cannot contain null");
        }
        int entry = 0;
        if (!key.isEmpty()) {
            entry += CodedSize.string(1, key);
        }
        if (value != 0) {
            entry += CodedSize.int32(2, value);
        }
        writer.writeTag(fieldNumber, WireType.LEN);
        writer.writeUInt32NoTag(entry);
        if (!key.isEmpty()) {
            writer.writeString(1, key);
        }
        if (value != 0) {
            writer.writeInt32(2, value);
        }
    }

    private static void readMapEntry(ProtoReader reader, Map<String, Integer> target) {
        String key = "";
        int value = 0;
        int old = reader.beginPacked();
        int tag;
        while ((tag = reader.readTag()) != 0) {
            switch (tag) {
                case 10 -> key = reader.readString();
                case 16 -> value = reader.readInt32();
                default -> reader.skipField();
            }
        }
        reader.popLimit(old);
        target.put(key, value);
    }

    public static final class AddressProtoCodec implements ProtoCodec<User.Address> {
        public static final AddressProtoCodec INSTANCE = new AddressProtoCodec();

        private AddressProtoCodec() {
        }

        @Override
        public Class<User.Address> type() {
            return User.Address.class;
        }

        @Override
        public int computeSize(User.Address value) {
            int size = 0;
            if (value.getCity() != null && !value.getCity().isEmpty()) {
                size += CodedSize.string(1, value.getCity());
            }
            if (value.getStreet() != null && !value.getStreet().isEmpty()) {
                size += CodedSize.string(2, value.getStreet());
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, User.Address value) {
            if (value.getCity() != null && !value.getCity().isEmpty()) {
                writer.writeString(1, value.getCity());
            }
            if (value.getStreet() != null && !value.getStreet().isEmpty()) {
                writer.writeString(2, value.getStreet());
            }
        }

        @Override
        public User.Address readFrom(ProtoReader reader) {
            User.Address msg = new User.Address();
            int tag;
            while ((tag = reader.readTag()) != 0) {
                switch (tag) {
                    case 10 -> msg.setCity(reader.readString());
                    case 18 -> msg.setStreet(reader.readString());
                    default -> reader.skipField();
                }
            }
            return msg;
        }
    }
}
