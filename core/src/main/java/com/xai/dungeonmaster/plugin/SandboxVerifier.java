package com.xai.dungeonmaster.plugin;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Reads a Java class file's constant pool (and method access flags) and reports
 * the first referenced class that a {@link SandboxPolicy} forbids — or the first
 * {@code native} method, which would let a plugin escape via JNI.
 *
 * Every type a class actually uses — superclass, interfaces, field/method
 * owners, {@code new}, {@code checkcast}, {@code instanceof}, method receivers —
 * appears as a {@code CONSTANT_Class} entry, so scanning those entries catches
 * real usage of a blocked API without false-positives on unrelated string
 * constants. Native methods are a second escape hatch the constant-pool scan
 * alone would miss.
 *
 * Pure JDK (no ASM): the class-file format is stable and simple enough to walk
 * directly.
 */
public final class SandboxVerifier {

    private SandboxVerifier() {}

    // Constant pool tags (JVMS §4.4).
    private static final int UTF8 = 1, INTEGER = 3, FLOAT = 4, LONG = 5, DOUBLE = 6,
            CLASS = 7, STRING = 8, FIELDREF = 9, METHODREF = 10, IMETHODREF = 11,
            NAME_AND_TYPE = 12, METHOD_HANDLE = 15, METHOD_TYPE = 16,
            DYNAMIC = 17, INVOKE_DYNAMIC = 18, MODULE = 19, PACKAGE = 20;

    private static final int ACC_NATIVE = 0x0100;

    /**
     * @return the first denied internal class name referenced by the class, a
     *         {@code "native method: …"} marker, or {@code null} if the class
     *         is clean (or the policy is disabled). Unparseable input is treated
     *         as a violation ("unparseable").
     */
    public static String firstViolation(byte[] classBytes, SandboxPolicy policy) {
        if (policy == null || !policy.isEnabled() || classBytes == null) {
            return null;
        }
        try {
            return scan(classBytes, policy);
        } catch (IOException | RuntimeException e) {
            return "unparseable class file (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static String scan(byte[] bytes, SandboxPolicy policy) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) {
            return "not a class file";
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int cpCount = in.readUnsignedShort();
        String[] utf8 = new String[cpCount];
        int[] classNameIndex = new int[cpCount];

        for (int i = 1; i < cpCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case UTF8 -> utf8[i] = in.readUTF();
                case CLASS -> classNameIndex[i] = in.readUnsignedShort();
                case STRING, METHOD_TYPE, MODULE, PACKAGE -> in.readUnsignedShort();
                case METHOD_HANDLE -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                case INTEGER, FLOAT -> in.readInt();
                case LONG, DOUBLE -> { in.readLong(); i++; } // 8-byte constants take two slots
                case FIELDREF, METHODREF, IMETHODREF, NAME_AND_TYPE, DYNAMIC, INVOKE_DYNAMIC -> {
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                }
                default -> {
                    return "unknown constant pool tag " + tag;
                }
            }
        }

        for (int i = 1; i < cpCount; i++) {
            if (classNameIndex[i] != 0) {
                String base = baseInternalName(utf8[classNameIndex[i]]);
                if (base != null && policy.isDenied(base)) {
                    return base;
                }
            }
        }

        // Walk class header → fields → methods looking for ACC_NATIVE.
        in.readUnsignedShort(); // access_flags
        in.readUnsignedShort(); // this_class
        in.readUnsignedShort(); // super_class
        int ifaceCount = in.readUnsignedShort();
        for (int i = 0; i < ifaceCount; i++) {
            in.readUnsignedShort();
        }
        int fieldCount = in.readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            skipMember(in);
        }
        int methodCount = in.readUnsignedShort();
        for (int i = 0; i < methodCount; i++) {
            int access = in.readUnsignedShort();
            int nameIdx = in.readUnsignedShort();
            in.readUnsignedShort(); // descriptor
            int attrCount = in.readUnsignedShort();
            for (int a = 0; a < attrCount; a++) {
                in.readUnsignedShort();
                int len = in.readInt();
                in.skipBytes(len);
            }
            if ((access & ACC_NATIVE) != 0) {
                String name = (nameIdx > 0 && nameIdx < utf8.length) ? utf8[nameIdx] : "?";
                return "native method: " + name;
            }
        }
        return null;
    }

    private static void skipMember(DataInputStream in) throws IOException {
        in.readUnsignedShort(); // access
        in.readUnsignedShort(); // name
        in.readUnsignedShort(); // descriptor
        int attrCount = in.readUnsignedShort();
        for (int a = 0; a < attrCount; a++) {
            in.readUnsignedShort();
            int len = in.readInt();
            in.skipBytes(len);
        }
    }

    /**
     * Reduce a CONSTANT_Class name to its base internal class name: strips array
     * dimensions and the {@code L...;} object wrapper. Returns null for
     * primitive array element types (e.g., {@code [I}).
     */
    static String baseInternalName(String name) {
        if (name == null) return null;
        int i = 0;
        while (i < name.length() && name.charAt(i) == '[') i++;
        if (i == 0) {
            return name; // plain internal name, e.g. java/lang/Runtime
        }
        if (i < name.length() && name.charAt(i) == 'L') {
            int semi = name.indexOf(';', i);
            return (semi > 0) ? name.substring(i + 1, semi) : name.substring(i + 1);
        }
        return null; // primitive array like [I, [[J
    }
}
