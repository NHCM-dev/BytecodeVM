# BytecodeVM

BytecodeVM 是一个完全运行在 JVM 生态内的 Java 字节码虚拟化框架。

它使用 ASM 读取普通 JVM 方法，将方法中的 JVM 指令翻译为自定义 VM 指令，并使用 Java 编写的解释器执行这些指令。转换后，原始方法体只保留参数封装、VM 调用和返回值转换，真正的程序逻辑存放在受保护的代码池中。

与生成 native code 的传统虚拟化保护不同，BytecodeVM 不依赖 JNI 或特定操作系统，可以继续保持 Java 程序的跨平台能力。

## 设计目标

原始方法：

```java
public static int calculate(int a, int b)
{
    int value = a * 2 + b;
    return value;
}
```

转换后的方法大致为：

```java
public static int calculate(int a, int b)
{
    return VM.executeInt(
            -137942816,
            null,
            new Object[]{
                    Integer.valueOf(a),
                    Integer.valueOf(b)
            }
    );
}
```

其中 `-137942816` 是随机生成的代码 ID。原方法的乘法、加法和局部变量操作已经从 JVM 方法体中移除，并被编译为 BytecodeVM 指令。

## 整体架构

```text
Original .class / .jar
          │
          ▼
     ASM ClassNode
          │
          ▼
     MethodCompiler
          │
          ├── VM instruction stream
          └── method metadata
          │
          ▼
        CodePool
          │
          ▼
      StubGenerator
          │
          ▼
  Transformed .class / .jar
          │
          ▼
     Runtime VM.execute
```

项目由两个主要部分组成：

- **编译期组件**：分析 JVM 方法、生成 VM 指令，并替换原方法体。
- **运行时组件**：加载 VM 方法、创建虚拟栈帧，并解释执行 VM 指令。

## 推荐模块

```text
nhcm.bytecodevm
├── runtime
│   ├── VM
│   ├── MethodFrame
│   ├── VMOpcode
│   ├── VMMethod
│   └── CodePool
├── compiler
│   ├── MethodCompiler
│   ├── CompilationResult
│   ├── InstructionCompiler
│   └── LabelResolver
├── transformer
│   ├── VirtualizationTransformer
│   ├── MethodSelector
│   └── StubGenerator
├── config
│   └── BytecodeVMConfig
└── util
```

## MethodFrame

`MethodFrame` 表示一个虚拟方法栈帧，对应 JVM 的局部变量表、操作数栈、程序计数器和返回值状态。

它属于 VM 的内部实现细节，转换后的业务方法不应直接构造或操作它，而是通过 `VM.executeInt`、`VM.executeObject` 等类型化入口调用 VM。

```java
final class MethodFrame
{
    final Object[] locals;
    final Object[] stack;

    int pc;
    int sp;

    Object returnValue;
    boolean returned;

    MethodFrame(int maxLocals, int maxStack)
    {
        this.locals = new Object[maxLocals];
        this.stack = new Object[maxStack];
    }

    void push(Object value)
    {
        stack[sp++] = value;
    }

    Object pop()
    {
        Object value = stack[--sp];
        stack[sp] = null;
        return value;
    }

    void pushInt(int value)
    {
        push(Integer.valueOf(value));
    }

    int popInt()
    {
        return ((Integer) pop()).intValue();
    }
}
```

首个版本使用 `Object[]` 和基本类型装箱，以实现正确性和可调试性为主。后续可以改为类型分离的存储结构来减少装箱和内存开销。

## VMMethod 与 CodePool

每个被虚拟化的方法被编译成一个 `VMMethod`：

```java
final class VMMethod
{
    final int[] code;
    final Object[] constants;
    final int maxLocals;
    final int maxStack;
}
```

- `code` 存储 VM opcode 及其整数操作数。
- `constants` 存储字符串、类型、字段引用和方法引用等常量。
- `maxLocals` 和 `maxStack` 用于创建 `MethodFrame`。

`CodePool` 通过随机代码 ID 获取对应的方法：

```java
VMMethod method = CodePool.get(codeId);
```

第一版可以直接使用 `int[]` 和 `Object[]`。之后再将指令流编码为加密或压缩的 `byte[]`，或者存放到 JAR 资源中。

## VM 解释器

VM 提供按照返回类型区分的入口：

```java
public static int executeInt(int codeId, Object receiver, Object[] arguments)
{
    return ((Integer) execute(codeId, receiver, arguments)).intValue();
}
```

