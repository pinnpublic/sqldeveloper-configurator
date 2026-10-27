import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;

/** Patches only the editor size menu, wheel clamp, and saved size accessors. */
public final class EditorPatcher {
    static final String PANEL = "oracle/ide/ceditor/options/FontSizeOptionsPanel.class";
    static final String WHEEL = "oracle/ide/ceditor/ZoomTextPlugin$MouseWheelL.class";
    static final String OPTIONS = "oracle/ide/ceditor/options/FontSizeOptions.class";

    static byte[] transform(String name, byte[] original) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] matches = new int[1];
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, method, desc, sig, exceptions);
                return new MethodVisitor(Opcodes.ASM8, next) {
                    @Override public void visitFieldInsn(int op, String owner, String field, String type) {
                        super.visitFieldInsn(op, owner, field, type);
                        if (name.equals(PANEL) && method.equals("initializeComponent") && op == Opcodes.PUTFIELD
                                && field.equals("comboBox_fontSize") && type.equals("Ljavax/swing/JComboBox;")) {
                            for (int size = 25; size <= 50; size++) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, owner, field, type);
                                super.visitIntInsn(Opcodes.BIPUSH, size);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JComboBox", "addItem", "(Ljava/lang/Object;)V", false);
                            }
                            matches[0]++;
                        }
                    }
                    @Override public void visitIntInsn(int op, int operand) {
                        if (name.equals(WHEEL) && method.equals("mouseWheelMoved") && op == Opcodes.BIPUSH && operand == 24) {
                            operand = 50;
                            matches[0]++;
                        }
                        super.visitIntInsn(op, operand);
                    }
                    void clamp() {
                        super.visitIntInsn(Opcodes.BIPUSH, 7);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false);
                        super.visitIntInsn(Opcodes.BIPUSH, 50);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false);
                    }
                    @Override public void visitCode() {
                        super.visitCode();
                        if (name.equals(OPTIONS) && method.equals("setFontSize") && desc.equals("(I)V")) {
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            clamp();
                            super.visitVarInsn(Opcodes.ISTORE, 1);
                            matches[0]++;
                        }
                    }
                    @Override public void visitInsn(int op) {
                        if (name.equals(OPTIONS) && method.equals("getFontSize") && desc.equals("()I") && op == Opcodes.IRETURN) {
                            clamp();
                            matches[0]++;
                        }
                        super.visitInsn(op);
                    }
                };
            }
        }, 0);
        int expected = name.equals(OPTIONS) ? 2 : 1;
        if (matches[0] != expected) throw new IllegalStateException("Unexpected editor implementation: " + name + " (" + matches[0] + ")");
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("EditorPatcher input.jar output.jar");
        Set<String> remaining = new HashSet<>(List.of(PANEL, WHEEL, OPTIONS));
        try (JarFile source = new JarFile(args[0]); JarOutputStream out = new JarOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.matches("(?i)META-INF/.*\\.(SF|RSA|DSA|EC)")) throw new IllegalStateException("Signed editor JAR is not supported");
                byte[] bytes;
                try (InputStream in = source.getInputStream(entry)) { bytes = in.readAllBytes(); }
                if (remaining.remove(name)) bytes = transform(name, bytes);
                JarEntry copy = new JarEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
        }
        if (!remaining.isEmpty()) throw new IllegalStateException("Missing editor classes: " + remaining);
        System.out.println("Editor menu, Ctrl+wheel and saved size clamp patched to 50pt.");
    }
}
