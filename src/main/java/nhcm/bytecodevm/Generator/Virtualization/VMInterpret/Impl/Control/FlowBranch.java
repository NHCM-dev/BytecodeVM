package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Condition;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;

import java.util.Set;

public class FlowBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.FLOW.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var jumpTarget = context.intLocal("jumpTarget", InterpretContext.JUMP_TARGET);
        context.nextOperand(ib, jumpTarget);

        Condition condition = switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                popInt(ib, context, InterpretContext.RIGHT_VALUE);
                yield intCondition(opcode, context.rightValue(NumericType.INT), AdvInsnBuilder.constant(0));
            }
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                popInt(ib, context, InterpretContext.RIGHT_VALUE);
                popInt(ib, context, InterpretContext.LEFT_VALUE);
                yield intCondition(opcode, context.leftValue(NumericType.INT), context.rightValue(NumericType.INT));
            }
            case IF_ACMPEQ, IF_ACMPNE -> {
                popObject(ib, context, InterpretContext.RIGHT_VALUE);
                popObject(ib, context, InterpretContext.LEFT_VALUE);
                yield opcode == Opcs.IF_ACMPEQ
                        ? AdvInsnBuilder.equal(context.objectLocal("left", InterpretContext.LEFT_VALUE),
                                               context.objectLocal("right", InterpretContext.RIGHT_VALUE))
                        : AdvInsnBuilder.notEqual(context.objectLocal("left", InterpretContext.LEFT_VALUE),
                                                  context.objectLocal("right", InterpretContext.RIGHT_VALUE));
            }
            case IFNULL, IFNONNULL -> {
                popObject(ib, context, InterpretContext.RIGHT_VALUE);
                yield opcode == Opcs.IFNULL
                        ? AdvInsnBuilder.isNull(context.objectLocal("value", InterpretContext.RIGHT_VALUE))
                        : AdvInsnBuilder.notNull(context.objectLocal("value", InterpretContext.RIGHT_VALUE));
            }
            default -> throw new IllegalArgumentException("Unsupported flow opcode: " + opcode);
        };

        ib.ifCondition(condition, b -> b.set(context.frameProgramCounter(), jumpTarget));
    }

    private static Condition intCondition(Opcs opcode, Expr left, Expr right)
    {
        return switch (opcode)
        {
            case IFEQ, IF_ICMPEQ -> AdvInsnBuilder.equal(left, right);
            case IFNE, IF_ICMPNE -> AdvInsnBuilder.notEqual(left, right);
            case IFLT, IF_ICMPLT -> AdvInsnBuilder.lessThan(left, right);
            case IFGE, IF_ICMPGE -> AdvInsnBuilder.greaterOrEqual(left, right);
            case IFGT, IF_ICMPGT -> AdvInsnBuilder.greaterThan(left, right);
            case IFLE, IF_ICMPLE -> AdvInsnBuilder.lessOrEqual(left, right);
            default -> throw new IllegalArgumentException("Unsupported int flow opcode: " + opcode);
        };
    }
}
