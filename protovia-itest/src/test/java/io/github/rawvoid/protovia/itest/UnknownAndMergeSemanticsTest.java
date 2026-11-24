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
import io.github.rawvoid.protovia.UnknownFields;
import io.github.rawvoid.protovia.itest.model.*;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.WireType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@link UnknownFields.Builder} O(N) decode, unknown enum resurrection fixes
 * (wire merge and application-layer mutation), oneof/map submessage merge semantics, and lossless
 * unknown enum handling in map entries.
 *
 * @author Rawvoid
 */
class UnknownAndMergeSemanticsTest {

    // -------------------------------------------------------------------------
    // 1. UnknownFields.Builder O(N) scaling in generated codecs
    // -------------------------------------------------------------------------

    @Test
    void twentyThousandUnknownFieldsDecodeLinearlyAndRoundTripLosslessly() {
        int count = 20_000;
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeString(1, "big");
        for (int i = 0; i < count; i++) {
            wire.writeInt32(100 + (i % 500), i);
        }
        byte[] input = wire.toByteArray();

        long startNanos = System.nanoTime();
        Envelope decoded = Protovia.fromBytes(input, Envelope.class);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertEquals("big", decoded.name);
        assertNotNull(decoded.unknownFields);
        assertFalse(decoded.unknownFields.isEmpty());
        assertTrue(elapsedMs < 5_000, "20,000 unknown fields took too long: " + elapsedMs + " ms");

        byte[] reencoded = Protovia.toBytes(decoded);
        assertEquals(reencoded.length, Protovia.sizeOf(decoded));
        assertArrayEquals(input, reencoded);
    }

    @Test
    void untouchedMergePreservesUnknownFieldsReferenceWithoutCopying() {
        ProtoWriter unknownWire = ProtoWriter.growing();
        unknownWire.writeInt32(99, 12345);
        Envelope initial = Protovia.fromBytes(unknownWire.toByteArray(), Envelope.class);
        UnknownFields originalUnknowns = initial.unknownFields;
        assertNotNull(originalUnknowns);
        assertFalse(originalUnknowns.isEmpty());

        ProtoWriter update = ProtoWriter.growing();
        update.writeString(1, "updated");
        Envelope merged = Protovia.mergeFrom(update.toByteArray(), initial);
        assertEquals("updated", merged.name);
        assertSame(originalUnknowns, merged.unknownFields);
    }

    // -------------------------------------------------------------------------
    // 2. Unknown enum resurrection — wire level
    // -------------------------------------------------------------------------

    @Test
    void singularEnumUnknownFollowedByKnownOnWireStripsUnknown() {
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(7, 99);
        wire.writeInt32(15, 42);
        wire.writeEnum(7, 1); // Status.ACTIVE
        Envelope decoded = Protovia.fromBytes(wire.toByteArray(), Envelope.class);

        assertEquals(Status.ACTIVE, decoded.status);
        assertEquals(List.of(1), readAllEnumsAt(Protovia.toBytes(decoded), 7));
        assertEquals(42, readIntAt(Protovia.toBytes(decoded), 15));
        assertEquals(Protovia.toBytes(decoded).length, Protovia.sizeOf(decoded));

        // Unknown followed by zero (Status.UNKNOWN) must also strip 99 and leave empty wire for field 7.
        ProtoWriter toZero = ProtoWriter.growing();
        toZero.writeEnum(7, 99);
        toZero.writeEnum(7, 0);
        Envelope zeroDecoded = Protovia.fromBytes(toZero.toByteArray(), Envelope.class);
        assertEquals(Status.UNKNOWN, zeroDecoded.status);
        assertTrue(zeroDecoded.unknownFields.isEmpty());
        assertEquals(0, Protovia.sizeOf(zeroDecoded));
        assertEquals(0, Protovia.toBytes(zeroDecoded).length);
    }

    @Test
    void oneofUnknownEnumFollowedByAnotherBranchOrKnownEnumOnWireStripsUnknown() {
        // Closed enum oneof (KindPicker): TAG_10 = 99 then TAG_11 = "hi".
        ProtoWriter closedSwitch = ProtoWriter.growing();
        closedSwitch.writeEnum(10, 99);
        closedSwitch.writeString(11, "hi");
        KindPicker kindDecoded = Protovia.fromBytes(closedSwitch.toByteArray(), KindPicker.class);
        assertEquals(new KindPicker.KindLabel("hi"), kindDecoded.choice);
        assertTrue(kindDecoded.unknownFields.isEmpty());
        byte[] kindReencoded = Protovia.toBytes(kindDecoded);
        assertEquals(kindReencoded.length, Protovia.sizeOf(kindDecoded));
        assertTrue(readAllEnumsAt(kindReencoded, 10).isEmpty());
        assertEquals(new KindPicker.KindLabel("hi"), Protovia.fromBytes(kindReencoded, KindPicker.class).choice);

        // Closed enum oneof: TAG_10 = 99 then TAG_10 = 1 (Kind.ALPHA).
        ProtoWriter closedKnown = ProtoWriter.growing();
        closedKnown.writeEnum(10, 99);
        closedKnown.writeEnum(10, 1);
        KindPicker kindKnown = Protovia.fromBytes(closedKnown.toByteArray(), KindPicker.class);
        assertEquals(new KindPicker.KindPick(Kind.ALPHA), kindKnown.choice);
        assertTrue(kindKnown.unknownFields.isEmpty());
        assertEquals(List.of(1), readAllEnumsAt(Protovia.toBytes(kindKnown), 10));

        // Open enum oneof (PickerEnvelope): TAG_10 = 99 then TAG_11 = "hi".
        ProtoWriter openSwitch = ProtoWriter.growing();
        openSwitch.writeEnum(10, 99);
        openSwitch.writeString(11, "hi");
        PickerEnvelope pickerDecoded = Protovia.fromBytes(openSwitch.toByteArray(), PickerEnvelope.class);
        assertEquals(new Picker.NamePick("hi"), pickerDecoded.choice);
        assertTrue(pickerDecoded.unknownFields.isEmpty());
        assertTrue(readAllEnumsAt(Protovia.toBytes(pickerDecoded), 10).isEmpty());

        // Multi-enum oneof: 4 unknown enum tags (10, 11, 12, 13) then empty record (14).
        ProtoWriter multi = ProtoWriter.growing();
        multi.writeEnum(10, 99);
        multi.writeEnum(11, 88);
        multi.writeEnum(12, 77);
        multi.writeEnum(13, 66);
        multi.writeUInt32NoTag(WireType.tag(14, WireType.LEN));
        multi.writeUInt32NoTag(0);
        MultiEnumOneofEnvelope multiDecoded = Protovia.fromBytes(multi.toByteArray(), MultiEnumOneofEnvelope.class);
        assertEquals(new MultiEnumOneofEnvelope.EmptyBranch(), multiDecoded.choice);
        assertTrue(multiDecoded.unknownFields.isEmpty());
        byte[] multiReencoded = Protovia.toBytes(multiDecoded);
        assertEquals(multiReencoded.length, Protovia.sizeOf(multiDecoded));
        assertTrue(readAllEnumsAt(multiReencoded, 10).isEmpty());
        assertTrue(readAllEnumsAt(multiReencoded, 11).isEmpty());
        assertTrue(readAllEnumsAt(multiReencoded, 12).isEmpty());
        assertTrue(readAllEnumsAt(multiReencoded, 13).isEmpty());
    }

