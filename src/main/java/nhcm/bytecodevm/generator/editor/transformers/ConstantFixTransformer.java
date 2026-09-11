package nhcm.bytecodevm.generator.editor.transformers;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.generator.abstracts.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;

public class ConstantFixTransformer extends Transformer
{
    public ConstantFixTransformer(BytecodeVMConfig config)
    {
        super(config, "constantFix");
    }

    @Override
    public int transform(Collection<ClassNode> classes)
    {
        int changed = 0;
        for (ClassNode classNode : classes)
        {
            Boolean sdkOverride = SdkAnnotationReader.classDirectives(classNode).constantFix();
            boolean enabled = sdkOverride == null ? config.constantFix : sdkOverride;
            if (!enabled)
            {
                continue;
            }
            for (FieldNode field : classNode.fields)
            {
                if (!shouldEncrypt(classNode, field, sdkOverride))
                {
                    continue;
                }
                MethodNode clinit = findOrCreateClinit(classNode);
                clinit.instructions.insertBefore(firstReturn(clinit), initializer(classNode, field));
                clinit.maxStack = Math.max(clinit.maxStack, constantStackSize(field.desc));
                field.value = null;
                changed++;
            }
        }
        return changed;
    }

    @Override
    protected boolean shouldEncrypt(ClassNode owner, FieldNode field, Boolean sdkOverride)
    {
        return field.value != null &&
               (field.access & Opcodes.ACC_STATIC) != 0 &&
               (field.access & Opcodes.ACC_FINAL) != 0 &&
               super.shouldEncrypt(owner, field, sdkOverride);
    }

    private static MethodNode findOrCreateClinit(ClassNode classNode)
    {
        for (MethodNode method : classNode.methods)
        {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc))
            {
                return method;
            }
        }
        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        classNode.methods.add(clinit);
        return clinit;
    }

    private static org.objectweb.asm.tree.AbstractInsnNode firstReturn(MethodNode method)
    {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions)
        {
            if (instruction.getOpcode() == Opcodes.RETURN)
            {
                return instruction;
            }
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method.instructions.getLast();
    }

    private static InsnList initializer(ClassNode owner, FieldNode field)
    {
        InsnList instructions = new InsnList();
        pushConstant(instructions, field.desc, field.value);
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner.name, field.name, field.desc));
        return instructions;
    }

    private static int constantStackSize(String descriptor)
    {
        Type type = Type.getType(descriptor);
        return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
    }

    private static void pushConstant(InsnList instructions, String descriptor, Object value)
    {
        Type type = Type.getType(descriptor);
        switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                    pushInt(instructions, ((Number) value).intValue());
            case Type.FLOAT -> {
                float floatValue = ((Number) value).floatValue();
                if (floatValue == 0.0F)
                {
                    instructions.add(new InsnNode(Opcodes.FCONST_0));
                }
                else if (floatValue == 1.0F)
                {
                    instructions.add(new InsnNode(Opcodes.FCONST_1));
                }
                else if (floatValue == 2.0F)
                {
                    instructions.add(new InsnNode(Opcodes.FCONST_2));
                }
                else
                {
                    instructions.add(new LdcInsnNode(value));
                }
            }
            case Type.LONG -> {
                long longValue = ((Number) value).longValue();
                if (longValue == 0L)
                {
                    instructions.add(new InsnNode(Opcodes.LCONST_0));
                }
                else if (longValue == 1L)
                {
                    instructions.add(new InsnNode(Opcodes.LCONST_1));
                }
                else
                {
                    instructions.add(new LdcInsnNode(value));
                }
            }
            case Type.DOUBLE -> {
                double doubleValue = ((Number) value).doubleValue();
                if (doubleValue == 0.0D)
                {
                    instructions.add(new InsnNode(Opcodes.DCONST_0));
                }
                else if (doubleValue == 1.0D)
                {
                    instructions.add(new InsnNode(Opcodes.DCONST_1));
                }
                else
                {
                    instructions.add(new LdcInsnNode(value));
                }
            }
            case Type.OBJECT -> {
                if (!"java/lang/String".equals(type.getInternalName()))
                {
                    throw new IllegalArgumentException("Unsupported ConstantValue descriptor: " + descriptor);
                }
                instructions.add(new LdcInsnNode(value));
            }
            default -> throw new IllegalArgumentException("Unsupported ConstantValue descriptor: " + descriptor);
        }
    }

    private static void pushInt(InsnList instructions, int value)
    {
        if (value >= -1 && value <= 5)
        {
            instructions.add(new InsnNode(Opcodes.ICONST_0 + value));
        }
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
        {
            instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
        }
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
        {
            instructions.add(new IntInsnNode(Opcodes.SIPUSH, value));
        }
        else
        {
            instructions.add(new LdcInsnNode(value));
        }
    }
}
