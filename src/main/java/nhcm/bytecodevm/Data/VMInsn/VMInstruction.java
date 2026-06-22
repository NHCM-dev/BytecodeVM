package nhcm.bytecodevm.Data.VMInsn;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;

import java.util.Collections;
import java.util.List;

public class VMInstruction
{
    public final int programCounter;
    public final int nextProgramCounter;
    public final int mutatedOpcode;
    public final Opcs opcode;
    public final VMOpcode operation;
    public final List<VMOperand> operands;

    protected VMInstruction(
            int programCounter,
            int nextProgramCounter,
            int mutatedOpcode,
            Opcs opcode,
            List<VMOperand> operands)
    {
        this.programCounter = programCounter;
        this.nextProgramCounter = nextProgramCounter;
        this.mutatedOpcode = mutatedOpcode;
        this.opcode = opcode;
        this.operation = VMOpcode.fromOpcode(opcode);
        this.operands = Collections.unmodifiableList(operands);
    }

    public VMOperand operand(int index)
    {
        return operands.get(index);
    }

    public int operandCount()
    {
        return operands.size();
    }

    @Override
    public String toString()
    {
        StringBuilder result = new StringBuilder()
                .append(programCounter)
                .append(": ")
                .append(operation)
                .append('/')
                .append(opcode)
                .append(" (")
                .append(mutatedOpcode)
                .append(')');

        for (VMOperand operand : operands)
        {
            result.append(' ').append(operand);
        }
        return result.toString();
    }
}