    @Test
    void recordOptionalEnumWireMergeStripsUnknown() {
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(2, 99);
        wire.writeEnum(3, 88);
        wire.writeEnum(2, 1); // Status.ACTIVE
        wire.writeEnum(3, 1); // Kind.ALPHA
        OptionalMessageEnumRecord decoded = Protovia.fromBytes(wire.toByteArray(), OptionalMessageEnumRecord.class);
        assertEquals(Optional.of(Status.ACTIVE), decoded.status());
        assertEquals(Optional.of(Kind.ALPHA), decoded.kind());
        assertTrue(decoded.unknown().isEmpty());
        byte[] bytes = Protovia.toBytes(decoded);
        assertEquals(bytes.length, Protovia.sizeOf(decoded));
        assertEquals(List.of(1), readAllEnumsAt(bytes, 2));
        assertEquals(List.of(1), readAllEnumsAt(bytes, 3));
    }

    @Test
    void closedEnumKnownFollowedByUnknownAndDefaultInitPreservesUnknown() {
        // (a) Optional<Kind> POJO: TAG_3 = 1 then TAG_3 = 99 preserves 3=99 and drops stale 3=1.
        ProtoWriter optWire = ProtoWriter.growing();
        optWire.writeEnum(3, 1);
        optWire.writeEnum(3, 99);
        OptionalMessageEnumPojo optDecoded = Protovia.fromBytes(optWire.toByteArray(), OptionalMessageEnumPojo.class);
        assertEquals(Optional.empty(), optDecoded.kind);
        byte[] optBytes = Protovia.toBytes(optDecoded);
        assertEquals(optBytes.length, Protovia.sizeOf(optDecoded));
        assertEquals(List.of(99), readAllEnumsAt(optBytes, 3));

        // Optional<Kind> POJO mergeFrom: existing Optional.of(ALPHA) + TAG_3 = 99 clears field & preserves 3=99.
        ProtoWriter onlyUnknown3 = ProtoWriter.growing();
        onlyUnknown3.writeEnum(3, 99);
        byte[] onlyUnknown3Bytes = onlyUnknown3.toByteArray();

        OptionalMessageEnumPojo optExisting = new OptionalMessageEnumPojo();
        optExisting.kind = Optional.of(Kind.ALPHA);
        OptionalMessageEnumPojo optMerged = Protovia.mergeFrom(onlyUnknown3Bytes, optExisting);
        assertEquals(Optional.empty(), optMerged.kind);
        byte[] optMergedBytes = Protovia.toBytes(optMerged);
        assertEquals(optMergedBytes.length, Protovia.sizeOf(optMerged));
        assertEquals(List.of(99), readAllEnumsAt(optMergedBytes, 3));

        // Optional<Kind> record: TAG_3 = 1 then TAG_3 = 99, and mergeFrom existing Optional.of(ALPHA).
        OptionalMessageEnumRecord recDecoded = Protovia.fromBytes(optWire.toByteArray(), OptionalMessageEnumRecord.class);
        assertEquals(Optional.empty(), recDecoded.kind());
        byte[] recBytes = Protovia.toBytes(recDecoded);
        assertEquals(recBytes.length, Protovia.sizeOf(recDecoded));
        assertEquals(List.of(99), readAllEnumsAt(recBytes, 3));

        OptionalMessageEnumRecord recExisting = new OptionalMessageEnumRecord(
            Optional.empty(), Optional.empty(), Optional.of(Kind.ALPHA), Optional.empty(), UnknownFields.EMPTY);
        OptionalMessageEnumRecord recMerged = Protovia.mergeFrom(onlyUnknown3Bytes, recExisting);
        assertEquals(Optional.empty(), recMerged.kind());
        byte[] recMergedBytes = Protovia.toBytes(recMerged);
        assertEquals(recMergedBytes.length, Protovia.sizeOf(recMerged));
        assertEquals(List.of(99), readAllEnumsAt(recMergedBytes, 3));

        // (b) Plain null-initialized Kind (field 1): TAG_1 = 1 then TAG_1 = 99 and mergeFrom.
        ProtoWriter plainWire = ProtoWriter.growing();
        plainWire.writeEnum(1, 1);
        plainWire.writeEnum(1, 99);
        ClosedEnumEnvelope plainDecoded = Protovia.fromBytes(plainWire.toByteArray(), ClosedEnumEnvelope.class);
        assertNull(plainDecoded.plain);
        byte[] plainBytes = Protovia.toBytes(plainDecoded);
        assertEquals(plainBytes.length, Protovia.sizeOf(plainDecoded));
        assertEquals(List.of(99), readAllEnumsAt(plainBytes, 1));

        ProtoWriter onlyUnknown1 = ProtoWriter.growing();
        onlyUnknown1.writeEnum(1, 99);
        ClosedEnumEnvelope plainExisting = new ClosedEnumEnvelope();
        plainExisting.plain = Kind.ALPHA;
        ClosedEnumEnvelope plainMerged = Protovia.mergeFrom(onlyUnknown1.toByteArray(), plainExisting);
        assertNull(plainMerged.plain);
        byte[] plainMergedBytes = Protovia.toBytes(plainMerged);
        assertEquals(plainMergedBytes.length, Protovia.sizeOf(plainMerged));
        assertEquals(List.of(99), readAllEnumsAt(plainMergedBytes, 1));

        // (c) Default-initialized POJO (`public Kind kind = Kind.UNSPECIFIED;`):
        // TAG_3 = 1 then TAG_3 = 99 and mergeFrom preserve 3=99 and drop stale 3=1.
        ClosedEnumEnvelope defSeq = Protovia.fromBytes(optWire.toByteArray(), ClosedEnumEnvelope.class);
        assertNull(defSeq.kind);
        byte[] defSeqBytes = Protovia.toBytes(defSeq);
        assertEquals(defSeqBytes.length, Protovia.sizeOf(defSeq));
        assertEquals(List.of(99), readAllEnumsAt(defSeqBytes, 3));

        ClosedEnumEnvelope defExisting = new ClosedEnumEnvelope();
        defExisting.kind = Kind.ALPHA;
        ClosedEnumEnvelope defMerged = Protovia.mergeFrom(onlyUnknown3Bytes, defExisting);
        assertNull(defMerged.kind);
        byte[] defMergedBytes = Protovia.toBytes(defMerged);
        assertEquals(defMergedBytes.length, Protovia.sizeOf(defMerged));
        assertEquals(List.of(99), readAllEnumsAt(defMergedBytes, 3));

        // Default-initialized POJO receiving TAG_3 = 99 directly preserves 3=99 on untouched toBytes(),
        // and mutating msg.kind = Kind.UNSPECIFIED afterward suppresses 3=99.
        ProtoWriter withExtra = ProtoWriter.growing();
        withExtra.writeEnum(3, 99);
        withExtra.writeInt32(15, 42);
        ClosedEnumEnvelope defDirect = Protovia.fromBytes(withExtra.toByteArray(), ClosedEnumEnvelope.class);
        assertNull(defDirect.kind);
        byte[] defDirectUntouched = Protovia.toBytes(defDirect);
        assertEquals(defDirectUntouched.length, Protovia.sizeOf(defDirect));
        assertEquals(List.of(99), readAllEnumsAt(defDirectUntouched, 3));
        assertEquals(42, readIntAt(defDirectUntouched, 15));

        defDirect.kind = Kind.UNSPECIFIED;
        byte[] defSuppressed = Protovia.toBytes(defDirect);
        assertEquals(defSuppressed.length, Protovia.sizeOf(defDirect));
        assertTrue(readAllEnumsAt(defSuppressed, 3).isEmpty());
        assertEquals(42, readIntAt(defSuppressed, 15));

        // Without unrelated unknown fields, mutating back to Kind.UNSPECIFIED produces 0 bytes.
        ClosedEnumEnvelope defOnly = Protovia.fromBytes(onlyUnknown3Bytes, ClosedEnumEnvelope.class);
        assertNull(defOnly.kind);
        assertEquals(List.of(99), readAllEnumsAt(Protovia.toBytes(defOnly), 3));
        defOnly.kind = Kind.UNSPECIFIED;
        assertEquals(0, Protovia.sizeOf(defOnly));
        assertEquals(0, Protovia.toBytes(defOnly).length);
    }

