package io.github.rawvoid.protovia.processor.gen;

/**
 * Indent-aware source buffer used by {@link CodecGenerator}.
 *
 * @author Rawvoid
 */
final class JavaWriter {

    private final StringBuilder sb = new StringBuilder();
    private int indent;

    JavaWriter line(String text) {
        if (!text.isEmpty()) {
            sb.append("    ".repeat(indent));
            sb.append(text);
        }
        sb.append('\n');
        return this;
    }

    JavaWriter open(String header) {
        return line(header + " {").inc();
    }

    JavaWriter close() {
        return dec().line("}");
    }

    JavaWriter inc() {
        indent++;
        return this;
    }

    JavaWriter dec() {
        indent--;
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
