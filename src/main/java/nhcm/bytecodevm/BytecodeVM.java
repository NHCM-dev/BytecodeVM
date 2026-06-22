package nhcm.bytecodevm;

import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Generator.Obfuscator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BytecodeVM
{
    private static final String defaultConfig = """
            {
              "createMode": "PER_CLASS", // ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
              "location": "SAME_PACKAGE_AS_TARGET", // SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
              "mutateMode": "ALL_RANDOM_INT", // ALL_RANDOM_INT, ALL_RESORT, ALL_AUTO_CHOOSE, NO_CHANGE
              "renameMode": "DISABLE", // ONLY_FOR_VMCLASS, ONLY_FOR_VMPACKAGE, ENABLE, DISABLE
              "exclusions": []
            }
            """;

    private static final String usage = """
            Usage:
            java -jar BytecodeVM.jar --input <input> --output <output> --config <config>
            java -jar BytecodeVM.jar --defaultconfig
            """;

    public static void main(String[] args) throws IOException
    {
        if(args.length == 1 && args[0].equals("--defaultconfig"))
        {
            Files.writeString(Path.of("defaultconfig.json"), defaultConfig);
            System.out.println("Default config saved to ./defaultconfig.json");
            return;
        }
        if(args.length < 6 || !args[0].equals("--input") || !args[2].equals("--output") || !args[4].equals("--config"))
        {
            System.out.println(usage);
            return;
        }
        Path inputFile = Path.of(args[1]);
        Path outputFile = Path.of(args[3]);
        Path configFile = Path.of(args[5]);
        if(!Files.exists(inputFile))
        {
            System.out.println("Input file does not exist");
            return;
        }
        if(!Files.exists(configFile))
        {
            System.out.println("Config file does not exist");
            return;
        }
        Obfuscator obfuscator = new Obfuscator(inputFile, outputFile, BytecodeVMConfig.parse(configFile));
        obfuscator.obfuscate();
        System.out.println("Program exiting...");
    }
}