    @Test
    void closedEnumOneofKnownBranchFollowedByUnknownPreservesUnknownAndDropsStaleBranch() {
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeString(11, "hi");
        wire.writeEnum(10, 99);

        KindPicker decoded = Protovia.fromBytes(wire.toByteArray(), KindPicker.class);
        assertNull(decoded.choice);
        assertFalse(decoded.unknownFields.isEmpty());
        byte[] bytes = Protovia.toBytes(decoded);
        assertEquals(bytes.length, Protovia.sizeOf(decoded));
        assertEquals(List.of(99), readAllEnumsAt(bytes, 10));
        assertNull(readStringAt(bytes, 11));

        // mergeFrom into an existing KindPicker holding KindLabel("hi") also clears choice and preserves 10=99.
        ProtoWriter onlyUnknown10 = ProtoWriter.growing();
        onlyUnknown10.writeEnum(10, 99);
        KindPicker existing = new KindPicker();
        existing.choice = new KindPicker.KindLabel("hi");
        KindPicker merged = Protovia.mergeFrom(onlyUnknown10.toByteArray(), existing);
        assertNull(merged.choice);
        byte[] mergedBytes = Protovia.toBytes(merged);
        assertEquals(mergedBytes.length, Protovia.sizeOf(merged));
        assertEquals(List.of(99), readAllEnumsAt(mergedBytes, 10));
        assertNull(readStringAt(mergedBytes, 11));

        // Without @ProtoUnknown, stale branch is still cleared rather than resurrected.
        KindPickerBare bare = Protovia.fromBytes(wire.toByteArray(), KindPickerBare.class);
        assertNull(bare.choice);
        assertEquals(0, Protovia.sizeOf(bare));
        assertEquals(0, Protovia.toBytes(bare).length);
    }

