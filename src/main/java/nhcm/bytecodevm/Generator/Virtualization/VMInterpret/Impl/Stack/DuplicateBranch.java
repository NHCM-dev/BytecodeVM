package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
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
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        switch (opcode)
        {
            case DUP ->
            {
                pop(ib, context, InterpretContext.DUP_VALUE_1);
                push(ib, context,
                        InterpretContext.DUP_VALUE_1,
                        InterpretContext.DUP_VALUE_1);
            }
            case DUP_X1 ->
            {
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
        return ib.toInsnList();
    }

    private static void generateDupX2(InsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);
        pop(ib, context, InterpretContext.DUP_VALUE_2);

        LabelNode category2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, InterpretContext.DUP_VALUE_2, category2);

        pop(ib, context, InterpretContext.DUP_VALUE_3);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        ib.label(category2);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.label(done);
    }

    private static void generateDup2(InsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);

        LabelNode category2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, InterpretContext.DUP_VALUE_1, category2);

        pop(ib, context, InterpretContext.DUP_VALUE_2);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        ib.label(category2);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_1);
        ib.label(done);
    }

    private static void generateDup2X1(InsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);

        LabelNode topCategory2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, InterpretContext.DUP_VALUE_1, topCategory2);

        pop(ib, context, InterpretContext.DUP_VALUE_2);
        pop(ib, context, InterpretContext.DUP_VALUE_3);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        ib.label(topCategory2);
        pop(ib, context, InterpretContext.DUP_VALUE_2);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.label(done);
    }

    private static void generateDup2X2(InsnBuilder ib, InterpretContext context)
    {
        pop(ib, context, InterpretContext.DUP_VALUE_1);

        LabelNode topCategory2 = new LabelNode();
        LabelNode thirdCategory2 = new LabelNode();
        LabelNode secondCategory2 = new LabelNode();
        LabelNode done = new LabelNode();
        jumpIfValueIsCategory2(ib, InterpretContext.DUP_VALUE_1, topCategory2);

        // Forms 1 and 2: value1/value2 are category-1.
        pop(ib, context, InterpretContext.DUP_VALUE_2);
        pop(ib, context, InterpretContext.DUP_VALUE_3);
        jumpIfValueIsCategory2(ib, InterpretContext.DUP_VALUE_3, thirdCategory2);

        // Form 1: four category-1 values.
        pop(ib, context, InterpretContext.DUP_VALUE_4);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_4,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        // Form 2: value3 is category-2.
        ib.label(thirdCategory2);
        push(ib, context,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        // Forms 3 and 4: value1 is category-2.
        ib.label(topCategory2);
        pop(ib, context, InterpretContext.DUP_VALUE_2);
        jumpIfValueIsCategory2(ib, InterpretContext.DUP_VALUE_2, secondCategory2);

        // Form 3: value2/value3 are category-1.
        pop(ib, context, InterpretContext.DUP_VALUE_3);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_3,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.goto_(done);

        // Form 4: value1/value2 are category-2.
        ib.label(secondCategory2);
        push(ib, context,
                InterpretContext.DUP_VALUE_1,
                InterpretContext.DUP_VALUE_2,
                InterpretContext.DUP_VALUE_1);
        ib.label(done);
    }

    private static void pop(InsnBuilder ib, InterpretContext context, int local)
    {
        context.loadFrame(ib);
        context.frame.peekWidth.invokeVirtual(ib);
        ib.istore(widthLocal(local));
        popObject(ib, context, local);
    }

    private static void push(InsnBuilder ib, InterpretContext context, int... locals)
    {
        for (int local : locals)
        {
            pushObjectWithWidth(ib, context, local, widthLocal(local));
        }
    }

    private static void jumpIfValueIsCategory2(
            InsnBuilder ib,
            int valueLocal,
            LabelNode target)
    {
        jumpIfCategory2(ib, widthLocal(valueLocal), target);
    }

    private static int widthLocal(int valueLocal)
    {
        return valueLocal + (InterpretContext.DUP_WIDTH_1 - InterpretContext.DUP_VALUE_1);
    }
}
