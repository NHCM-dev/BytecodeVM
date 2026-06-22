package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class SwitchBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.SWITCH.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        popInt(ib, context, InterpretContext.SWITCH_KEY);

        if (opcode == Opcs.TABLESWITCH)
        {
            generateTableSwitch(ib, context);
        }
        else
        {
            generateLookupSwitch(ib, context);
        }

        setProgramCounter(ib, context);
        return ib.toInsnList();
    }

    private static void generateTableSwitch(InsnBuilder ib, InterpretContext context)
    {
        // Encoding: min, max, defaultTarget, labelCount, target0...
        context.nextToken(ib);
        ib.istore(InterpretContext.SWITCH_MIN);

        // max is redundant at runtime because labelCount follows it.
        context.nextToken(ib);
        ib.pop();

        context.nextToken(ib);
        ib.istore(InterpretContext.JUMP_TARGET);
        context.nextToken(ib);
        ib.istore(InterpretContext.SWITCH_COUNT);

        ib.iconst0();
        ib.istore(InterpretContext.SWITCH_INDEX);

        LabelNode loop = new LabelNode();
        LabelNode next = new LabelNode();
        LabelNode done = new LabelNode();
        ib.label(loop);
        ib.iload(InterpretContext.SWITCH_INDEX);
        ib.iload(InterpretContext.SWITCH_COUNT);
        ib.ifIcmpGe(done);

        context.nextToken(ib);
        ib.istore(InterpretContext.SWITCH_CANDIDATE);

        ib.iload(InterpretContext.SWITCH_KEY);
        ib.iload(InterpretContext.SWITCH_MIN);
        ib.iload(InterpretContext.SWITCH_INDEX);
        ib.iadd();
        ib.ifIcmpNe(next);

        ib.iload(InterpretContext.SWITCH_CANDIDATE);
        ib.istore(InterpretContext.JUMP_TARGET);

        ib.label(next);
        ib.iinc(InterpretContext.SWITCH_INDEX, 1);
        ib.goto_(loop);
        ib.label(done);
    }

    private static void generateLookupSwitch(InsnBuilder ib, InterpretContext context)
    {
        // Encoding: defaultTarget, pairCount, key0, target0...
        context.nextToken(ib);
        ib.istore(InterpretContext.JUMP_TARGET);
        context.nextToken(ib);
        ib.istore(InterpretContext.SWITCH_COUNT);

        ib.iconst0();
        ib.istore(InterpretContext.SWITCH_INDEX);

        LabelNode loop = new LabelNode();
        LabelNode next = new LabelNode();
        LabelNode done = new LabelNode();
        ib.label(loop);
        ib.iload(InterpretContext.SWITCH_INDEX);
        ib.iload(InterpretContext.SWITCH_COUNT);
        ib.ifIcmpGe(done);

        context.nextToken(ib);
        ib.istore(InterpretContext.SWITCH_MIN);
        context.nextToken(ib);
        ib.istore(InterpretContext.SWITCH_CANDIDATE);

        ib.iload(InterpretContext.SWITCH_KEY);
        ib.iload(InterpretContext.SWITCH_MIN);
        ib.ifIcmpNe(next);

        ib.iload(InterpretContext.SWITCH_CANDIDATE);
        ib.istore(InterpretContext.JUMP_TARGET);

        ib.label(next);
        ib.iinc(InterpretContext.SWITCH_INDEX, 1);
        ib.goto_(loop);
        ib.label(done);
    }

    private static void setProgramCounter(InsnBuilder ib, InterpretContext context)
    {
        context.loadFrame(ib);
        ib.iload(InterpretContext.JUMP_TARGET);
        ib.putField(context.frameClassName, "programCounter", "I");
    }
}