    @Test
    void multiEnumOneofSwitchingBetweenUnknownEnumBranchesPreservesOnlyWinningBranch() {
        // Open wrapped (10=99) followed by open naked (12=77) -> only 12=77.
        ProtoWriter openToOpen = ProtoWriter.growing();
        openToOpen.writeEnum(10, 99);
        openToOpen.writeEnum(12, 77);
        MultiEnumOneofEnvelope d1 = Protovia.fromBytes(openToOpen.toByteArray(), MultiEnumOneofEnvelope.class);
        assertEquals(Status.UNRECOGNIZED, d1.choice);
        byte[] b1 = Protovia.toBytes(d1);
        assertEquals(b1.length, Protovia.sizeOf(d1));
        assertTrue(readAllEnumsAt(b1, 10).isEmpty());
        assertTrue(readAllEnumsAt(b1, 11).isEmpty());
        assertEquals(List.of(77), readAllEnumsAt(b1, 12));
        assertTrue(readAllEnumsAt(b1, 13).isEmpty());

        // Open wrapped (10=99) followed by closed wrapped (11=88) -> choice cleared, only 11=88.
        ProtoWriter openToClosed = ProtoWriter.growing();
        openToClosed.writeEnum(10, 99);
        openToClosed.writeEnum(11, 88);
        MultiEnumOneofEnvelope d2 = Protovia.fromBytes(openToClosed.toByteArray(), MultiEnumOneofEnvelope.class);
        assertNull(d2.choice);
        byte[] b2 = Protovia.toBytes(d2);
        assertEquals(b2.length, Protovia.sizeOf(d2));
        assertTrue(readAllEnumsAt(b2, 10).isEmpty());
        assertEquals(List.of(88), readAllEnumsAt(b2, 11));
        assertTrue(readAllEnumsAt(b2, 12).isEmpty());
        assertTrue(readAllEnumsAt(b2, 13).isEmpty());

        // Closed wrapped (11=88) followed by closed naked (13=66) -> only 13=66.
        ProtoWriter closedToClosed = ProtoWriter.growing();
        closedToClosed.writeEnum(11, 88);
        closedToClosed.writeEnum(13, 66);
        MultiEnumOneofEnvelope d3 = Protovia.fromBytes(closedToClosed.toByteArray(), MultiEnumOneofEnvelope.class);
        assertNull(d3.choice);
        byte[] b3 = Protovia.toBytes(d3);
        assertEquals(b3.length, Protovia.sizeOf(d3));
        assertTrue(readAllEnumsAt(b3, 10).isEmpty());
        assertTrue(readAllEnumsAt(b3, 11).isEmpty());
        assertTrue(readAllEnumsAt(b3, 12).isEmpty());
        assertEquals(List.of(66), readAllEnumsAt(b3, 13));

        // mergeFrom: 10=99 then mergeFrom(12=77) strips 10=99 and keeps only 12=77.
        ProtoWriter first10 = ProtoWriter.growing();
        first10.writeEnum(10, 99);
        ProtoWriter next12 = ProtoWriter.growing();
        next12.writeEnum(12, 77);
        MultiEnumOneofEnvelope initial = Protovia.fromBytes(first10.toByteArray(), MultiEnumOneofEnvelope.class);
        MultiEnumOneofEnvelope merged = Protovia.mergeFrom(next12.toByteArray(), initial);
        assertEquals(Status.UNRECOGNIZED, merged.choice);
        byte[] mergedBytes = Protovia.toBytes(merged);
        assertEquals(mergedBytes.length, Protovia.sizeOf(merged));
        assertTrue(readAllEnumsAt(mergedBytes, 10).isEmpty());
        assertEquals(List.of(77), readAllEnumsAt(mergedBytes, 12));
    }

    // -------------------------------------------------------------------------
    // 3. Unknown enum resurrection — application-layer mutation
    // -------------------------------------------------------------------------

    @Test
    void singularEnumApplicationMutationSuppressesUnknownOnReencode() {
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(7, 99);
        wire.writeInt32(15, 42);
        Envelope env = Protovia.fromBytes(wire.toByteArray(), Envelope.class);
        assertEquals(Status.UNRECOGNIZED, env.status);
        assertFalse(env.unknownFields.isEmpty());

        // Negative check: untouched UNRECOGNIZED preserves 99 and unrelated unknown 15=42.
        byte[] untouched = Protovia.toBytes(env);
        assertEquals(untouched.length, Protovia.sizeOf(env));
        assertEquals(List.of(99), readAllEnumsAt(untouched, 7));
        assertEquals(42, readIntAt(untouched, 15));

        // Mutate to ACTIVE -> only 1 is written for field 7 (NO 99!), 15=42 preserved.
        env.status = Status.ACTIVE;
        byte[] activeBytes = Protovia.toBytes(env);
        assertEquals(activeBytes.length, Protovia.sizeOf(env));
        assertEquals(List.of(1), readAllEnumsAt(activeBytes, 7));
        assertEquals(42, readIntAt(activeBytes, 15));
        Envelope activeBack = Protovia.fromBytes(activeBytes, Envelope.class);
        assertEquals(Status.ACTIVE, activeBack.status);

        // Mutate to UNKNOWN (wire default 0) -> NO tag 7 at all (NO 99!), 15=42 preserved.
        env.status = Status.UNKNOWN;
        byte[] unknownZeroBytes = Protovia.toBytes(env);
        assertEquals(unknownZeroBytes.length, Protovia.sizeOf(env));
        assertTrue(readAllEnumsAt(unknownZeroBytes, 7).isEmpty());
        assertEquals(42, readIntAt(unknownZeroBytes, 15));
        Envelope zeroBack = Protovia.fromBytes(unknownZeroBytes, Envelope.class);
        assertNull(zeroBack.status); // field 7 absent on wire -> Java default (NOT UNRECOGNIZED / 99)
        Envelope defaultTarget = new Envelope();
        defaultTarget.status = Status.UNKNOWN;
        Protovia.mergeFrom(unknownZeroBytes, defaultTarget);
        assertEquals(Status.UNKNOWN, defaultTarget.status);

        // Without unrelated unknown fields, mutating to UNKNOWN yields 0 bytes.
        ProtoWriter onlyUnknownEnum = ProtoWriter.growing();
        onlyUnknownEnum.writeEnum(7, 99);
        Envelope env2 = Protovia.fromBytes(onlyUnknownEnum.toByteArray(), Envelope.class);
        env2.status = Status.UNKNOWN;
        assertEquals(0, Protovia.sizeOf(env2));
        assertEquals(0, Protovia.toBytes(env2).length);
    }

