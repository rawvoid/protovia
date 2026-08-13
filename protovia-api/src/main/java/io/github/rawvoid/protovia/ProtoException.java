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
