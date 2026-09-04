package nhcm.bytecodevm.cli;

import nhcm.bytecodevm.BuildInfo;
import nhcm.bytecodevm.BytecodeVM;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.PresetConfigGallery;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.ObfuscationReport;
import nhcm.bytecodevm.generator.Obfuscator;
import nhcm.bytecodevm.sdk.watermark.WatermarkInfo;
import nhcm.bytecodevm.sdk.watermark.WatermarkReader;
import nhcm.bytecodevm.utils.LogColors;
import nhcm.bytecodevm.utils.RandomUtils;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "java -jar BytecodeVM.jar",
        customSynopsis = "java -jar BytecodeVM.jar <command> [options]",
        description = "Protect Java applications with bytecode virtualization.",
        synopsisSubcommandLabel = "COMMAND",
        optionListHeading = "%nOptions:%n",
        commandListHeading = "%nCommands:%n",
        sortOptions = false,
        usageHelpAutoWidth = true,
        versionProvider = BytecodeVMCLI.VersionProvider.class,
        subcommands = {
                BytecodeVMCLI.ProtectCommand.class,
                BytecodeVMCLI.InitCommand.class,
                BytecodeVMCLI.PresetCommand.class,
                BytecodeVMCLI.ValidateCommand.class,
                BytecodeVMCLI.InspectCommand.class,
                BytecodeVMCLI.WatermarkCommand.class
        })
public final class BytecodeVMCLI implements Callable<Integer>
{
    private static final Logger logger = LoggerFactory.getLogger("CLI");

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @CommandLine.Option(
            names = {"-V", "--version"},
            versionHelp = true,
            description = "Print version information and exit.",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean versionRequested;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Show detailed planning and generation logs.",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean verbose;

    @CommandLine.Option(
            names = {"-q", "--quiet"},
            description = "Only print errors and explicitly requested output.",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean quiet;

    @CommandLine.Option(
            names = "--log-file",
            paramLabel = "<file>",
            description = "Also write logs to this file.",
            scope = CommandLine.ScopeType.INHERIT)
    private Path logFile;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call()
    {
        printRootUsage(spec.commandLine());
        return CLIExitCodes.USAGE;
    }

    public static int execute(String[] arguments)
    {
        BytecodeVMCLI application = new BytecodeVMCLI();
        CommandLine commandLine = new CommandLine(application);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setUsageHelpWidth(100);
        String requestedCommandHelp = requestedCommandHelp(arguments);
        if (requestedCommandHelp != null)
        {
            logUsage(commandLine.getSubcommands().get(requestedCommandHelp));
            return CLIExitCodes.SUCCESS;
        }
        String[] normalizedArguments = normalizeArguments(arguments);
        if (requestsRootHelp(normalizedArguments))
        {
            printRootUsage(commandLine);
            return CLIExitCodes.SUCCESS;
        }
        commandLine.setParameterExceptionHandler((exception, ignored) -> {
            CommandLine failedCommand = exception.getCommandLine();
            logCliError(exception.getMessage());
            if (failedCommand == commandLine)
            {
                printRootUsage(failedCommand);
            }
            else
            {
                logUsage(failedCommand);
            }
            return CLIExitCodes.USAGE;
        });
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            int exitCode = exception instanceof CLIException cli
                    ? cli.exitCode()
                    : CLIExitCodes.FAILURE;
            String message = exception.getMessage();
            logCliError(message == null ? exception.getClass().getSimpleName() : message);
            if (application.verbose && exitCode != CLIExitCodes.USAGE)
            {
                Throwable detail = exception.getCause() == null ? exception : exception.getCause();
                LoggerFactory.getLogger(BytecodeVMCLI.class).error("Command failed", detail);
                Path details = application.resolvedLogFile();
                if (details != null)
                {
                    logger.error("{}", LogColors.error("Details also written to " + details));
                }
            }
            return exitCode;
        });
        return commandLine.execute(normalizedArguments);
    }

    private static String requestedCommandHelp(String[] arguments)
    {
        boolean help = false;
        String command = null;
        for (String argument : arguments)
        {
            if ("-h".equals(argument) || "--help".equals(argument))
            {
                help = true;
            }
            else if (isCommand(argument))
            {
                command = argument.toLowerCase();
            }
        }
        return help ? command : null;
    }

