package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class DuplicateBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.DUPLICATE.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        switch (opcode)
        {
            case DUP -> {
                pop(ib, context, InterpretContext.DUP_VALUE_1);
                push(ib, context, InterpretContext.DUP_VALUE_1, InterpretContext.DUP_VALUE_1);
            }
            case DUP_X1 -> {
                pop(ib, context, InterpretContext.DUP_VALUE_1);
                pop(ib, context, InterpretContext.DUP_VALUE_2);
                push(ib, context,
                        InterpretContext.DUP_VALUE_1,
                        InterpretContext.DUP_VALUE_2,
                        InterpretContext.DUP_VALUE_1);
            }
            case DUP_X2 -> generateDupX2(ib, context);
            case DUP2 -> generateDup2(ib, context);
            case DUP2_X1 -> generateDup2X1(ib, context);
            case DUP2_X2 -> generateDup2X2(ib, context);
        }
    }

    private static void generateDupX2(AdvInsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);
        pop(ib, context, InterpretContext.DUP_VALUE_2);

        LabelNode category2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, context, InterpretContext.DUP_VALUE_2, category2);

        pop(ib, context, InterpretContext.DUP_VALUE_3);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.gotoLabel(done);

        ib.mark(category2, "dupX2Category2");
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.mark(done, "dupX2Done");
    }

    private static void generateDup2(AdvInsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);

        LabelNode category2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, context, InterpretContext.DUP_VALUE_1, category2);

        pop(ib, context, InterpretContext.DUP_VALUE_2);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.gotoLabel(done);

        ib.mark(category2, "dup2Category2");
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_1);
        ib.mark(done, "dup2Done");
    }

    private static void generateDup2X1(AdvInsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);

        LabelNode topCategory2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, context, InterpretContext.DUP_VALUE_1, topCategory2);

        pop(ib, context, InterpretContext.DUP_VALUE_2);
        pop(ib, context, InterpretContext.DUP_VALUE_3);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.gotoLabel(done);

        ib.mark(topCategory2, "dup2X1TopCategory2");
        pop(ib, context, InterpretContext.DUP_VALUE_2);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.mark(done, "dup2X1Done");
    }

    private static void generateDup2X2(AdvInsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);

        LabelNode topCategory2 = new LabelNode();
        LabelNode thirdCategory2 = new LabelNode();
        LabelNode secondCategory2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, context, InterpretContext.DUP_VALUE_1, topCategory2);

        pop(ib, context, InterpretContext.DUP_VALUE_2);
        pop(ib, context, InterpretContext.DUP_VALUE_3);
        jumpIfValueIsCategory2(ib, context, InterpretContext.DUP_VALUE_3, thirdCategory2);

        pop(ib, context, InterpretContext.DUP_VALUE_4);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_4,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.gotoLabel(done);

        ib.mark(thirdCategory2, "dup2X2ThirdCategory2");
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.gotoLabel(done);

        ib.mark(topCategory2, "dup2X2TopCategory2");
        pop(ib, context, InterpretContext.DUP_VALUE_2);
        jumpIfValueIsCategory2(ib, context, InterpretContext.DUP_VALUE_2, secondCategory2);

        pop(ib, context, InterpretContext.DUP_VALUE_3);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.gotoLabel(done);

        ib.mark(secondCategory2, "dup2X2SecondCategory2");
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.mark(done, "dup2X2Done");
    }

    private static void pop(AdvInsnBuilder ib, InterpretContext context, int local)
    {
        popObjectAndWidth(ib, context, local, widthLocal(local));
    }

    private static void push(AdvInsnBuilder ib, InterpretContext context, int... locals)
    {
        for (int local : locals)
        {
            pushObjectWithWidth(ib, context, local, widthLocal(local));
        }
    }

    private static void jumpIfValueIsCategory2(
            AdvInsnBuilder ib,
            InterpretContext context,
            int valueLocal,
            LabelNode target)
    {
        jumpIfCategory2(ib, context.intLocal("width" + valueLocal, widthLocal(valueLocal)), target);
    }

    private static int widthLocal(int valueLocal)
    {
        return valueLocal + (InterpretContext.DUP_WIDTH_1 - InterpretContext.DUP_VALUE_1);
    }
}
