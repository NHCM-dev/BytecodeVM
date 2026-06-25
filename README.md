# BytecodeVM

(Credit to GPT 5.5)

BytecodeVM is a Java bytecode virtualizing obfuscator.
It rewrites selected Java methods into a compact virtual bytecode program, injects a generated VM, and executes the protected logic through that VM at runtime.

This obfuscator is used for demo only, not for production use. 
It can make your program hundreds of times slower. 
Its purpose is to demonstrate the concept of bytecode virtualization.

## Features

- Pure Java bytecode virtualization powered by ASM.
- Configurable VM layout: one VM for all targets, per class, per method, or per package.
- Opcode mutation support for generated VM programs.
- Method include/exclusion rules for precise targeting.
- Generated VM support classes and code pools are injected into the output jar.
- Colored SLF4J + Logback console output in a Minecraft-style format.

## Requirements

- JDK 21
- Gradle wrapper included in this repository

## Build

```powershell
.\gradlew.bat build
```

The runnable fat jar is generated at:

```text
build/libs/BytecodeVM-Indev-full.jar
```

## Usage

Create a default config:

```powershell
java -jar build\libs\BytecodeVM-Indev-full.jar --defaultconfig
```

Run obfuscation:

```powershell
java -jar build\libs\BytecodeVM-Indev-full.jar --config defaultconfig.json
```

Set log level when debugging:

```powershell
java "-Dbytecodevm.log.level=DEBUG" -jar build\libs\BytecodeVM-Indev-full.jar --config defaultconfig.json
```

## Config

```jsonc
{
  "input": "./input.jar",
  "output": "./output.jar",
  "createMode": "PER_CLASS",
  "location": "SAME_PACKAGE_AS_TARGET",
  "mutateMode": "ALL_RANDOM_INT",
  "renameMode": "DISABLE",
  "interpretMode": "SAVE_ONLY_REQUIRED_INSTRUCTION",
  "includes": ["* *(*)*"],
  "exclusions": ["* <init>(*)V", "* <clinit>()V"]
}
```

### Options

| Field | Values | Description |
|---|---|---|
| `input` | Path | Input jar to transform. |
| `output` | Path | Output jar path. |
| `createMode` | `ONE_FOR_ALL`, `PER_METHOD`, `PER_CLASS`, `PER_PACKAGE` | Controls how VM classes are grouped. |
| `location` | `SAME_PACKAGE_AS_TARGET`, `NEW_PACKAGE`, `ONE_PACKAGE` | Controls where generated VM classes are placed. |
| `mutateMode` | `ALL_RANDOM_INT`, `ALL_RESORT`, `ALL_AUTO_CHOOSE`, `NO_CHANGE` | Controls opcode mutation strategy. |
| `renameMode` | `ENABLE`, `DISABLE` | Controls renaming behavior. |
| `interpretMode` | `SAVE_ALL_INSTRUCTION`, `SAVE_ONLY_REQUIRED_INSTRUCTION` | Controls how many interpreter branches are emitted. |
| `includes` | Match expressions | Methods/classes to virtualize. |
| `exclusions` | Match expressions | Methods/classes to skip. Exclusions win over includes. |

## Include / Exclude Match Expressions

`includes` and `exclusions` use the same matcher syntax. A target is selected when it matches an include rule and does not match an exclusion rule. Exclusions always win.

Wildcards are supported with `*`. Class names use dot form in config rules, while method descriptors use JVM descriptor syntax.

### Class Rules

| Expression | Effect |
|---|---|
| `*` | Match all classes. |
| `package.*` | Match classes in `package` and its subpackages. |
| `@Virtualized *` | Match classes annotated with `@Virtualized`. |
| `@com.example.Virtualized com.example.*` | Match classes in `com.example` annotated with `@com.example.Virtualized`. |
| `@Lcom/example/Virtualized; *` | Match classes annotated with descriptor form `Lcom/example/Virtualized;`. |

### Field Rules

| Expression | Effect |
|---|---|
| `* *` | Match all fields. |
| `* exclude*` | Match fields whose names start with `exclude`. |
| `com.example.* token` | Match field `token` in `com.example` classes. |
| `* @Sensitive *` | Match fields annotated with `@Sensitive`. |
| `com.example.* @Sensitive secret*` | Match annotated fields whose names start with `secret`. |

### Method Rules

| Expression | Effect |
|---|---|
| `* *(*)*` | Match all methods with any signature. |
| `* main(*)*` | Match methods named `main`. |
| `* main([Ljava/lang/String;)V` | Match `void main(String[])`. |
| `* @Virtualize *(*)*` | Match methods annotated with `@Virtualize`. |
| `com.example.* @Virtualize run(*)*` | Match annotated `run` methods in `com.example` classes. |
| `* <init>(*)V` | Match constructors. |
| `* <clinit>()V` | Match static initializers. |

Annotation matching checks both runtime-visible and runtime-invisible annotations. You may use a simple annotation name, a full class name, or JVM descriptor form:

```jsonc
{
  "includes": [
    "@Virtualized *",
    "* @Virtualize *(*)*",
    "com.example.* @com.example.Protect secret(*)*"
  ],
  "exclusions": [
    "* <init>(*)V",
    "* <clinit>()V",
    "* @DoNotVirtualize *(*)*"
  ]
}
```

## Logging

Console logs use a compact bracketed format:

```text
[20:30:33] [main/INFO ] [Obfuscator]: Scanning input file for methods to obfuscate
```

Different operation types use different message colors, such as scanning, jar reads/writes, virtualization, completion, and errors. Logs are console-only by default and are not written to `./logs`.

## Notes

- Always exclude constructors and static initializers unless you have verified the generated output carefully.
- Start with a small include pattern and expand it after testing.
- Bytecode virtualization adds runtime overhead by design. Measure your target workload after obfuscation.
- If a generated jar fails verification or launch, rerun with `-Dbytecodevm.log.level=DEBUG` and test with the smallest reproducible input jar.
