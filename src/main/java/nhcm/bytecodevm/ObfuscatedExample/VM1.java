package nhcm.bytecodevm.ObfuscatedExample;

final class VM1
{
    private VM1()
    {
    }

    static int executeInt(int codeId, Object receiver, Object[] arguments)
    {
        MethodFrame frame = new MethodFrame(
                CodePool.maxLocals(codeId),
                CodePool.maxStack(codeId));

        frame.locals[0] = receiver;
        System.arraycopy(arguments, 0, frame.locals, 1, arguments.length);
        execute(codeId, frame);
        return (Integer) frame.returnValue;
    }

    private static void execute(int codeId, MethodFrame frame)
    {
        int[] encodedCode = CodePool.encodedCode(codeId);

        while (!frame.returned)
        {
            int opcode = nextToken(encodedCode, codeId, frame);

            switch (opcode)
            {
                case CodePool.LOAD_LOCAL -> {
                    int localIndex = nextToken(encodedCode, codeId, frame);
                    frame.push(frame.locals[localIndex]);
                }

                case CodePool.STORE_LOCAL -> {
                    int localIndex = nextToken(encodedCode, codeId, frame);
                    frame.locals[localIndex] = frame.pop();
                }

                case CodePool.PUSH_INT -> frame.push(
                        nextToken(encodedCode, codeId, frame));

                case CodePool.ADD_INT -> {
                    int right = frame.popInt();
                    int left = frame.popInt();
                    frame.push(left + right);
                }

                case CodePool.SUBTRACT_INT -> {
                    int right = frame.popInt();
                    int left = frame.popInt();
                    frame.push(left - right);
                }

                case CodePool.MULTIPLY_INT -> {
                    int right = frame.popInt();
                    int left = frame.popInt();
                    frame.push(left * right);
                }

                case CodePool.XOR_INT -> {
                    int right = frame.popInt();
                    int left = frame.popInt();
                    frame.push(left ^ right);
                }

                case CodePool.NEGATE_INT -> frame.push(-frame.popInt());

                case CodePool.SHIFT_LEFT_INT -> {
                    int distance = frame.popInt();
                    int value = frame.popInt();
                    frame.push(value << distance);
                }

                case CodePool.UNSIGNED_SHIFT_RIGHT_INT -> {
                    int distance = frame.popInt();
                    int value = frame.popInt();
                    frame.push(value >>> distance);
                }

                case CodePool.IF_INT_GE_ZERO -> {
                    int target = nextToken(encodedCode, codeId, frame);
                    if (frame.popInt() >= 0) frame.programCounter = target;
                }

                case CodePool.IF_INT_LESS_THAN -> {
                    int target = nextToken(encodedCode, codeId, frame);
                    int right = frame.popInt();
                    int left = frame.popInt();
                    if (left < right) frame.programCounter = target;
                }

                case CodePool.IF_INT_GREATER_THAN -> {
                    int target = nextToken(encodedCode, codeId, frame);
                    int right = frame.popInt();
                    int left = frame.popInt();
                    if (left > right) frame.programCounter = target;
                }

                case CodePool.GOTO -> frame.programCounter =
                        nextToken(encodedCode, codeId, frame);

                case CodePool.RETURN_INT -> {
                    frame.returnValue = frame.popInt();
                    frame.returned = true;
                }

                default -> throw new IllegalStateException(
                        "Unknown VM opcode " + Integer.toHexString(opcode) +
                                " at pc " + (frame.programCounter - 1));
            }
        }
    }

    private static int nextToken(int[] encodedCode, int codeId, MethodFrame frame)
    {
        return CodePool.decodeToken(encodedCode, codeId, frame.programCounter++);
    }
}
