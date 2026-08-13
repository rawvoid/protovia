package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.UnknownFields;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnknownFieldsTest {

    @Test
    void mergeUnknownInt32AndRewrite() {
        ProtoWriter in = ProtoWriter.growing();
        in.writeString(1, "a");
        in.writeInt32(15, 7);
        byte[] input = in.toByteArray();

        ProtoReader r = new ProtoReader(input);
        String name = null;
        UnknownFields unknown = UnknownFields.EMPTY;
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == WireType.tag(1, WireType.LEN)) {
                name = r.readString();
            } else {
                unknown = UnknownFields.merge(unknown, r);
            }
        }
        assertEquals("a", name);

        int size = CodedSize.string(1, name) + unknown.serializedSize();
        ProtoWriter out = new ProtoWriter(size);
        out.writeString(1, name);
        unknown.writeTo(out);

        ProtoReader back = new ProtoReader(out.finish());
        int extra = 0;
        while ((tag = back.readTag()) != 0) {
            if (tag == WireType.tag(1, WireType.LEN)) {
                assertEquals("a", back.readString());
            } else if (tag == WireType.tag(15, WireType.VARINT)) {
                extra = back.readInt32();
            } else {
                back.skipField();
            }
        }
        assertEquals(7, extra);
    }

    @Test
    void captureLengthDelimitedUnknown() {
        ProtoWriter in = ProtoWriter.growing();
        in.writeString(9, "xyz");
        byte[] input = in.toByteArray();

        ProtoReader r = new ProtoReader(input);
        assertEquals(WireType.tag(9, WireType.LEN), r.readTag());
        UnknownFields unknown = UnknownFields.merge(UnknownFields.EMPTY, r);
        assertEquals(input.length, unknown.serializedSize());

        ProtoWriter out = new ProtoWriter(unknown.serializedSize());
        unknown.writeTo(out);
        assertArrayEquals(input, out.finish());
    }

    @Test
    void mergeVarintUsesInt32EncodingForNegatives() {
        UnknownFields unknown = UnknownFields.mergeVarint(UnknownFields.EMPTY, 56, -1);
        assertEquals(CodedSize.uint32(56) + CodedSize.int32(-1), unknown.serializedSize());
        assertEquals(11, unknown.serializedSize());
        ProtoWriter out = new ProtoWriter(unknown.serializedSize());
        unknown.writeTo(out);
        ProtoReader back = new ProtoReader(out.finish());
        assertEquals(56, back.readTag());
        assertEquals(-1, back.readInt32());
    }

    @Test
    void emptyIsNoop() {
        assertTrue(UnknownFields.EMPTY.isEmpty());
        assertEquals(0, UnknownFields.EMPTY.serializedSize());
        ProtoWriter w = new ProtoWriter(0);
        UnknownFields.EMPTY.writeTo(w);
        assertArrayEquals(new byte[0], w.finish());
    }
}
