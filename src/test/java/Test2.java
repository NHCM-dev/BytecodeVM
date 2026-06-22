import nhcm.bytecodevm.Data.VMInsn.VMInstruction;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Data.VMInsn.VMOperand;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Tools.VMMethodCompiler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Test2
{
    public static void main(String[] args) throws IOException
    {
        VMMethodCompiler compiler = new VMMethodCompiler(OpcMutator.fromStrategy(OpcMutator.MutateStrategy.NONE));
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of("workspace/CodePool.class")));
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        for(MethodNode method : node.methods)
        {
            System.out.println("===");
            System.out.println("Method: " + method.name + method.desc);
            VMMethod vmMethod = compiler.compile(node, method);
            StringBuilder sb = new StringBuilder();
            sb.append("int[] codes = {");
            List<String> opcodes = new ArrayList<>();
            for(VMInstruction instruction : vmMethod.getInstructions())
            {
                StringBuilder operation = new StringBuilder();
                List<String> operands = new ArrayList<>();
                operation.append(instruction.opcode.name());
                operation.append("[").append(instruction.operandCount()).append("]");
                operation.append("(");
                for(VMOperand operand : instruction.operands)
                {
                    operands.add(operand.toString());
                }
                operation.append(String.join(", ", operands)).append(")");
                opcodes.add(operation.toString());
            }
            sb.append(String.join(", ", opcodes));
            sb.append("};");
            System.out.println(sb);
            System.out.println("Object[] constants = " + Arrays.toString(vmMethod.constants));
            System.out.println("int maxLocals = " + vmMethod.maxLocals);
            System.out.println("int maxStack = " + vmMethod.maxStack);
            System.out.println("===");
        }
    }
}
