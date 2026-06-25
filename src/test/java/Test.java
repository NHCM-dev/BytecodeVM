import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Data.VMInsn.VMInstruction;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Tools.VMMethodCompiler;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;

public class Test
{
    public static int sampleStaticCounter;

    public int sampleField;

    public static void main(String[] args)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "generatedScore",
                "(ILjava/lang/String;)I",
                null,
                null);

        AdvInsnBuilder code = AdvInsnBuilder.into(method);

        Local score = code.getLocal("score", "I", 0);
        Local name = code.getLocal("name", "java/lang/String", 1);
        Local i = code.var("i", "I");

        code.createLocal("total", "I", AdvInsnBuilder.plus(score, AdvInsnBuilder.constant(7)))
            .createLocal(
                    "mask",
                    "I",
                    AdvInsnBuilder.bitOr(
                            AdvInsnBuilder.bitAnd(code.getLocal("total"), AdvInsnBuilder.constant(15)),
                            AdvInsnBuilder.shiftLeft(AdvInsnBuilder.constant(1), AdvInsnBuilder.constant(3))))
            .createLocal("values", "[I", AdvInsnBuilder.newArray("I", AdvInsnBuilder.constant(3)))
            .setArray(code.getLocal("values"), AdvInsnBuilder.constant(0), code.getLocal("total"))
            .setArray(code.getLocal("values"), AdvInsnBuilder.constant(1), code.getLocal("mask"))
            .createLocal("box", "Test", AdvInsnBuilder.newObject("Test"))
            .writeLocal(
                    AdvInsnBuilder.field(code.getLocal("box"), "Test", "sampleField", "I"),
                    code.getLocal("total"))
            .writeLocal(
                    AdvInsnBuilder.staticField("Test", "sampleStaticCounter", "I"),
                    code.getLocal("total"))
            .tryCatch(
                    tryBlock -> tryBlock.directCall(AdvInsnBuilder.callVirtual(
                            AdvInsnBuilder.staticField("java/lang/System", "out", "Ljava/io/PrintStream;"),
                            "java/io/PrintStream",
                            "println",
                            "V",
                            AdvInsnBuilder.callVirtual(
                                    name,
                                    "java/lang/String",
                                    "toUpperCase",
                                    "java/lang/String"))),
                    AdvInsnBuilder.catchBlock(
                            "java/lang/Exception",
                            "ex",
                            catchBlock -> catchBlock.writeLocal("total", AdvInsnBuilder.constant(-1))))
            .ifCondition(
                    AdvInsnBuilder.and(
                            AdvInsnBuilder.notNull(name),
                            AdvInsnBuilder.isInstanceOf(name, "java/lang/String")),
                    block -> block.writeLocal(
                            "total",
                            AdvInsnBuilder.plus(
                                    AdvInsnBuilder.arrayAt(code.getLocal("values"), AdvInsnBuilder.constant(0)),
                                    AdvInsnBuilder.arrayLength(code.getLocal("values")))))
            .increment("total", 1)
            .writeLocal(
                    "total",
                            AdvInsnBuilder.callStatic(
                            "java/lang/Math",
                            "abs",
                            "I",
                            AdvInsnBuilder.negative(code.getLocal("total"))))
            .forLoop(
                    init -> init.writeLocal(i, AdvInsnBuilder.constant(0)),
                    AdvInsnBuilder.lessThan(i, AdvInsnBuilder.constant(3)),
                    update -> update.increment(i, 1),
                    body -> body.ifElse(
                            AdvInsnBuilder.equal(i, AdvInsnBuilder.constant(1)),
                            thenBlock -> thenBlock.continueLoop(),
                            elseBlock -> elseBlock.writeLocal(
                                    "total",
                                    AdvInsnBuilder.plus(code.getLocal("total"), i))))
            .switchLookup(
                    code.getLocal("mask"),
                    defaultBlock -> defaultBlock.writeLocal(
                            "total",
                            AdvInsnBuilder.plus(code.getLocal("total"), AdvInsnBuilder.constant(1))),
                    AdvInsnBuilder.switchCase(
                            8,
                            caseBlock -> caseBlock.writeLocal(
                                    "total",
                                    AdvInsnBuilder.plus(code.getLocal("total"), AdvInsnBuilder.constant(8)))),
                    AdvInsnBuilder.switchCase(
                            15,
                            caseBlock -> caseBlock.breakFlow()))
            .ifElse(
                    AdvInsnBuilder.greaterThan(code.getLocal("total"), AdvInsnBuilder.constant(20)),
                    thenBlock -> thenBlock.writeLocal(
                            "total",
                            AdvInsnBuilder.multiply(code.getLocal("total"), AdvInsnBuilder.constant(2))),
                    elseBlock -> elseBlock.writeLocal(
                            "total",
                            AdvInsnBuilder.plus(code.getLocal("total"), AdvInsnBuilder.constant(3))))
            .whileLoop(
                    AdvInsnBuilder.lessThan(code.getLocal("total"), AdvInsnBuilder.constant(50)),
                    loop -> loop.writeLocal(
                            "total",
                            AdvInsnBuilder.plus(code.getLocal("total"), AdvInsnBuilder.constant(5))))
            .returnValue(code.getLocal("total"));

        System.out.println("=== AdvInsnBuilder source view ===");
        System.out.println(code.sourceView());

        ClassNode owner = new ClassNode();
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "TestGenerated";
        owner.superName = "java/lang/Object";
        owner.methods.add(method);

        OpcMutator mutator = new OpcMutator(OpcMutator.MutateStrategy.NONE);
        VMMethod vmMethod = new VMMethodCompiler(mutator).compile(owner, method);

        System.out.println("=== VM raw code ===");
        System.out.println(Arrays.toString(vmMethod.code));

        System.out.println("=== VM constants ===");
        System.out.println(Arrays.toString(vmMethod.constants));

        System.out.println("=== VM decoded instructions ===");
        for (VMInstruction instruction : vmMethod.getInstructions())
        {
            System.out.println(instruction);
        }
    }
}