    private static boolean requestsRootHelp(String[] arguments)
    {
        boolean help = false;
        for (String argument : arguments)
        {
            if (isCommand(argument))
            {
                return false;
            }
            if ("-h".equals(argument) || "--help".equals(argument))
            {
                help = true;
            }
        }
        return help;
    }

    private static void printRootUsage(CommandLine root)
    {
        logCliBlock("""
                Usage:
                  java -jar BytecodeVM.jar <command> [options]

                Commands:
                  protect <source> [options]   Virtualize matched methods and write a protected JAR.
                  inspect <source> [options]   Preview include matches and VM allocation.
                  validate <source> [options]  Validate a config and input without writing a JAR.
                  init [config.yml] [options]  Write the documented default configuration.
                  preset [name] [file]         List or write a documented preset configuration.
                  watermark <jar> [options]   Read and verify an embedded watermark.

                Common options:
                  -h, --help                   Show help for the current command.
                  -V, --version                Print the BytecodeVM version.
                  -v, --verbose                Show detailed planning and generation logs.
                  -q, --quiet                  Only print errors and requested output.
                      --log-file <file>         Also write logs to a file.

                Run `java -jar BytecodeVM.jar --help <command>` for command-specific options.
                """);
    }

    private static void logUsage(CommandLine commandLine)
    {
        StringWriter buffer = new StringWriter();
        commandLine.usage(new PrintWriter(buffer));
        logCliBlock(buffer.toString());
    }

    private static void logCliBlock(String message)
    {
        logger.info("{}", message.stripTrailing());
    }

    private static void logCliError(String message)
    {
        logger.error("{}", LogColors.error("Error: " + message));
    }

    private static String[] normalizeArguments(String[] arguments)
    {
        if (arguments.length == 0)
        {
            return arguments;
        }
        List<String> normalized = new ArrayList<>();
        switch (arguments[0])
        {
            case "--config" -> {
                normalized.add("protect");
                normalized.addAll(Arrays.asList(arguments));
            }
            case "--defaultconfig" -> {
                normalized.add("init");
                normalized.addAll(Arrays.asList(arguments).subList(1, arguments.length));
            }
            case "--defaultrun" -> {
                normalized.add("protect");
                normalized.addAll(Arrays.asList(arguments).subList(1, arguments.length));
            }
            default -> {
                return arguments;
            }
        }
        return normalized.toArray(String[]::new);
    }

    private static boolean isCommand(String value)
    {
        return switch (value.toLowerCase())
        {
            case "protect", "init", "preset", "validate", "inspect", "watermark" -> true;
            default -> false;
        };
    }

    private void begin()
    {
        CLIRuntime.configure(verbose, quiet, logFile);
    }

    private boolean quiet()
    {
        return quiet;
    }

    private void finish()
    {
        Path resolved = resolvedLogFile();
        if (resolved != null && !quiet)
        {
            logger.info("{}", LogColors.success("Log written to " + resolved));
        }
    }

    private Path resolvedLogFile()
    {
        Path resolved = logFile;
        return resolved == null ? null : resolved.toAbsolutePath().normalize();
    }

    public static final class VersionProvider implements CommandLine.IVersionProvider
    {
        @Override
        public String[] getVersion()
        {
            return new String[]{"BytecodeVM " + BuildInfo.VERSION};
        }
    }

    private abstract static class ConfigCommand implements Callable<Integer>
    {
        @CommandLine.ParentCommand
        protected BytecodeVMCLI parent;

        @CommandLine.Option(names = {"-c", "--config"}, paramLabel = "<file>", description = "Use this YAML configuration file.")
        protected Path configFile;

        @CommandLine.Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<source>",
                description = "YAML configuration, or an input JAR using default settings.")
        protected Path source;

        @CommandLine.Option(names = {"-i", "--input"}, paramLabel = "<jar>", description = "Override the input JAR from the configuration.")
        protected Path inputOverride;

        @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<jar>", description = "Override the output JAR from the configuration.")
        protected Path outputOverride;

