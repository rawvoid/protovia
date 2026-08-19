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

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.itest.model.Audit;
import io.github.rawvoid.protovia.itest.model.Dated;
import io.github.rawvoid.protovia.itest.model.Event;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class AdapterInteropTest {

    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final LocalDate SAMPLE = LocalDate.of(2026, 8, 13);

    @Test
    void protoviaEpochDayZeroIsVisibleToDynamicMessage() throws Exception {
        Descriptors.Descriptor desc = datedDescriptor(false);
        Dated dated = new Dated();
        dated.birthDate = EPOCH;
        byte[] bytes = Protovia.toBytes(dated);
        assertArrayEquals(new byte[]{0x18, 0x00}, bytes);

        DynamicMessage parsed = DynamicMessage.parseFrom(desc, bytes);
        assertEquals(0, parsed.getField(desc.findFieldByName("birthDate")));
    }

    @Test
    void officialImplicitZeroIsProtoviaNull() throws Exception {
        Descriptors.Descriptor desc = datedDescriptor(false);
        DynamicMessage official = DynamicMessage.newBuilder(desc)
            .setField(desc.findFieldByName("birthDate"), 0)
            .build();
        assertEquals(0, official.toByteArray().length);

        Dated back = Protovia.fromBytes(official.toByteArray(), Dated.class);
        assertNull(back.birthDate);
    }

    @Test
    void sampleEpochDayInteropsBothDirections() throws Exception {
        Descriptors.Descriptor desc = datedDescriptor(false);
        Dated dated = new Dated();
        dated.birthDate = SAMPLE;
        DynamicMessage parsed = DynamicMessage.parseFrom(desc, Protovia.toBytes(dated));
        assertEquals(20678, parsed.getField(desc.findFieldByName("birthDate")));

        DynamicMessage official = DynamicMessage.newBuilder(desc)
            .setField(desc.findFieldByName("birthDate"), 20678)
            .build();
        Dated back = Protovia.fromBytes(official.toByteArray(), Dated.class);
        assertEquals(SAMPLE, back.birthDate);
    }

    @Test
    void proto3OptionalZeroRoundTripsPresence() throws Exception {
        Descriptors.Descriptor desc = datedDescriptor(true);
        Dated dated = new Dated();
        dated.birthDate = EPOCH;
        DynamicMessage parsed = DynamicMessage.parseFrom(desc, Protovia.toBytes(dated));
        assertEquals(0, parsed.getField(desc.findFieldByName("birthDate")));
        assertTrue(parsed.hasField(desc.findFieldByName("birthDate")));
    }

    @Test
    void mapOmittedInt32ValueIsEpochDay() throws Exception {
        Descriptors.Descriptor desc = datedMapDescriptor();
        Descriptors.Descriptor entry = desc.findNestedTypeByName("DatesEntry");
        DynamicMessage official = DynamicMessage.newBuilder(desc)
            .addRepeatedField(desc.findFieldByName("dates"),
                DynamicMessage.newBuilder(entry)
                    .setField(entry.findFieldByName("key"), "epoch")
                    .build())
            .build();
        Dated back = Protovia.fromBytes(official.toByteArray(), Dated.class);
        assertEquals(EPOCH, back.dates.get("epoch"));
    }

    @Test
    void uuidMapKeyInteropsWithDynamicMessage() throws Exception {
        Descriptors.Descriptor desc = datedUuidMapDescriptor();
        Descriptors.Descriptor entry = desc.findNestedTypeByName("ByIdEntry");
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        Dated dated = new Dated();
        dated.byId.put(id, SAMPLE);
        DynamicMessage parsed = DynamicMessage.parseFrom(desc, Protovia.toBytes(dated));
        DynamicMessage parsedEntry = (DynamicMessage) parsed.getRepeatedField(
            desc.findFieldByName("byId"), 0);
        assertEquals(id.toString(), parsedEntry.getField(entry.findFieldByName("key")));
        assertEquals(20678, parsedEntry.getField(entry.findFieldByName("value")));

        DynamicMessage official = DynamicMessage.newBuilder(desc)
            .addRepeatedField(desc.findFieldByName("byId"),
                DynamicMessage.newBuilder(entry)
                    .setField(entry.findFieldByName("key"), id.toString())
                    .setField(entry.findFieldByName("value"), 20678)
                    .build())
            .build();
        Dated back = Protovia.fromBytes(official.toByteArray(), Dated.class);
        assertEquals(SAMPLE, back.byId.get(id));
    }

    @Test
    void instantFieldOverrideIsInt64NotTimestamp() throws Exception {
        Descriptors.FileDescriptor fd = auditFile();
        Descriptors.Descriptor desc = fd.findMessageTypeByName("Audit");
        Instant created = Instant.ofEpochMilli(1_600_000_000_000L);
        Instant published = Instant.parse("2020-01-02T03:04:05.006Z");

        Audit audit = new Audit();
        audit.id = "a1";
        audit.created = created;
        audit.published = published;
        DynamicMessage parsed = DynamicMessage.parseFrom(desc, Protovia.toBytes(audit));
        assertEquals("a1", parsed.getField(desc.findFieldByName("id")));
        assertEquals(created.toEpochMilli(), parsed.getField(desc.findFieldByName("created")));
        DynamicMessage ts = (DynamicMessage) parsed.getField(desc.findFieldByName("published"));
        Descriptors.Descriptor tsDesc = ts.getDescriptorForType();
        assertEquals(published.getEpochSecond(), ts.getField(tsDesc.findFieldByName("seconds")));
        assertEquals(published.getNano(), ts.getField(tsDesc.findFieldByName("nanos")));

        DynamicMessage official = DynamicMessage.newBuilder(desc)
            .setField(desc.findFieldByName("created"), created.toEpochMilli())
            .build();
        Audit back = Protovia.fromBytes(official.toByteArray(), Audit.class);
        assertEquals(created, back.created);
        assertNull(back.published);
    }

    @Test
    void classLevelOverrideIsInt64() throws Exception {
        Descriptors.Descriptor desc = eventDescriptor();
        Instant created = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant updated = Instant.ofEpochMilli(1_700_000_100_000L);
        Duration ttl = Duration.ofMillis(2500);
        Event event = new Event();
        event.created = created;
        event.updated = updated;
        event.ttl = ttl;

        DynamicMessage parsed = DynamicMessage.parseFrom(desc, Protovia.toBytes(event));
        assertEquals(created.toEpochMilli(), parsed.getField(desc.findFieldByName("created")));
        assertEquals(updated.toEpochMilli(), parsed.getField(desc.findFieldByName("updated")));
        assertEquals(ttl.toMillis(), parsed.getField(desc.findFieldByName("ttl")));

        DynamicMessage official = DynamicMessage.newBuilder(desc)
            .setField(desc.findFieldByName("created"), created.toEpochMilli())
            .setField(desc.findFieldByName("updated"), updated.toEpochMilli())
            .setField(desc.findFieldByName("ttl"), ttl.toMillis())
            .build();
        Event back = Protovia.fromBytes(official.toByteArray(), Event.class);
        assertEquals(created, back.created);
        assertEquals(updated, back.updated);
        assertEquals(ttl, back.ttl);
    }

    private static Descriptors.Descriptor datedDescriptor(boolean proto3Optional) throws Exception {
        DescriptorProtos.FieldDescriptorProto.Builder birth = field(
            "birthDate", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32);
        if (proto3Optional) {
            birth.setProto3Optional(true);
        }
        return message("Dated", "dated.proto", birth.build());
    }

    private static Descriptors.Descriptor datedMapDescriptor() throws Exception {
        DescriptorProtos.DescriptorProto dated = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("Dated")
            .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("dates")
                .setNumber(6)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(".Dated.DatesEntry"))
            .addNestedType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("DatesEntry")
                .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                .addField(field("key", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("value", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)))
            .build();
        return file("dated_map.proto", dated).findMessageTypeByName("Dated");
    }

    private static Descriptors.Descriptor datedUuidMapDescriptor() throws Exception {
        DescriptorProtos.DescriptorProto dated = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("Dated")
            .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("byId")
                .setNumber(8)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(".Dated.ByIdEntry"))
            .addNestedType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ByIdEntry")
                .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                .addField(field("key", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("value", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)))
            .build();
        return file("dated_uuid.proto", dated).findMessageTypeByName("Dated");
    }

    private static Descriptors.FileDescriptor auditFile() throws Exception {
        DescriptorProtos.DescriptorProto timestamp = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("Timestamp")
            .addField(field("seconds", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64))
            .addField(field("nanos", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
            .build();
        Descriptors.FileDescriptor tsFd = Descriptors.FileDescriptor.buildFrom(
            DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("google/protobuf/timestamp.proto")
                .setPackage("google.protobuf")
                .setSyntax("proto3")
                .addMessageType(timestamp)
                .build(),
            new Descriptors.FileDescriptor[0]);
        DescriptorProtos.DescriptorProto audit = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("Audit")
            .addField(field("id", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
            .addField(field("created", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64))
            .addField(field("published", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(".google.protobuf.Timestamp"))
            .build();
        return Descriptors.FileDescriptor.buildFrom(
            DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("audit.proto")
                .setSyntax("proto3")
                .addDependency("google/protobuf/timestamp.proto")
                .addMessageType(audit)
                .build(),
            new Descriptors.FileDescriptor[]{tsFd});
    }

    private static Descriptors.Descriptor eventDescriptor() throws Exception {
        return message("Event", "event.proto",
            field("created", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64).build(),
            field("updated", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64).build(),
            field("ttl", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64).build());
    }

    private static Descriptors.Descriptor message(
        String name, String fileName, DescriptorProtos.FieldDescriptorProto... fields) throws Exception {
        DescriptorProtos.DescriptorProto.Builder builder = DescriptorProtos.DescriptorProto.newBuilder()
            .setName(name);
        for (DescriptorProtos.FieldDescriptorProto field : fields) {
            builder.addField(field);
        }
        return file(fileName, builder.build()).findMessageTypeByName(name);
    }

    private static Descriptors.FileDescriptor file(
        String fileName, DescriptorProtos.DescriptorProto message) throws Exception {
        return Descriptors.FileDescriptor.buildFrom(
            DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(fileName)
                .setSyntax("proto3")
                .addMessageType(message)
                .build(),
            new Descriptors.FileDescriptor[0]);
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder field(
        String name, int number, DescriptorProtos.FieldDescriptorProto.Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName(name)
            .setNumber(number)
            .setType(type)
            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
    }
}
