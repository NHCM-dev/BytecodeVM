package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Condition;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;

import java.util.Set;

public class CompareBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.COMPARE.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        Local result = context.intLocal("compareResult", InterpretContext.MIDDLE_VALUE);

        popNumber(ib, context, type, InterpretContext.RIGHT_VALUE);
        popNumber(ib, context, type, InterpretContext.LEFT_VALUE);

        if (opcode == Opcs.LCMP)
        {
            compareOrdered(ib, result, context.leftValue(type), context.rightValue(type));
        }
        else
        {
            compareFloating(ib, opcode, result, context.leftValue(type), context.rightValue(type));
        }

        pushInt(ib, context, result);
    }

    private static void compareFloating(
            AdvInsnBuilder ib,
            Opcs opcode,
            Local result,
            Expr left,
            Expr right)
    {
        boolean nanAsGreater = opcode == Opcs.FCMPG || opcode == Opcs.DCMPG;
        Condition hasNaN = switch (opcode)
        {
            case FCMPL, FCMPG -> AdvInsnBuilder.or(
                    AdvInsnBuilder.isTrue(AdvInsnBuilder.callStatic("java/lang/Float", "isNaN", "Z", left)),
                    AdvInsnBuilder.isTrue(AdvInsnBuilder.callStatic("java/lang/Float", "isNaN", "Z", right)));
            case DCMPL, DCMPG -> AdvInsnBuilder.or(
                    AdvInsnBuilder.isTrue(AdvInsnBuilder.callStatic("java/lang/Double", "isNaN", "Z", left)),
                    AdvInsnBuilder.isTrue(AdvInsnBuilder.callStatic("java/lang/Double", "isNaN", "Z", right)));
            default -> throw new IllegalArgumentException("Not a floating compare opcode: " + opcode);
        };

        ib.ifElse(
                hasNaN,
                b -> b.set(result, AdvInsnBuilder.constant(nanAsGreater ? 1 : -1)),
                b -> compareOrdered(b, result, left, right));
    }

    private static void compareOrdered(AdvInsnBuilder ib, Local result, Expr left, Expr right)
    {
        ib.ifElse(
                AdvInsnBuilder.greaterThan(left, right),
                b -> b.set(result, AdvInsnBuilder.constant(1)),
                b -> b.ifElse(
                        AdvInsnBuilder.equal(left, right),
                        equal -> equal.set(result, AdvInsnBuilder.constant(0)),
                        less -> less.set(result, AdvInsnBuilder.constant(-1))));
    }
}
