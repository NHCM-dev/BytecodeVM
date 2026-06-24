package nhcm.bytecodevm;

import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Generator.Obfuscator;
import nhcm.bytecodevm.Utils.LogColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class BytecodeVM
{
    private static final Logger logger = LoggerFactory.getLogger(BytecodeVM.class);

    private static final String version = "1.0.0";

    private static final String defaultConfig = """
            {
              "input": "./input.jar",
              "output": "./output.jar",
              "createMode": "PER_CLASS", // ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
              "location": "SAME_PACKAGE_AS_TARGET", // SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
              "mutateMode": "ALL_RANDOM_INT", // ALL_RANDOM_INT, ALL_RESORT, ALL_AUTO_CHOOSE, NO_CHANGE
              "renameMode": "DISABLE", // ENABLE, DISABLE
              "interpretMode": "SAVE_ONLY_REQUIRED_INSTRUCTION", // SAVE_ALL_INSTRUCTION, SAVE_ONLY_REQUIRED_INSTRUCTION
              "includes": ["*", "* *(*)*"],
              "exclusions": ["* <init>(*)V", "* <clinit>()V"]
            }
            """;

    private static final String usage = """
            Usage:
            java -jar BytecodeVM.jar --config <config>
            java -jar BytecodeVM.jar --defaultconfig
            """;

    private static final String asciiArt = """
            ██████╗ ██╗   ██╗████████╗███████╗ ██████╗ ██████╗ ██████╗ ███████╗██╗   ██╗███╗   ███╗
            ██╔══██╗╚██╗ ██╔╝╚══██╔══╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝██║   ██║████╗ ████║
            ██████╔╝ ╚████╔╝    ██║   █████╗  ██║     ██║   ██║██║  ██║█████╗  ██║   ██║██╔████╔██║
            ██╔══██╗  ╚██╔╝     ██║   ██╔══╝  ██║     ██║   ██║██║  ██║██╔══╝  ╚██╗ ██╔╝██║╚██╔╝██║
            ██████╔╝   ██║      ██║   ███████╗╚██████╗╚██████╔╝██████╔╝███████╗ ╚████╔╝ ██║ ╚═╝ ██║
            ╚═════╝    ╚═╝      ╚═╝   ╚══════╝ ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝  ╚═══╝  ╚═╝     ╚═╝
            
            By NHCM, Version %s
            """.formatted(version);

    public static void main(String[] args)
    {
        System.out.println(asciiArt);
        int exitCode = run(args);
        if(exitCode != 0)
        {
            System.exit(exitCode);
        }
    }

    private static int run(String[] args)
    {
        try
        {
            if(args.length == 1 && args[0].equals("--defaultconfig"))
            {
                Files.writeString(Path.of("defaultconfig.json"), defaultConfig);
                logger.info("{}", LogColors.success("Default config saved to ./defaultconfig.json"));
                return 0;
            }
            if(args.length != 2 || !args[0].equals("--config"))
            {
                logger.info("{}", usage);
                return 1;
            }
            Path configFile = Path.of(args[1]);
            if(!Files.exists(configFile))
            {
                logger.error("{}", LogColors.error("Config file does not exist: " + LogColors.path(configFile.toAbsolutePath())));
                return 1;
            }
            logger.info("{}", LogColors.lifecycle("Starting BytecodeVM with config " + LogColors.path(configFile.toAbsolutePath())));
            Obfuscator obfuscator = new Obfuscator(BytecodeVMConfig.parse(configFile));
            obfuscator.obfuscate();
            logger.info("{}", LogColors.success("Program exiting"));
            return 0;
        }
        catch (Exception e)
        {
            logger.error(LogColors.error("Program failed"), e);
            return 1;
        }
    }
}
