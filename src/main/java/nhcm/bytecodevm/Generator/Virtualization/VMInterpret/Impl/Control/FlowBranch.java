package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class FlowBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.FLOW.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        context.nextToken(ib);
        ib.istore(InterpretContext.JUMP_TARGET);

        LabelNode skipJump = new LabelNode();

        switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE ->
            {
                popInt(ib, context);
                switch (opcode)
                {
                    case IFEQ -> ib.ifne(skipJump);
                    case IFNE -> ib.ifeq(skipJump);
                    case IFLT -> ib.ifge(skipJump);
                    case IFGE -> ib.iflt(skipJump);
                    case IFGT -> ib.ifle(skipJump);
                    case IFLE -> ib.ifgt(skipJump);
                }
            }
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE ->
            {
                popInt(ib, context, InterpretContext.RIGHT_VALUE);
                popInt(ib, context, InterpretContext.LEFT_VALUE);
                ib.iload(InterpretContext.LEFT_VALUE);
                ib.iload(InterpretContext.RIGHT_VALUE);
                switch (opcode)
                {
                    case IF_ICMPEQ -> ib.ifIcmpNe(skipJump);
                    case IF_ICMPNE -> ib.ifIcmpEq(skipJump);
                    case IF_ICMPLT -> ib.ifIcmpGe(skipJump);
                    case IF_ICMPGE -> ib.ifIcmpLt(skipJump);
                    case IF_ICMPGT -> ib.ifIcmpLe(skipJump);
                    case IF_ICMPLE -> ib.ifIcmpGt(skipJump);
                }
            }
            case IF_ACMPEQ, IF_ACMPNE ->
            {
                popObject(ib, context, InterpretContext.RIGHT_VALUE);
                popObject(ib, context, InterpretContext.LEFT_VALUE);
                ib.aload(InterpretContext.LEFT_VALUE);
                ib.aload(InterpretContext.RIGHT_VALUE);
                switch (opcode)
                {
                    case IF_ACMPEQ -> ib.ifAcmpNe(skipJump);
                    case IF_ACMPNE -> ib.ifAcmpEq(skipJump);
                }
            }
            case IFNULL, IFNONNULL ->
            {
                popObject(ib, context);
                switch (opcode)
                {
                    case IFNULL -> ib.ifNonNull(skipJump);
                    case IFNONNULL -> ib.ifNull(skipJump);
                }
            }
        }

        setProgramCounter(ib, context);
        ib.label(skipJump);
        return ib.toInsnList();
    }

    private static void setProgramCounter(InsnBuilder ib, InterpretContext context)
    {
        context.loadFrame(ib);
        ib.iload(InterpretContext.JUMP_TARGET);
        context.frame.programCounter.put(ib);
    }
}
