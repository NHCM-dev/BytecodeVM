package nhcm.bytecodevm.Tools;

import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Utils.Collection.IntArrayBuilder;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class VMMethodCompiler
{
    private final OpcMutator opcMutator;

    public VMMethodCompiler(OpcMutator opcMutator)
    {
        this.opcMutator = opcMutator;
    }

    public VMMethod compile(ClassNode owner, MethodNode method)
    {
        IntArrayBuilder code = new IntArrayBuilder();
        List<Object> constants = new ArrayList<>();

        Map<LabelNode, Integer> labels = new IdentityHashMap<>();
        List<JumpFixup> jumps = new ArrayList<>();

        for (AbstractInsnNode insn : method.instructions)
        {
            if (insn instanceof LabelNode)
            {
                labels.put((LabelNode) insn, code.size());
                continue;
            }

            if (insn.getOpcode() < 0)
            {
                continue;
            }

            Opcs opcode = Opcs.fromOpcode(insn.getOpcode());

            if (opcode == null)
            {
                throw unsupported(owner, method, insn);
            }

            code.add(opcMutator.toMutated(opcode));
            int operandStart = code.size();
            compileOperands(owner, method, insn, code, constants, jumps);

            int actualOperandCount = code.size() - operandStart;
            int expectedOperandCount = opcode.operandFormat.variableLength
                    ? opcode.getOperandCount(code.toArray(), operandStart)
                    : opcode.operandCount;

            if (expectedOperandCount != actualOperandCount)
            {
                throw new IllegalStateException(
                        "Operand metadata mismatch for " + opcode +
                                ": expected=" + expectedOperandCount +
                                ", actual=" + actualOperandCount);
            }
        }

        resolveJumps(code, labels, jumps);

        return new VMMethod(
                code.toArray(),
                constants.toArray(),
                method.maxLocals,
                method.maxStack,
                opcMutator);
    }

    private void compileOperands(ClassNode owner, MethodNode method, AbstractInsnNode insn, IntArrayBuilder code, List<Object> constants, List<JumpFixup> jumps)
    {
        if (insn instanceof InsnNode)
        {
            return;
        }

        if (insn instanceof VarInsnNode)
        {
            code.add(((VarInsnNode) insn).var);
            return;
        }

        if (insn instanceof IntInsnNode)
        {
            code.add(((IntInsnNode) insn).operand);
            return;
        }

        if (insn instanceof IincInsnNode iinc)
        {
            code.add(iinc.var).add(iinc.incr);
            return;
        }

        if (insn instanceof LdcInsnNode)
        {
            code.add(addConstant(constants, convertConstant(((LdcInsnNode) insn).cst)));
            return;
        }

        if (insn instanceof JumpInsnNode)
        {
            int operandIndex = code.size();
            code.add(0);
            jumps.add(new JumpFixup(operandIndex, ((JumpInsnNode) insn).label));
            return;
        }

        if (insn instanceof TypeInsnNode typeInsn)
        {
            code.add(addConstant(constants, typeInsn.desc));
            return;
        }

        if (insn instanceof FieldInsnNode fieldInsn)
        {
            code.add(addConstant(constants, fieldInsn.owner));
            code.add(addConstant(constants, fieldInsn.name));
            code.add(addConstant(constants, fieldInsn.desc));
            return;
        }

        if (insn instanceof MethodInsnNode methodInsn)
        {
            code.add(addConstant(constants, methodInsn.owner));
            code.add(addConstant(constants, methodInsn.name));
            code.add(addConstant(constants, methodInsn.desc));
            code.add(methodInsn.itf ? 1 : 0);
            return;
        }

        if (insn instanceof InvokeDynamicInsnNode dynamicInsn)
        {
            code.add(addConstant(constants, dynamicInsn.name));
            code.add(addConstant(constants, dynamicInsn.desc));
            code.add(addConstant(constants, dynamicInsn.bsm));
            code.add(dynamicInsn.bsmArgs.length);

            for (Object argument : dynamicInsn.bsmArgs)
            {
                code.add(addConstant(constants, convertConstant(argument)));
            }
            return;
        }

        if (insn instanceof TableSwitchInsnNode tableSwitch)
        {
            code.add(tableSwitch.min);
            code.add(tableSwitch.max);
            addJump(code, jumps, tableSwitch.dflt);
            code.add(tableSwitch.labels.size());

            for (LabelNode label : tableSwitch.labels)
            {
                addJump(code, jumps, label);
            }
            return;
        }

        if (insn instanceof LookupSwitchInsnNode lookupSwitch)
        {
            addJump(code, jumps, lookupSwitch.dflt);
            code.add(lookupSwitch.keys.size());

            for (int index = 0; index < lookupSwitch.keys.size(); index++)
            {
                code.add(lookupSwitch.keys.get(index));
                addJump(code, jumps, lookupSwitch.labels.get(index));
            }
            return;
        }

        if (insn instanceof MultiANewArrayInsnNode multiArray)
        {
            code.add(addConstant(constants, multiArray.desc));
            code.add(multiArray.dims);
            return;
        }

        throw unsupported(owner, method, insn);
    }

    private static void addJump(
            IntArrayBuilder code,
            List<JumpFixup> jumps,
            LabelNode target)
    {
        int operandIndex = code.size();
        code.add(0);
        jumps.add(new JumpFixup(operandIndex, target));
    }

    private static Object convertConstant(Object constant)
    {
        if (constant instanceof Type)
        {
            return constant;
        }

        if (constant instanceof Handle)
        {
            return constant;
        }

        if (constant instanceof ConstantDynamic)
        {
            return constant;
        }

        if (constant instanceof Integer || constant instanceof Long ||
                constant instanceof Float || constant instanceof Double ||
                constant instanceof String)
        {
            return constant;
        }

        throw new IllegalArgumentException(
                "Unsupported constant type: " +
                        (constant == null ? "null" : constant.getClass().getName()));
    }

    private static int addConstant(List<Object> constants, Object constant)
    {
        int existing = constants.indexOf(constant);

        if (existing >= 0)
        {
            return existing;
        }

        constants.add(constant);
        return constants.size() - 1;
    }

    private static void resolveJumps(IntArrayBuilder code, Map<LabelNode, Integer> labels, List<JumpFixup> jumps)
    {
        for (JumpFixup jump : jumps)
        {
            Integer targetPc = labels.get(jump.target);

            if (targetPc == null)
            {
                throw new IllegalStateException("Jump target is not part of the method");
            }

            code.set(jump.operandIndex, targetPc);
        }
    }

    private static IllegalArgumentException unsupported(ClassNode owner, MethodNode method, AbstractInsnNode insn)
    {
        Opcs opcode = Opcs.fromOpcode(insn.getOpcode());
        String opcodeName = opcode == null ? Integer.toString(insn.getOpcode()) : opcode.name();

        return new IllegalArgumentException("Unsupported instruction " + opcodeName + " in " + owner.name + '.' + method.name + method.desc);
    }

    private static final class JumpFixup
    {
        private final int operandIndex;
        private final LabelNode target;

        private JumpFixup(int operandIndex, LabelNode target)
        {
            this.operandIndex = operandIndex;
            this.target = target;
        }
    }
}
