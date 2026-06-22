# BytecodeVM
Obfuscator obfuscate using pure java bytecode virtual machine to interpret mutated java bytecodes of original java program.
(In progress)
# Exclusion
| Expression                   | Effect                                               |
|------------------------------|------------------------------------------------------|
| *                            | Exclude all classes                                  |
| * *                          | Exclude all fields                                   |
| * * *(*)*                    | Exclude all methods with any signature               |
| * * main(*)*                 | Exclude all methods with name "main"                 |
| package.*                    | Exclude all classes in "package" and its subpackages |
| * exclude*                   | Exclude all fields name starts with "exclude"        |
| * main([Ljava/lang/String;)V | Exclude all void main(String[]) methods              |
# Config example
```json
{
  "createMode": "PER_CLASS", // ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
  "location": "SAME_PACKAGE_AS_TARGET", // SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
  "mutateMode": "ALL_RANDOM_INT", // ALL_RANDOM_INT, ALL_RESORT, ALL_AUTO_CHOOSE, NO_CHANGE
  "renameMode": "DISABLE", // ONLY_FOR_VMCLASS, ONLY_FOR_VMPACKAGE, ENABLE, DISABLE
  "exclusions": ["* <init>()V", "* main(*)*"]
}
```