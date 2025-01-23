package com.EyeOfHarmonyBuffer.ASMChange;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

public class Grade2WaterPurificationRecipeChange implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!transformedName.equals("gregtech.loaders.postload.chains.PurifiedWaterRecipes")) {
            return basicClass;
        }

        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new PurifiedWaterRecipesClassVisitor(classWriter);
        classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }
}

class PurifiedWaterRecipesClassVisitor extends ClassVisitor {

    public PurifiedWaterRecipesClassVisitor(ClassWriter cw) {
        super(Opcodes.ASM5, cw);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (name.equals("run") && desc.equals("()V")) {
            return new RunMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}

class RunMethodVisitor extends MethodVisitor {

    public RunMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM5, mv);
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof Float && ((Float) value) == 20.0f) {
            super.visitLdcInsn(100.0f);
        } else {
            super.visitLdcInsn(value);
        }
    }
}