    @Test
    void oneofApplicationMutationSuppressesUnknownOnReencode() {
        // PickerEnvelope (open enum wrapper): decode TAG_10 = 99.
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(10, 99);
        PickerEnvelope env = Protovia.fromBytes(wire.toByteArray(), PickerEnvelope.class);
        assertEquals(new Picker.StatusPick(Status.UNRECOGNIZED), env.choice);
        assertFalse(env.unknownFields.isEmpty());

        // Untouched UNRECOGNIZED preserves 99.
        byte[] untouched = Protovia.toBytes(env);
        assertEquals(untouched.length, Protovia.sizeOf(env));
        assertEquals(List.of(99), readAllEnumsAt(untouched, 10));

        // Mutate to NamePick("changed") -> ONLY TAG_11 = "changed", NO TAG_10 = 99.
        env.choice = new Picker.NamePick("changed");
        byte[] nameBytes = Protovia.toBytes(env);
        assertEquals(nameBytes.length, Protovia.sizeOf(env));
        assertTrue(readAllEnumsAt(nameBytes, 10).isEmpty());
        PickerEnvelope nameBack = Protovia.fromBytes(nameBytes, PickerEnvelope.class);
        assertEquals(new Picker.NamePick("changed"), nameBack.choice);
        assertTrue(nameBack.unknownFields.isEmpty());

        // Mutate to StatusPick(ACTIVE) -> ONLY TAG_10 = 1, NO 99.
        env.choice = new Picker.StatusPick(Status.ACTIVE);
        byte[] activeBytes = Protovia.toBytes(env);
        assertEquals(activeBytes.length, Protovia.sizeOf(env));
        assertEquals(List.of(1), readAllEnumsAt(activeBytes, 10));
        assertEquals(new Picker.StatusPick(Status.ACTIVE), Protovia.fromBytes(activeBytes, PickerEnvelope.class).choice);

        // Mutate to StatusPick(UNKNOWN) -> ONLY TAG_10 = 0, NO 99.
        env.choice = new Picker.StatusPick(Status.UNKNOWN);
        byte[] zeroBytes = Protovia.toBytes(env);
        assertEquals(zeroBytes.length, Protovia.sizeOf(env));
        assertEquals(List.of(0), readAllEnumsAt(zeroBytes, 10));

        // KindPicker (closed enum wrapper): decode TAG_10 = 99 -> choice is null.
        KindPicker kindEnv = Protovia.fromBytes(wire.toByteArray(), KindPicker.class);
        assertNull(kindEnv.choice);
        assertEquals(List.of(99), readAllEnumsAt(Protovia.toBytes(kindEnv), 10));

        kindEnv.choice = new KindPicker.KindLabel("changed");
        byte[] kindLabelBytes = Protovia.toBytes(kindEnv);
        assertEquals(kindLabelBytes.length, Protovia.sizeOf(kindEnv));
        assertTrue(readAllEnumsAt(kindLabelBytes, 10).isEmpty());
        assertEquals(new KindPicker.KindLabel("changed"), Protovia.fromBytes(kindLabelBytes, KindPicker.class).choice);

        kindEnv.choice = new KindPicker.KindPick(Kind.ALPHA);
        byte[] kindAlphaBytes = Protovia.toBytes(kindEnv);
        assertEquals(kindAlphaBytes.length, Protovia.sizeOf(kindEnv));
        assertEquals(List.of(1), readAllEnumsAt(kindAlphaBytes, 10));

        // MultiEnumOneofEnvelope (naked open & closed enums): decode TAG_12 = 99 (naked Status).
        ProtoWriter nakedWire = ProtoWriter.growing();
        nakedWire.writeEnum(12, 99);
        MultiEnumOneofEnvelope multi = Protovia.fromBytes(nakedWire.toByteArray(), MultiEnumOneofEnvelope.class);
        assertEquals(Status.UNRECOGNIZED, multi.choice);
        assertEquals(List.of(99), readAllEnumsAt(Protovia.toBytes(multi), 12));

        multi.choice = Status.ACTIVE;
        byte[] nakedActive = Protovia.toBytes(multi);
        assertEquals(nakedActive.length, Protovia.sizeOf(multi));
        assertEquals(List.of(1), readAllEnumsAt(nakedActive, 12));

        multi.choice = Kind.ALPHA;
        byte[] nakedClosed = Protovia.toBytes(multi);
        assertEquals(nakedClosed.length, Protovia.sizeOf(multi));
        assertTrue(readAllEnumsAt(nakedClosed, 12).isEmpty());
        assertEquals(List.of(1), readAllEnumsAt(nakedClosed, 13));
    }

    @Test
    void optionalEnumApplicationMutationSuppressesUnknownOnReencode() {
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(2, 99);
        wire.writeEnum(3, 88);
        OptionalMessageEnumPojo pojo = Protovia.fromBytes(wire.toByteArray(), OptionalMessageEnumPojo.class);
        assertEquals(Optional.of(Status.UNRECOGNIZED), pojo.status);
        assertEquals(Optional.empty(), pojo.kind);

        // Untouched preserves both 2=99 and 3=88.
        byte[] untouched = Protovia.toBytes(pojo);
        assertEquals(untouched.length, Protovia.sizeOf(pojo));
        assertEquals(List.of(99), readAllEnumsAt(untouched, 2));
        assertEquals(List.of(88), readAllEnumsAt(untouched, 3));

        // Mutating both to zero enum values suppresses 99 and 88 and writes explicit zero presence.
        pojo.status = Optional.of(Status.UNKNOWN);
        pojo.kind = Optional.of(Kind.UNSPECIFIED);
        byte[] mutated = Protovia.toBytes(pojo);
        assertEquals(mutated.length, Protovia.sizeOf(pojo));
        assertEquals(List.of(0), readAllEnumsAt(mutated, 2));
        assertEquals(List.of(0), readAllEnumsAt(mutated, 3));
    }

    @Test
    void repeatedUnknownEnumRemainsPreservedAlongsideKnownValues() {
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(1, 1);
        wire.writeEnum(1, 99);
        wire.writeEnum(1, 2);
        Flags decoded = Protovia.fromBytes(wire.toByteArray(), Flags.class);
        assertEquals(List.of(Status.ACTIVE, Status.BANNED), decoded.flags);
        assertFalse(decoded.unknownFields.isEmpty());

        byte[] reencoded = Protovia.toBytes(decoded);
        assertEquals(reencoded.length, Protovia.sizeOf(decoded));
        Flags again = Protovia.fromBytes(reencoded, Flags.class);
        assertEquals(List.of(Status.ACTIVE, Status.BANNED), again.flags);
        assertEquals(List.of(99), readAllEnumsAt(reencoded, 1));
    }

    // -------------------------------------------------------------------------
    // 4. Oneof same-case submessage merge & cross-case replace
    // -------------------------------------------------------------------------