        @CommandLine.Option(names = "--report", paramLabel = "<json>", description = "Write the complete result as JSON.")
        protected Path reportPath;

        @CommandLine.Option(
                names = "--watermark",
                paramLabel = "<key=value>",
                description = "Add or override a custom watermark field (repeatable).")
        protected Map<String, String> watermarkOverrides = new LinkedHashMap<>();

        protected void begin()
        {
            parent.begin();
        }

        protected BytecodeVMConfig loadConfig()
        {
            Path resolvedConfigFile = configFile;
            Path positionalInput = null;
            if (source != null && isYaml(source))
            {
                if (configFile != null)
                {
                    throw new CLIException(CLIExitCodes.USAGE, "Specify the config as a positional argument or --config, not both");
                }
                resolvedConfigFile = source;
            }
            else
            {
                positionalInput = source;
            }
            if (positionalInput != null && inputOverride != null)
            {
                throw new CLIException(CLIExitCodes.USAGE, "Specify the input as a positional argument or --input, not both");
            }
            Path input = inputOverride != null ? inputOverride : positionalInput;
            try
            {
                BytecodeVMConfig config;
                if (resolvedConfigFile != null)
                {
                    if (!Files.isRegularFile(resolvedConfigFile))
                    {
                        throw new CLIException(
                                CLIExitCodes.CONFIG,
                                "Config file does not exist: " + resolvedConfigFile.toAbsolutePath());
                    }
                    config = BytecodeVMConfig.parse(resolvedConfigFile);
                    config = config.withPaths(input, outputOverride);
                }
                else
                {
                    if (input == null)
                    {
                        throw new CLIException(
                                CLIExitCodes.USAGE,
                                "Specify --config <file> or an input JAR");
                    }
                    Path output = outputOverride == null ? defaultOutput(input) : outputOverride;
                    config = BytecodeVMConfig.parse(
                            BytecodeVM.defaultConfig(),
                            input.toString(),
                            output.toString());
                }
                if (!watermarkOverrides.isEmpty())
                {
                    Map<String, String> watermark = new LinkedHashMap<>(config.watermark);
                    watermark.putAll(watermarkOverrides);
                    config = config.toBuilder().watermark(Map.copyOf(watermark)).build();
                }
                return config;
            }
            catch (CLIException exception)
            {
                throw exception;
            }
            catch (IOException | IllegalArgumentException exception)
            {
                throw new CLIException(CLIExitCodes.CONFIG, "Invalid configuration: " + exception.getMessage(), exception);
            }
        }

        protected void prepare(BytecodeVMConfig config, boolean writingOutput)
        {
            validatePaths(config, writingOutput);
            prepareReportPath(config);
            RandomUtils.useSecureRandom();
            VMStructure.resetAutomaticSelection();
            logger.debug("Effective configuration: input={}, output={}, structure={}, vmCount={}",
                    config.inputFile.toAbsolutePath(),
                    config.outputFile.toAbsolutePath(),
                    config.vmStructure,
                    config.vmCount);
        }

        protected void writeReport(ObfuscationReport report)
        {
            if (reportPath == null)
            {
                return;
            }
            try
            {
                report.writeJson(reportPath);
                if (!parent.quiet())
                {
                    logger.info("{}", LogColors.success(
                            "Report written to " + reportPath.toAbsolutePath().normalize()));
                }
            }
            catch (IOException exception)
            {
                throw new CLIException(
                        CLIExitCodes.GENERATION,
                        "Cannot write report: " + reportPath.toAbsolutePath(),
                        exception);
            }
        }

        private void prepareReportPath(BytecodeVMConfig config)
        {
            if (reportPath == null)
            {
                return;
            }
            Path report = reportPath.toAbsolutePath().normalize();
            if (report.equals(config.inputFile.toAbsolutePath().normalize()) ||
                report.equals(config.outputFile.toAbsolutePath().normalize()))
            {
                throw new CLIException(
                        CLIExitCodes.CONFIG,
                        "Report path must differ from input and output paths: " + report);
            }
        }

        protected static boolean isYaml(Path path)
        {
            String name = path.getFileName() == null
                    ? path.toString()
                    : path.getFileName().toString();
            String lower = name.toLowerCase();
            return lower.endsWith(".yml") || lower.endsWith(".yaml");
        }

