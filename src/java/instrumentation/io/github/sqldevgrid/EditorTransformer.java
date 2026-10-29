package io.github.sqldevgrid;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.*;

/** Adds display hooks to Oracle's existing View, retaining its type and document. */
public final class EditorTransformer implements ClassFileTransformer {
    static final String BRIDGE = "io/github/sqldevgrid/bridge/EditorBridge";
    public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
        if ("oracle/javatools/editor/BasicAction".equals(name)) {
            ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8,writer){
                public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                    MethodVisitor next=super.visitMethod(access,method,descriptor,signature,exceptions);
                    if(!method.equals("getFontHeight")||!descriptor.equals("(Loracle/javatools/editor/BasicEditorPane;)I"))return next;
                    return new MethodVisitor(Opcodes.ASM8,next){public void visitInsn(int opcode){
                        if(opcode==Opcodes.IRETURN)mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/bridge/NotebookBridge","rowHeight","(I)I",false);
                        super.visitInsn(opcode);
                    }};
                }
            },0);return writer.toByteArray();
        }
        if ("oracle/ide/ceditor/options/FontSizeOptionsPanel".equals(name)) {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor next = super.visitMethod(access, method, descriptor, signature, exceptions);
                    String callback = method.equals("onEntry") && descriptor.equals("(Loracle/ide/panels/TraversableContext;)V") ? "entered"
                        : method.equals("apply") && descriptor.equals("(Loracle/ide/panels/ApplyEvent;)V") ? "applied"
                        : method.equals("cancel") && descriptor.equals("(Loracle/ide/panels/ApplyEvent;)V") ? "canceled" : null;
                    if (callback == null) return next;
                    return new MethodVisitor(Opcodes.ASM8, next) {
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                if (!callback.equals("canceled")) mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/sqldevgrid/NativeFontPreferencesBridge", callback,
                                    callback.equals("canceled") ? "(Ljava/lang/Object;)V" : "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                            }
                            super.visitInsn(opcode);
                        }

                    };
                }
            }, 0);
            return writer.toByteArray();
        }
        if ("oracle/ide/config/ExtensionNavigable".equals(name)) {
            ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8,writer){
                public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                    MethodVisitor next=super.visitMethod(access,method,descriptor,signature,exceptions);
                    if(!method.equals("getChildNavigables")||!descriptor.equals("()[Loracle/ide/panels/Navigable;"))return next;
                    return new MethodVisitor(Opcodes.ASM8,next){public void visitInsn(int opcode){
                        if(opcode==Opcodes.ARETURN){
                            mv.visitVarInsn(Opcodes.ALOAD,0);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/NativeTreePreferencesBridge","extend","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);
                            mv.visitTypeInsn(Opcodes.CHECKCAST,"[Loracle/ide/panels/Navigable;");
                        }
                        super.visitInsn(opcode);
                    }};
                }
            },0);return writer.toByteArray();
        }
        if ("oracle/ide/config/SettingsUIRegistry".equals(name)) {
            ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8,writer) {
                public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                    MethodVisitor next=super.visitMethod(access,method,descriptor,signature,exceptions);
                    if(!method.equals("getExtensionNavigables")||!descriptor.equals("(Ljava/lang/String;)Ljava/util/Collection;"))return next;
                    return new MethodVisitor(Opcodes.ASM8,next) {
                        public void visitInsn(int opcode) {
                            if(opcode==Opcodes.ARETURN) {
                                mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitLdcInsn(Type.getObjectType("oracle/ide/config/SettingsUIRegistry"));
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/NativePreferencesBridge","extend","(Ljava/util/Collection;Ljava/lang/String;Ljava/lang/Class;)Ljava/util/Collection;",false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            },0);
            System.setProperty("sqldev.font50.nativePreferencesTransformed","true");
            return writer.toByteArray();
        }
        if ("oracle/javatools/editor/gutter/LineGutterPlugin".equals(name) || "oracle/javatools/editor/folding/CodeFoldingMargin".equals(name)) {
            ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8,writer) {
                public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                    MethodVisitor target=super.visitMethod(access,method,descriptor,signature,exceptions);
                    return new MethodVisitor(Opcodes.ASM8,target) {
                        int heightReads;
                        public void visitFieldInsn(int opcode,String owner,String field,String desc) {
                            super.visitFieldInsn(opcode,owner,field,desc);
                            if(opcode==Opcodes.GETFIELD && field.equals("_fontHeight") && desc.equals("I")) {
                                heightReads++;
                                // Paint uses the next two reads for icon size and glyph-centered positioning.
                                if(!method.equals("paint")||heightReads==1)
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/bridge/NotebookBridge","rowHeight","(I)I",false);
                            }
                        }
                        public void visitInsn(int opcode) {
                            if(method.equals("getFontHeight") && opcode==Opcodes.IRETURN)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/bridge/NotebookBridge","rowHeight","(I)I",false);
                            super.visitInsn(opcode);
                        }
                        public void visitMethodInsn(int opcode,String owner,String called,String desc,boolean itf) {
                            super.visitMethodInsn(opcode,owner,called,desc,itf);
                            boolean gutter=method.equals("paint")&&called.equals("fillRect")&&owner.equals("java/awt/Graphics");
                            boolean folding=method.equals("paintComponent")&&called.equals("paintComponent")&&opcode==Opcodes.INVOKESPECIAL;
                            if(gutter||folding){
                                mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitVarInsn(Opcodes.ALOAD,0);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,gutter?"oracle/javatools/editor/gutter/LineGutterPlugin":"oracle/javatools/editor/folding/CodeFoldingMargin",gutter?"getEditor":"getEditorPane","()Loracle/javatools/editor/BasicEditorPane;",false);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/bridge/NotebookBridge","paintGutter","(Ljava/awt/Graphics;Ljavax/swing/JComponent;Ljavax/swing/text/JTextComponent;)V",false);
                            }
                        }
                    };
                }
            },0);
            return writer.toByteArray();
        }
        if ("oracle/javatools/editor/BasicEditorUI".equals(name)) {
            ClassReader reader = new ClassReader(bytes); ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                    MethodVisitor target=super.visitMethod(access,method,descriptor,signature,exceptions);
                    if (!method.equals("paintBackground") || !descriptor.equals("(Ljava/awt/Graphics;)V")) return target;
                    return new MethodVisitor(Opcodes.ASM8,target) {
                        public void visitInsn(int opcode) {
                            if(opcode==Opcodes.RETURN) {
                                mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitVarInsn(Opcodes.ALOAD,0);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"oracle/javatools/editor/BasicEditorUI","getComponent","()Ljavax/swing/text/JTextComponent;",false);
                                mv.visitInsn(Opcodes.ICONST_0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/bridge/NotebookBridge","paint","(Ljava/awt/Graphics;Ljavax/swing/text/JTextComponent;Z)V",false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            },0);
            return writer.toByteArray();
        }
        if (!"oracle/javatools/editor/BasicView".equals(name)) return null;
        System.setProperty("sqldev.font50.viewTransformed", "true");
        ClassReader reader = new ClassReader(bytes); ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, method, descriptor, signature, exceptions);
                if (method.equals("paintRow") || method.equals("paintSegment"))
                    return new MethodVisitor(Opcodes.ASM8,target) {
                        public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf) {
                            if(opcode==Opcodes.INVOKEVIRTUAL && owner.equals("java/awt/Graphics") && name.equals("fillRect") && desc.equals("(IIII)V")) {
                                mv.visitVarInsn(Opcodes.ALOAD,0);
                                if(method.equals("paintRow"))mv.visitVarInsn(Opcodes.ALOAD,12);
                                else {mv.visitVarInsn(Opcodes.ALOAD,3);mv.visitFieldInsn(Opcodes.GETFIELD,"oracle/javatools/editor/BasicView$RenderFragment","backgroundHighlight","Loracle/javatools/editor/highlight/HighlightStyle;");}
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,"io/github/sqldevgrid/bridge/NotebookBridge","fillHighlight","(Ljava/awt/Graphics;IIIILjavax/swing/text/View;Ljava/lang/Object;)V",false);
                            } else super.visitMethodInsn(opcode,owner,name,desc,itf);
                        }
                    };
                if (method.equals("drawTabbedText") && descriptor.equals("(Ljava/awt/Graphics;Ljava/awt/Rectangle;Ljava/awt/FontMetrics;IIII)I"))
                    return new MethodVisitor(Opcodes.ASM8, target) {
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/awt/Graphics") && name.equals("drawChars") && desc.equals("([CIIII)V")) {
                                mv.visitVarInsn(Opcodes.ALOAD,0); mv.visitVarInsn(Opcodes.ILOAD,4);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "drawChars", "(Ljava/awt/Graphics;[CIIIILjavax/swing/text/View;I)V", false);
                            } else super.visitMethodInsn(opcode, owner, name, desc, itf);
                        }
                    };
                // updateMetrics runs on every coordinate query and paint, even when nothing changes.
                // Layout validity already checks the font and native span; evict only on folding changes.
                if ((method.equals("updateFolding") || method.equals("rebuildFoldedBlocks")) && descriptor.equals("()V"))
                    return new MethodVisitor(Opcodes.ASM8, target) {
                        public void visitCode() { super.visitCode(); mv.visitVarInsn(Opcodes.ALOAD,0);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,BRIDGE,"drop","(Ljavax/swing/text/View;)V",false); }
                    };
                int operation = method.equals("paint") && descriptor.equals("(Ljava/awt/Graphics;Ljava/awt/Shape;)V") ? 1
                    : method.equals("modelToView") && descriptor.equals("(ILjava/awt/Shape;Ljavax/swing/text/Position$Bias;)Ljava/awt/Shape;") ? 2
                    : method.equals("modelToView") && descriptor.equals("(ILjavax/swing/text/Position$Bias;ILjavax/swing/text/Position$Bias;Ljava/awt/Shape;)Ljava/awt/Shape;") ? 3
                    : method.equals("viewToModel") ? 4
                    : method.equals("getPreferredSpan") ? 5 : method.equals("getMinimumSpan") ? 6 : method.equals("getMaximumSpan") ? 7
                    : method.equals("getRowForLine") ? 8 : method.equals("getLineForRow") ? 9 : 0;
                if (operation == 0) return target;
                return new MethodVisitor(Opcodes.ASM8, target) {
                    public void visitCode() {
                        super.visitCode(); Label original = new Label();
                        mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitInsn(operation == 1 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "active", "(Ljavax/swing/text/View;Z)Z", false);
                        mv.visitJumpInsn(Opcodes.IFEQ, original);
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        switch (operation) {
                            case 1 -> {
                                mv.visitVarInsn(Opcodes.ALOAD, 1); mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "paint", "(Ljavax/swing/text/View;Ljava/awt/Graphics;Ljava/awt/Shape;)V", false); mv.visitInsn(Opcodes.RETURN);
                            }
                            case 2 -> {
                                mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitVarInsn(Opcodes.ALOAD, 3);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "model", "(Ljavax/swing/text/View;ILjava/awt/Shape;Ljavax/swing/text/Position$Bias;)Ljava/awt/Shape;", false); mv.visitInsn(Opcodes.ARETURN);
                            }
                            case 3 -> {
                                mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitVarInsn(Opcodes.ILOAD, 3); mv.visitVarInsn(Opcodes.ALOAD, 4); mv.visitVarInsn(Opcodes.ALOAD, 5);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "range", "(Ljavax/swing/text/View;ILjavax/swing/text/Position$Bias;ILjavax/swing/text/Position$Bias;Ljava/awt/Shape;)Ljava/awt/Shape;", false); mv.visitInsn(Opcodes.ARETURN);
                            }
                            case 4 -> {
                                mv.visitVarInsn(Opcodes.FLOAD, 1); mv.visitVarInsn(Opcodes.FLOAD, 2); mv.visitVarInsn(Opcodes.ALOAD, 3); mv.visitVarInsn(Opcodes.ALOAD, 4);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "position", "(Ljavax/swing/text/View;FFLjava/awt/Shape;[Ljavax/swing/text/Position$Bias;)I", false); mv.visitInsn(Opcodes.IRETURN);
                            }
                            case 5, 6, 7 -> {
                                mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitInsn(operation == 5 ? Opcodes.ICONST_0 : operation == 6 ? Opcodes.ICONST_1 : Opcodes.ICONST_2);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "span", "(Ljavax/swing/text/View;II)F", false); mv.visitInsn(Opcodes.FRETURN);
                            }
                            case 8, 9 -> {
                                mv.visitVarInsn(Opcodes.ILOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, operation == 8 ? "rowForLine" : "lineForRow", "(Ljavax/swing/text/View;I)I", false); mv.visitInsn(Opcodes.IRETURN);
                            }
                        }
                        mv.visitLabel(original); mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