    @Test
    void oneofWrappedMessageMergesSameCaseAndReplacesAcrossCases() {
        Contact first = new Contact();
        first.target = new Home(new Address("Paris", null));
        Contact second = new Contact();
        second.target = new Home(new Address(null, "Rue"));

        // Concatenated wire fragments merge same-case submessage.
        Contact mergedFromWire = Protovia.fromBytes(concat(Protovia.toBytes(first), Protovia.toBytes(second)), Contact.class);
        assertEquals(new Home(new Address("Paris", "Rue")), mergedFromWire.target);

        // Protovia.mergeFrom into existing Contact merges same-case submessage.
        Contact existing = new Contact();
        existing.target = new Home(new Address("Paris", null));
        Contact mergedExisting = Protovia.mergeFrom(Protovia.toBytes(second), existing);
        assertEquals(new Home(new Address("Paris", "Rue")), mergedExisting.target);

        // Record oneof merges same-case submessage on wire and via mergeFrom.
        ApiRecordRS<Target> recFirst = new ApiRecordRS<>(true, new Home(new Address("Paris", null)));
        ApiRecordRS<Target> recSecond = new ApiRecordRS<>(false, new Home(new Address(null, "Rue")));
        ApiRecordRS<?> recWireMerged = Protovia.fromBytes(
            concat(Protovia.toBytes(recFirst), Protovia.toBytes(recSecond)), ApiRecordRS.class);
        assertEquals(new Home(new Address("Paris", "Rue")), recWireMerged.data());
        ApiRecordRS<Target> recExistingMerged = Protovia.mergeFrom(Protovia.toBytes(recSecond), recFirst);
        assertEquals(new Home(new Address("Paris", "Rue")), recExistingMerged.data());

        // Switching Home -> Email -> Home must NOT leak "Paris" from earlier Home.
        Contact middle = new Contact();
        middle.target = new Email("a@b.c");
        Contact third = new Contact();
        third.target = new Home(new Address(null, "Lyon"));
        byte[] switched = concat(concat(Protovia.toBytes(first), Protovia.toBytes(middle)), Protovia.toBytes(third));
        Contact switchedBack = Protovia.fromBytes(switched, Contact.class);
        assertEquals(new Home(new Address(null, "Lyon")), switchedBack.target);
    }

    @Test
    void oneofSelfMessageMergesSameCaseAndReplacesAcrossCases() {
        Bag first = new Bag();
        first.data = new Address("Paris", null);
        Bag second = new Bag();
        second.data = new Address(null, "Rue");

        Bag mergedFromWire = Protovia.fromBytes(concat(Protovia.toBytes(first), Protovia.toBytes(second)), Bag.class);
        assertEquals(new Address("Paris", "Rue"), mergedFromWire.data);

        Bag existing = new Bag();
        existing.data = new Address("Paris", null);
        Bag mergedExisting = Protovia.mergeFrom(Protovia.toBytes(second), existing);
        assertEquals(new Address("Paris", "Rue"), mergedExisting.data);

        // Switching Address -> String -> Address must NOT leak "Paris".
        Bag middle = new Bag();
        middle.data = "reset";
        Bag third = new Bag();
        third.data = new Address(null, "Lyon");
        byte[] switched = concat(concat(Protovia.toBytes(first), Protovia.toBytes(middle)), Protovia.toBytes(third));
        Bag switchedBack = Protovia.fromBytes(switched, Bag.class);
        assertEquals(new Address(null, "Lyon"), switchedBack.data);
    }

    // -------------------------------------------------------------------------
    // 5. Map duplicate message value merge
    // -------------------------------------------------------------------------

    @Test
    void mapDuplicateMessageValueInSingleEntryMergesSubmessage() {
        byte[] addrPart1 = Protovia.toBytes(new Address("Paris", null));
        byte[] addrPart2 = Protovia.toBytes(new Address(null, "Rue"));

        ProtoWriter entry = ProtoWriter.growing();
        entry.writeString(1, "k");
        entry.writeUInt32NoTag(WireType.tag(2, WireType.LEN));
        entry.writeUInt32NoTag(addrPart1.length);
        entry.writeRawBytes(addrPart1, 0, addrPart1.length);
        entry.writeUInt32NoTag(WireType.tag(2, WireType.LEN));
        entry.writeUInt32NoTag(addrPart2.length);
        entry.writeRawBytes(addrPart2, 0, addrPart2.length);
        byte[] entryBytes = entry.toByteArray();

        ProtoWriter wire = ProtoWriter.growing();
        wire.writeUInt32NoTag(WireType.tag(3, WireType.LEN));
        wire.writeUInt32NoTag(entryBytes.length);
        wire.writeRawBytes(entryBytes, 0, entryBytes.length);

        EnumMapEnvelope decoded = Protovia.fromBytes(wire.toByteArray(), EnumMapEnvelope.class);
        assertEquals(Map.of("k", new Address("Paris", "Rue")), decoded.addresses);
    }

    @Test
    void mapSeparateEntriesForSameKeyReplaceMessageValueRatherThanMerge() {
        byte[] addrPart1 = Protovia.toBytes(new Address("Paris", null));
        byte[] addrPart2 = Protovia.toBytes(new Address(null, "Rue"));

        ProtoWriter entry1 = ProtoWriter.growing();
        entry1.writeString(1, "k");
        writeLenField(entry1, 2, addrPart1);

        ProtoWriter entry2 = ProtoWriter.growing();
        entry2.writeString(1, "k");
        writeLenField(entry2, 2, addrPart2);

        ProtoWriter wire = ProtoWriter.growing();
        writeLenField(wire, 3, entry1.toByteArray());
        writeLenField(wire, 3, entry2.toByteArray());

        EnumMapEnvelope decoded = Protovia.fromBytes(wire.toByteArray(), EnumMapEnvelope.class);
        assertEquals(Map.of("k", new Address(null, "Rue")), decoded.addresses);

        // mergeFrom with a separate entry for the same key also replaces rather than merges.
        EnumMapEnvelope existing = new EnumMapEnvelope();
        existing.addresses = new LinkedHashMap<>();
        existing.addresses.put("k", new Address("Paris", "Old"));
        ProtoWriter delta = ProtoWriter.growing();
        writeLenField(delta, 3, entry2.toByteArray());
        EnumMapEnvelope merged = Protovia.mergeFrom(delta.toByteArray(), existing);
        assertEquals(Map.of("k", new Address(null, "Rue")), merged.addresses);
    }

