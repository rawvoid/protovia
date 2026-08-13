package io.github.rawvoid.protovia;

/**
 * Unchecked failure while encoding, decoding, looking up a codec, or validating a schema.
 */
public class ProtoException extends RuntimeException {

    public ProtoException(String message) {
        super(message);
    }

    public ProtoException(String message, Throwable cause) {
        super(message, cause);
    }
}