        private static Path defaultOutput(Path input)
        {
            Path fileName = input.getFileName();
            String name = fileName == null ? input.toString() : fileName.toString();
            String base = name.toLowerCase().endsWith(".jar")
                    ? name.substring(0, name.length() - 4)
                    : name;
            Path parent = input.getParent();
            Path output = Path.of(base + "-bytecodevm.jar");
            return parent == null ? output : parent.resolve(output);
        }
    }

    @CommandLine.Command(
            name = "protect",
            customSynopsis = "java -jar BytecodeVM.jar protect <source> [options]",
            description = "Virtualize matched methods and write the protected JAR.",
            optionListHeading = "%nOptions:%n",
            parameterListHeading = "%nInput:%n",
            sortOptions = false,
            usageHelpAutoWidth = true)
    static final class ProtectCommand extends ConfigCommand
    {
        @Override
        public Integer call()
        {
            begin();
            BytecodeVMConfig config = loadConfig();
            prepare(config, true);
            ObfuscationReport report;
            try
            {
                report = new Obfuscator(config).obfuscate();
            }
            catch (NoSuchFileException exception)
            {
                throw new CLIException(CLIExitCodes.INPUT, "Input file does not exist: " + config.inputFile.toAbsolutePath(), exception);
            }
            catch (CLIException exception)
            {
                throw exception;
            }
            catch (IllegalStateException exception)
            {
                throw new CLIException(CLIExitCodes.GENERATION, "VM generation failed: " + exception.getMessage(), exception);
            }
            catch (Exception exception)
            {
                throw new CLIException(CLIExitCodes.GENERATION, "Protection failed: " + exception.getMessage(), exception);
            }
            writeReport(report);
            if (!parent.quiet())
            {
                logSummary(report);
            }
            parent.finish();
            return CLIExitCodes.SUCCESS;
        }
    }

    @CommandLine.Command(
            name = "validate",
            customSynopsis = "java -jar BytecodeVM.jar validate <source> [options]",
            description = "Validate the configuration and input without generating output.",
            optionListHeading = "%nOptions:%n",
            parameterListHeading = "%nInput:%n",
            sortOptions = false,
            usageHelpAutoWidth = true)
    static final class ValidateCommand extends ConfigCommand
    {
        @Override
        public Integer call()
        {
            begin();
            BytecodeVMConfig config = loadConfig();
            prepare(config, false);
            try
            {
                ObfuscationReport report = new Obfuscator(config).inspect();
                writeReport(report);
                if (!parent.quiet())
                {
                    logger.info("{}", LogColors.success("Configuration is valid: " +
                            (configFile == null && (source == null || !isYaml(source))
                                    ? "default configuration"
                                    : (configFile == null ? source : configFile).toAbsolutePath().normalize())));
                }
                parent.finish();
                return CLIExitCodes.SUCCESS;
            }
            catch (NoSuchFileException exception)
            {
                throw new CLIException(CLIExitCodes.INPUT, "Input file does not exist: " + config.inputFile.toAbsolutePath(), exception);
            }
            catch (CLIException exception)
            {
                throw exception;
            }
            catch (Exception exception)
            {
                throw new CLIException(CLIExitCodes.CONFIG, "Validation failed: " + exception.getMessage(), exception);
            }
        }
    }

    @CommandLine.Command(
            name = "inspect",
            customSynopsis = "java -jar BytecodeVM.jar inspect <source> [options]",
            description = "Show a concise include-match and VM allocation summary.",
            optionListHeading = "%nOptions:%n",
            parameterListHeading = "%nInput:%n",
            sortOptions = false,
            usageHelpAutoWidth = true)
    static final class InspectCommand extends ConfigCommand
    {
        @Override
        public Integer call()
        {
            begin();
            BytecodeVMConfig config = loadConfig();
            prepare(config, false);
            try
            {
                ObfuscationReport report = new Obfuscator(config).inspect();
                writeReport(report);
                if (!parent.quiet())
                {
                    logSummary(report);
                }
                parent.finish();
                return CLIExitCodes.SUCCESS;
            }
            catch (NoSuchFileException exception)
            {
                throw new CLIException(CLIExitCodes.INPUT, "Input file does not exist: " + config.inputFile.toAbsolutePath(), exception);
            }
            catch (CLIException exception)
            {
                throw exception;
            }
            catch (Exception exception)
            {
                throw new CLIException(CLIExitCodes.CONFIG, "Inspection failed: " + exception.getMessage(), exception);
            }
        }
    }

    @CommandLine.Command(
            name = "watermark",
            customSynopsis = "java -jar BytecodeVM.jar watermark <jar> [options]",
            description = "Read and verify a watermark embedded in generated bytecode.",
            optionListHeading = "%nOptions:%n",
            parameterListHeading = "%nInput:%n",
            sortOptions = false,
            usageHelpAutoWidth = true)
    static final class WatermarkCommand implements Callable<Integer>
    {
        @CommandLine.ParentCommand
        private BytecodeVMCLI parent;

        @CommandLine.Parameters(
                index = "0",
                paramLabel = "<jar>",
                description = "Protected JAR whose embedded watermark should be read.")
        private Path jar;

        @CommandLine.Option(
                names = "--json",
                description = "Print watermark fields as JSON.")
        private boolean json;

        @Override
        public Integer call()
        {
            parent.begin();
            if (!Files.isRegularFile(jar))
            {
                throw new CLIException(CLIExitCodes.INPUT, "Input JAR does not exist: " + jar.toAbsolutePath());
            }
            try
            {
                WatermarkInfo watermark = WatermarkReader.read(jar);
                if (json)
                {
                    System.out.println(new GsonBuilder()
                            .setPrettyPrinting()
                            .disableHtmlEscaping()
                            .create()
                            .toJson(watermark.values()));
                }
                else
                {
                    logger.info("{}", LogColors.success("Watermark verified"));
                    watermark.values().forEach((key, value) -> logger.info("  {}: {}", key, value));
                }
                parent.finish();
                return CLIExitCodes.SUCCESS;
            }
            catch (IOException exception)
            {
                throw new CLIException(
                        CLIExitCodes.INPUT,
                        "Cannot read watermark: " + exception.getMessage(),
                        exception);
            }
        }
    }

    @CommandLine.Command(
            name = "init",
            customSynopsis = "java -jar BytecodeVM.jar init [config.yml] [options]",
            description = "Write the documented default YAML configuration.",
            optionListHeading = "%nOptions:%n",
            parameterListHeading = "%nOutput:%n",
            sortOptions = false,
            usageHelpAutoWidth = true)
    static final class InitCommand implements Callable<Integer>
    {
        @CommandLine.ParentCommand
        private BytecodeVMCLI parent;

        @CommandLine.Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<file>",
                description = "Configuration file to write (default: defaultconfig.yml).")
        private Path positionalOutput;

        @CommandLine.Option(
                names = {"-o", "--output"},
                paramLabel = "<file>",
                description = "Configuration output path.")
        private Path outputOption;

        @Override
        public Integer call()
        {
            parent.begin();
            if (positionalOutput != null && outputOption != null)
            {
                throw new CLIException(CLIExitCodes.USAGE, "Specify the output positionally or with --output, not both");
            }
            Path output = outputOption != null
                    ? outputOption
                    : positionalOutput != null ? positionalOutput : Path.of("defaultconfig.yml");
            try
            {
                Path absolute = output.toAbsolutePath();
                Path directory = absolute.getParent();
                if (directory != null)
                {
                    Files.createDirectories(directory);
                }
                Files.writeString(absolute, BytecodeVM.defaultConfig());
                if (!parent.quiet())
                {
                    logger.info("{}", LogColors.success("Config written to " + absolute));
                }
                parent.finish();
                return CLIExitCodes.SUCCESS;
            }
            catch (IOException exception)
            {
                throw new CLIException(CLIExitCodes.GENERATION, "Cannot write config: " + output.toAbsolutePath(), exception);
            }
            catch (IllegalArgumentException exception)
            {
                throw new CLIException(CLIExitCodes.CONFIG, exception.getMessage(), exception);
            }
        }

    }

    @CommandLine.Command(
            name = "preset",
            customSynopsis = "java -jar BytecodeVM.jar preset [name] [config.yml] [options]",
            description = "List presets or write a documented preset YAML configuration.",
            optionListHeading = "%nOptions:%n",
            parameterListHeading = "%nArguments:%n",
            sortOptions = false,
            usageHelpAutoWidth = true)
    static final class PresetCommand implements Callable<Integer>
    {
        @CommandLine.ParentCommand
        private BytecodeVMCLI parent;

        @CommandLine.Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<name>",
                description = "Preset or concrete VMStructure name. Omit to list presets.")
        private String preset;

        @CommandLine.Parameters(
                index = "1",
                arity = "0..1",
                paramLabel = "<file>",
                description = "Configuration output path (default: <preset>.yml).")
        private Path positionalOutput;

        @CommandLine.Option(
                names = {"-o", "--output"},
                paramLabel = "<file>",
                description = "Configuration output path.")
        private Path outputOption;

        @CommandLine.Option(
                names = "--vm-structure",
                paramLabel = "<structure>",
                description = "Override the preset VM structure or automatic tier.")
        private String vmStructure;

        @CommandLine.Option(
                names = "--vm-count",
                paramLabel = "<count>",
                description = "Override the generated VM count (1-1024).")
        private Integer vmCount;

        @Override
        public Integer call()
        {
            parent.begin();
            if (preset == null)
            {
                if (positionalOutput != null || outputOption != null ||
                    vmStructure != null || vmCount != null)
                {
                    throw new CLIException(CLIExitCodes.USAGE, "Specify a preset name before preset options");
                }
                CLIRuntime.configure(false, false, parent.resolvedLogFile());
                logPresetGallery();
                parent.finish();
                return CLIExitCodes.SUCCESS;
            }
            if (positionalOutput != null && outputOption != null)
            {
                throw new CLIException(CLIExitCodes.USAGE, "Specify the output positionally or with --output, not both");
            }

            Path output = outputOption != null
                    ? outputOption
                    : positionalOutput != null
                            ? positionalOutput
                            : Path.of(defaultFileName(preset));
            try
            {
                BytecodeVMConfig config = customizedConfig();
                Path absolute = output.toAbsolutePath();
                Path directory = absolute.getParent();
                if (directory != null)
                {
                    Files.createDirectories(directory);
                }
                Files.writeString(absolute, config.toYaml());
                if (!parent.quiet())
                {
                    logger.info("{}", LogColors.success(
                            "Preset " + preset.toUpperCase(Locale.ROOT) + " written to " + absolute));
                }
                parent.finish();
                return CLIExitCodes.SUCCESS;
            }
            catch (IOException exception)
            {
                throw new CLIException(
                        CLIExitCodes.GENERATION,
                        "Cannot write preset config: " + output.toAbsolutePath(),
                        exception);
            }
            catch (IllegalArgumentException exception)
            {
                throw new CLIException(CLIExitCodes.CONFIG, exception.getMessage(), exception);
            }
        }

        private BytecodeVMConfig customizedConfig()
        {
            BytecodeVMConfig.BytecodeVMConfigBuilder builder = PresetConfigGallery.create(
                            preset,
                            Path.of("./input.jar"),
                            Path.of("./output.jar"))
                    .toBuilder();
            if (vmStructure != null)
            {
                VMStructure structure;
                try
                {
                    structure = VMStructure.parse(vmStructure);
                }
                catch (RuntimeException exception)
                {
                    throw new IllegalArgumentException("Unknown VM structure: " + vmStructure, exception);
                }
                builder.vmStructure(structure);
                if (!structure.isAutomatic() && vmCount == null)
                {
                    builder.vmCount(1);
                }
            }
            if (vmCount != null)
            {
                if (vmCount < 1 || vmCount > 1024)
                {
                    throw new IllegalArgumentException("VM count must be between 1 and 1024");
                }
                builder.vmCount(vmCount);
            }
            return builder.build();
        }

        private static String defaultFileName(String name)
        {
            return name.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace('_', '-')
                    .replaceAll("[^a-z0-9.-]+", "-") + ".yml";
        }

        private static void logPresetGallery()
        {
            logger.info("{}", LogColors.lifecycle("Available configuration presets:"));
            for (PresetConfigGallery.Preset available : PresetConfigGallery.presets())
            {
                logger.info("  {} - {}", available.name(), available.description());
            }
            logger.info("{}", LogColors.lifecycle(
                    "Concrete VMStructure names are also accepted, for example: preset GRAPH graph.yml"));
        }
    }

    private static void validatePaths(BytecodeVMConfig config, boolean writingOutput)
    {
        Path input = config.inputFile.toAbsolutePath().normalize();
        Path output = config.outputFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(input) || !Files.isReadable(input))
        {
            throw new CLIException(CLIExitCodes.INPUT, "Input JAR is not readable: " + input);
        }
        if (input.equals(output))
        {
            throw new CLIException(CLIExitCodes.CONFIG, "Input and output paths must be different: " + input);
        }
        if (!writingOutput)
        {
            return;
        }
        if (Files.isDirectory(output))
        {
            throw new CLIException(CLIExitCodes.GENERATION, "Output path is a directory: " + output);
        }
        Path parent = output.getParent();
        try
        {
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
        }
        catch (IOException exception)
        {
            throw new CLIException(CLIExitCodes.GENERATION, "Cannot create output directory: " + parent, exception);
        }
        if (parent != null && !Files.isWritable(parent))
        {
            throw new CLIException(CLIExitCodes.GENERATION, "Output directory is not writable: " + parent);
        }
    }

    private static void logSummary(ObfuscationReport report)
    {
        logger.info("{}", LogColors.lifecycle("Input: " + report.input()));
        if (report.output() != null)
        {
            logger.info("{}", LogColors.lifecycle("Output: " + report.output()));
        }
        logger.info("{}", LogColors.success(
                "Protected methods: " + report.matchedMethods()));
        logger.info("{}", LogColors.virtualize(
                "VM sets: " + report.vmSetCount() + " " + report.structures()));

        int visibleVmSets = Math.min(report.vmSets().size(), 12);
        for (int index = 0; index < visibleVmSets; index++)
        {
            ObfuscationReport.VMSet vmSet = report.vmSets().get(index);
            logger.info("{}", LogColors.virtualize(
                    "VM " + vmSet.name() + " [" + vmSet.structure() + "]: " +
                            vmSet.methodCount() + " method(s)"));
        }
        if (report.vmSets().size() > visibleVmSets)
        {
            logger.info("{}", LogColors.virtualize(
                    (report.vmSets().size() - visibleVmSets) +
                            " more VM set(s); use --report for the complete plan"));
        }
        logSdkStructureOverrides(report);
        if ("protect".equals(report.mode()))
        {
            logger.info("{}", LogColors.success(
                    "Generated classes: " + report.generatedClasses() +
                            ", elapsed: " + report.elapsedMillis() + " ms"));
            if (report.outputVerified())
            {
                logger.info("{}", LogColors.success("Output verification passed"));
            }
        }
    }

    private static void logSdkStructureOverrides(ObfuscationReport report)
    {
        Object configuredValue = report.effectiveConfig().get("vmStructure");
        if (!(configuredValue instanceof String configuredName))
        {
            return;
        }
        VMStructure configured = VMStructure.parse(configuredName);
        Map<String, Integer> overrides = new java.util.LinkedHashMap<>();
        for (ObfuscationReport.MethodPlan method : report.methods())
        {
            if (!"SDK_ANNOTATION".equals(method.selection()))
            {
                continue;
            }
            VMStructure resolved = VMStructure.parse(method.structure());
            if (!configured.acceptsResolvedStructure(resolved))
            {
                overrides.merge(resolved.name(), 1, Integer::sum);
                logger.debug(
                        "SDK structure override: {}.{}{} -> {}",
                        method.owner().replace('/', '.'),
                        method.name(),
                        method.descriptor(),
                        resolved);
            }
        }
        if (!overrides.isEmpty())
        {
            int count = overrides.values().stream().mapToInt(Integer::intValue).sum();
            logger.warn("{}", LogColors.scan(
                    "SDK annotations override configured " + configured +
                            " for " + count + " method(s): " + overrides));
        }
    }
}