    // -------------------------------------------------------------------------
    // 6. Map unknown enum value handling (with and without @ProtoUnknown)
    // -------------------------------------------------------------------------

    @Test
    void mapUnknownEnumWithUnknownSlotPreservesEntryLosslesslyWithoutCorruption() {
        ProtoWriter wire = ProtoWriter.growing();
        writeStringEnumEntry(wire, 1, "good", 1);      // Status.ACTIVE
        writeStringEnumEntry(wire, 1, "future", 99);   // unknown Status
        writeStringEnumEntry(wire, 1, "neg", -5);      // negative unknown Status
        writeStringEnumEntry(wire, 2, "ok", 1);        // Kind.ALPHA
        writeStringEnumEntry(wire, 2, "futureK", 99);  // unknown closed Kind

        EnumMapEnvelope decoded = Protovia.fromBytes(wire.toByteArray(), EnumMapEnvelope.class);
        assertEquals(Map.of("good", Status.ACTIVE), decoded.statuses);
        assertFalse(decoded.statuses.containsKey("future"));
        assertFalse(decoded.statuses.containsKey("neg"));
        assertEquals(Map.of("ok", Kind.ALPHA), decoded.kinds);
        assertFalse(decoded.kinds.containsKey("futureK"));
        assertFalse(decoded.unknownFields.isEmpty());

        byte[] reencoded = Protovia.toBytes(decoded);
        assertEquals(reencoded.length, Protovia.sizeOf(decoded));

        // Inspect raw wire map entries to verify lossless preservation of unknown entries.
        Map<String, Integer> rawStatuses = readRawStringEnumMap(reencoded, 1);
        assertEquals(1, rawStatuses.get("good"));
        assertEquals(99, rawStatuses.get("future"));
        assertEquals(-5, rawStatuses.get("neg"));

        Map<String, Integer> rawKinds = readRawStringEnumMap(reencoded, 2);
        assertEquals(1, rawKinds.get("ok"));
        assertEquals(99, rawKinds.get("futureK"));

        // Re-decoding preserves both known map entries and unknown entries again.
        EnumMapEnvelope again = Protovia.fromBytes(reencoded, EnumMapEnvelope.class);
        assertEquals(Map.of("good", Status.ACTIVE), again.statuses);
        assertEquals(Map.of("ok", Kind.ALPHA), again.kinds);
        assertEquals(decoded.unknownFields, again.unknownFields);
    }

    @Test
    void mapUnknownEnumWithoutUnknownSlotDropsEntryWithoutCorruption() {
        ProtoWriter wire = ProtoWriter.growing();
        writeStringEnumEntry(wire, 1, "good", 1);
        writeStringEnumEntry(wire, 1, "future", 99);
        writeStringEnumEntry(wire, 2, "ok", 1);
        writeStringEnumEntry(wire, 2, "futureK", 99);

        EnumMapBare decoded = Protovia.fromBytes(wire.toByteArray(), EnumMapBare.class);
        assertEquals(Map.of("good", Status.ACTIVE), decoded.statuses);
        assertFalse(decoded.statuses.containsKey("future"));
        assertEquals(Map.of("ok", Kind.ALPHA), decoded.kinds);
        assertFalse(decoded.kinds.containsKey("futureK"));

        byte[] reencoded = Protovia.toBytes(decoded);
        assertEquals(reencoded.length, Protovia.sizeOf(decoded));
        assertEquals(Map.of("good", 1), readRawStringEnumMap(reencoded, 1));
        assertEquals(Map.of("ok", 1), readRawStringEnumMap(reencoded, 2));
    }

    @Test
    void mapEnumLastWinsWithinSameEntryAndMissingValueUsesDefaultZero() {
        // Entry 1: tag-2 = 99 followed by tag-2 = 1 -> known ACTIVE wins, nothing in unknownFields.
        ProtoWriter e1 = ProtoWriter.growing();
        e1.writeString(1, "recovered");
        e1.writeEnum(2, 99);
        e1.writeEnum(2, 1);
        byte[] e1Bytes = e1.toByteArray();

        // Entry 2: tag-2 = 1 followed by tag-2 = 99 -> unknown wins, captured in unknownFields.
        ProtoWriter e2 = ProtoWriter.growing();
        e2.writeString(1, "lost");
        e2.writeEnum(2, 1);
        e2.writeEnum(2, 99);
        byte[] e2Bytes = e2.toByteArray();

        // Entry 3: key only, missing tag-2 -> defaults to Status.UNKNOWN (0), NOT unknown.
        ProtoWriter e3 = ProtoWriter.growing();
        e3.writeString(1, "defaultZero");
        byte[] e3Bytes = e3.toByteArray();

        ProtoWriter wire = ProtoWriter.growing();
        writeLenField(wire, 1, e1Bytes);
        writeLenField(wire, 1, e2Bytes);
        writeLenField(wire, 1, e3Bytes);

        EnumMapEnvelope decoded = Protovia.fromBytes(wire.toByteArray(), EnumMapEnvelope.class);
        assertEquals(Status.ACTIVE, decoded.statuses.get("recovered"));
        assertEquals(Status.UNKNOWN, decoded.statuses.get("defaultZero"));
        assertFalse(decoded.statuses.containsKey("lost"));
        assertFalse(decoded.unknownFields.isEmpty());

        byte[] reencoded = Protovia.toBytes(decoded);
        assertEquals(reencoded.length, Protovia.sizeOf(decoded));
        Map<String, Integer> raw = readRawStringEnumMap(reencoded, 1);
        assertEquals(1, raw.get("recovered"));
        assertEquals(0, raw.get("defaultZero"));
        assertEquals(99, raw.get("lost"));
    }

    @Test
    void recordWithEnumMapsAndAdaptedDeterministicKeyRoundTripsUnknownEntries() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID uUnknown = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID uMergedUnknown = UUID.fromString("00000000-0000-0000-0000-000000000004");