实例方法通过 `receiver` 传递 `this`，静态方法传递 `null`。VM 根据方法元数据把 receiver 和参数放入正确的 local slot，然后开始解释执行：

```java
while (!frame.returned)
{
    int opcode = code[frame.pc++];

    switch (opcode)
    {
        case ILOAD:
            frame.push(frame.locals[code[frame.pc++]]);
            break;

        case IADD:
            int right = frame.popInt();
            int left = frame.popInt();
            frame.pushInt(left + right);
            break;

        case IRETURN:
            frame.returnValue = frame.pop();
            frame.returned = true;
            break;

        default:
            throw new IllegalStateException("Unknown VM opcode: " + opcode);
    }
}
```

## 指令翻译示例

以下 Java 代码：

```java
int value = a * 2 + b;
return value;
```

对应的 JVM 逻辑为：

```text
ILOAD 0
ICONST_2
IMUL
ILOAD 1
IADD
ISTORE 2
ILOAD 2
IRETURN
```

可以被编译成：

```java
int[] code = {
    19, 0,    // ILOAD 0
    7,  2,    // ICONST 2
    52,       // IMUL
    19, 1,    // ILOAD 1
    41,       // IADD
    28, 2,    // ISTORE 2
    19, 2,    // ILOAD 2
    63        // IRETURN
};
```

实际构建时可以随机分配这些数字，使不同构建产物拥有不同的 VM opcode 映射。

## 方法替换

`StubGenerator` 清空原方法体，并使用 ASM 生成 VM 调用。对于上面的静态整数方法，生成的 JVM 字节码大致为：

```text
LDC codeId
ACONST_NULL

ICONST_2
ANEWARRAY java/lang/Object

DUP
ICONST_0
ILOAD 0
INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;
AASTORE

DUP
ICONST_1
ILOAD 1
INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;
AASTORE

INVOKESTATIC VM.executeInt (ILjava/lang/Object;[Ljava/lang/Object;)I
IRETURN
```

对象、数组和 `void` 方法分别调用对应的运行时入口。实例方法还会把 local slot 0 中的 `this` 作为 receiver 传给 VM。

## 实现路线

### 第一阶段：最小执行闭环

只实现以下指令：

```text
ICONST
ILOAD
ISTORE
IADD
ISUB
IMUL
IDIV
IRETURN
RETURN
```

目标是成功虚拟化并执行：

```java
public static int add(int a, int b)
{
    return a + b;
}
```

测试必须比较转换前后的执行结果，而不只是检查生成的字节码结构。

### 第二阶段：控制流

加入：

```text
IFEQ
IFNE
IF_ICMP*
GOTO
TABLESWITCH
LOOKUPSWITCH
```

编译器使用两遍处理：第一遍生成指令并记录 Label，第二遍把 JVM Label 解析为 VM 程序计数器。

### 第三阶段：对象操作

逐步支持：

```text
GETFIELD / PUTFIELD
GETSTATIC / PUTSTATIC
INVOKESTATIC / INVOKEVIRTUAL
数组创建和数组访问
CHECKCAST / INSTANCEOF
```

### 第四阶段：复杂 JVM 语义

最后处理：

- `NEW + DUP + INVOKESPECIAL <init>` 的未初始化对象语义。
- JVM exception table 与异常处理器。
- `long` 和 `double` 占用两个 local slot 的规则。
- `invokedynamic`。
- `monitorenter` 和 `monitorexit`。

在这些语义被正确实现前，编译器应明确拒绝包含相关指令的方法。

## 首版限制

不支持的指令不能被静默忽略。编译器必须抛出包含类名、方法名、descriptor 和 opcode 的异常，例如：

```text
Unsupported instruction INVOKEDYNAMIC in example/Test.run()V
```

首版暂不虚拟化：

- 构造器和静态初始化器。
- 包含异常处理器的方法。
- `invokedynamic`。
- `monitorenter` / `monitorexit`。
- 已废弃的 `jsr` / `ret`。

## 后续保护

功能和语义稳定后，可以增加：

- 每次构建随机生成 opcode 映射。
- 随机化 code ID 和常量索引。
- 将 `int[]` 编码为加密的 `byte[]`。
- 压缩或拆分 CodePool。
- 把代码池存放在加密 JAR 资源中。
- 拆分 VM dispatcher 和 opcode handler。
- 对常量、owner、name 和 descriptor 分别编码。

这些功能不能先于正确性实现。BytecodeVM 的第一个里程碑是让一个简单整数方法在转换后能够被真实加载和执行，并与原始方法返回完全相同的结果。