        ProtoWriter wire = ProtoWriter.growing();
        writeStringEnumEntry(wire, 1, "good", 1);      // Status.ACTIVE
        writeStringEnumEntry(wire, 1, "future", 99);   // unknown Status
        writeStringEnumEntry(wire, 1, "neg", -5);      // negative unknown Status
        writeStringEnumEntry(wire, 2, u2.toString(), 1); // Kind.ALPHA (written before u1 to test deterministic sort)
        writeStringEnumEntry(wire, 2, u1.toString(), 0); // Kind.UNSPECIFIED
        writeStringEnumEntry(wire, 2, uUnknown.toString(), 77); // unknown closed Kind

        EnumMapRecord decoded = Protovia.fromBytes(wire.toByteArray(), EnumMapRecord.class);
        assertEquals(Map.of("good", Status.ACTIVE), decoded.statuses());
        assertEquals(Map.of(u1, Kind.UNSPECIFIED, u2, Kind.ALPHA), decoded.uuidKinds());
        assertFalse(decoded.unknownFields().isEmpty());

        byte[] reencoded = Protovia.toBytes(decoded);
        assertEquals(reencoded.length, Protovia.sizeOf(decoded));

        Map<String, Integer> rawStatuses = readRawStringEnumMap(reencoded, 1);
        assertEquals(1, rawStatuses.get("good"));
        assertEquals(99, rawStatuses.get("future"));
        assertEquals(-5, rawStatuses.get("neg"));

        Map<String, Integer> rawKinds = readRawStringEnumMap(reencoded, 2);
        assertEquals(List.of(u1.toString(), u2.toString(), uUnknown.toString()), new ArrayList<>(rawKinds.keySet()));
        assertEquals(0, rawKinds.get(u1.toString()));
        assertEquals(1, rawKinds.get(u2.toString()));
        assertEquals(77, rawKinds.get(uUnknown.toString()));

        EnumMapRecord again = Protovia.fromBytes(reencoded, EnumMapRecord.class);
        assertEquals(decoded, again);

        // mergeFrom on record preserves existing entries and accumulates new known + unknown entries.
        ProtoWriter delta = ProtoWriter.growing();
        writeStringEnumEntry(delta, 1, "more", 2);      // Status.BANNED
        writeStringEnumEntry(delta, 1, "future2", 101); // additional unknown Status
        writeStringEnumEntry(delta, 2, uMergedUnknown.toString(), 88);

        EnumMapRecord merged = Protovia.mergeFrom(delta.toByteArray(), decoded);
        assertEquals(Map.of("good", Status.ACTIVE, "more", Status.BANNED), merged.statuses());
        assertEquals(Map.of(u1, Kind.UNSPECIFIED, u2, Kind.ALPHA), merged.uuidKinds());
        byte[] mergedBytes = Protovia.toBytes(merged);
        assertEquals(mergedBytes.length, Protovia.sizeOf(merged));
        Map<String, Integer> mergedRawStatuses = readRawStringEnumMap(mergedBytes, 1);
        assertEquals(1, mergedRawStatuses.get("good"));
        assertEquals(2, mergedRawStatuses.get("more"));
        assertEquals(99, mergedRawStatuses.get("future"));
        assertEquals(-5, mergedRawStatuses.get("neg"));
        assertEquals(101, mergedRawStatuses.get("future2"));
        Map<String, Integer> mergedRawKinds = readRawStringEnumMap(mergedBytes, 2);
        assertEquals(0, mergedRawKinds.get(u1.toString()));
        assertEquals(1, mergedRawKinds.get(u2.toString()));
        assertEquals(77, mergedRawKinds.get(uUnknown.toString()));
        assertEquals(88, mergedRawKinds.get(uMergedUnknown.toString()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void writeStringEnumEntry(ProtoWriter out, int fieldNumber, String key, int enumNumber) {
        ProtoWriter entry = ProtoWriter.growing();
        entry.writeString(1, key);
        entry.writeEnum(2, enumNumber);
        writeLenField(out, fieldNumber, entry.toByteArray());
    }

    private static void writeLenField(ProtoWriter out, int fieldNumber, byte[] payload) {
        out.writeUInt32NoTag(WireType.tag(fieldNumber, WireType.LEN));
        out.writeUInt32NoTag(payload.length);
        out.writeRawBytes(payload, 0, payload.length);
    }

    private static Map<String, Integer> readRawStringEnumMap(byte[] bytes, int fieldNumber) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ProtoReader r = new ProtoReader(bytes);
        int expectedTag = WireType.tag(fieldNumber, WireType.LEN);
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == expectedTag) {
                int oldLimit = r.pushLengthDelimited();
                String k = "";
                int v = 0;
                int etag;
                while ((etag = r.readTag()) != 0) {
                    switch (etag) {
                        case 10 -> k = r.readString();
                        case 16 -> v = r.readEnum();
                        default -> r.skipField();
                    }
                }
                r.popLimit(oldLimit);
                result.put(k, v);
            } else {
                r.skipField();
            }
        }
        return result;
    }

    private static List<Integer> readAllEnumsAt(byte[] bytes, int fieldNumber) {
        List<Integer> values = new ArrayList<>();
        ProtoReader r = new ProtoReader(bytes);
        int expectedTag = WireType.tag(fieldNumber, WireType.VARINT);
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == expectedTag) {
                values.add(r.readEnum());
            } else {
                r.skipField();
            }
        }
        return values;
    }

    private static int readIntAt(byte[] bytes, int fieldNumber) {
        ProtoReader r = new ProtoReader(bytes);
        int expectedTag = WireType.tag(fieldNumber, WireType.VARINT);
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == expectedTag) {
                return r.readInt32();
            }
            r.skipField();
        }
        return -1;
    }

    private static String readStringAt(byte[] bytes, int fieldNumber) {
        ProtoReader r = new ProtoReader(bytes);
        int expectedTag = WireType.tag(fieldNumber, WireType.LEN);
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == expectedTag) {
                return r.readString();
            }
            r.skipField();
        }
        return null;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] out = new byte[left.length + right.length];
        System.arraycopy(left, 0, out, 0, left.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }
}
