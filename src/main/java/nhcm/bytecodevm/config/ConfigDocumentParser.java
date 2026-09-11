package nhcm.bytecodevm.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Safely parses YAML configuration documents. */
final class ConfigDocumentParser
{
    record MatchBlock(boolean included, Map<String, List<String>> groups)
    {
    }

    record Document(Map<String, Object> values, List<MatchBlock> matchBlocks)
    {
    }

    private ConfigDocumentParser()
    {
    }

    static Map<String, Object> parse(String document)
    {
        return parseDocument(document).values();
    }

    static Map<String, Object> parse(String document, Path source)
    {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".yaml") && !name.endsWith(".yml"))
        {
            throw new IllegalArgumentException(
                    "Unsupported config format for " + source.getFileName() +
                            "; use a .yml or .yaml file");
        }
        return parse(document);
    }

    static Document parseDocument(String document)
    {
        rejectJsonSyntax(document);
        return new Document(parseYaml(withoutMatchBlocks(document)), parseMatchBlocks(document));
    }

    static Document parseDocument(String document, Path source)
    {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".yaml") && !name.endsWith(".yml"))
        {
            throw new IllegalArgumentException(
                    "Unsupported config format for " + source.getFileName() +
                            "; use a .yml or .yaml file");
        }
        return parseDocument(document);
    }

    private static Map<String, Object> parseYaml(String document)
    {
        Object root;
        try
        {
            root = newYaml().load(document);
        }
        catch (RuntimeException exception)
        {
            throw new IllegalArgumentException("Invalid YAML configuration", exception);
        }
        if (root == null)
        {
            return Map.of();
        }
        if (!(root instanceof Map<?, ?> rootMap))
        {
            throw new IllegalArgumentException("YAML configuration root must be a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rootMap.entrySet())
        {
            if (!(entry.getKey() instanceof String key))
            {
                throw new IllegalArgumentException("YAML configuration keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Yaml newYaml()
    {
        LoaderOptions options = new LoaderOptions();
        // Repeated include/exclude blocks form an ordered decision chain.
        options.setAllowDuplicateKeys(true);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(32);
        options.setNestingDepthLimit(64);
        return new Yaml(new SafeConstructor(options));
    }

    private static List<MatchBlock> parseMatchBlocks(String document)
    {
        Node root;
        try
        {
            root = newYaml().compose(new StringReader(document));
        }
        catch (RuntimeException exception)
        {
            throw new IllegalArgumentException("Invalid YAML configuration", exception);
        }
        if (!(root instanceof MappingNode mapping))
        {
            return List.of();
        }
        List<MatchBlock> blocks = new ArrayList<>();
        for (NodeTuple tuple : mapping.getValue())
        {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode))
            {
                continue;
            }
            String key = keyNode.getValue();
            if ("includes".equals(key))
            {
                blocks.add(new MatchBlock(true, readRuleGroups(tuple.getValueNode(), key)));
            }
            else if ("excludes".equals(key) || "exclusions".equals(key))
            {
                blocks.add(new MatchBlock(false, readRuleGroups(tuple.getValueNode(), key)));
            }
        }
        return List.copyOf(blocks);
    }

    private static String withoutMatchBlocks(String document)
    {
        StringBuilder filtered = new StringBuilder(document.length());
        boolean skipping = false;
        for (String line : document.split("\\R", -1))
        {
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)))
            {
                String key = line.substring(0, line.indexOf(':') < 0 ? line.length() : line.indexOf(':')).trim();
                if ("includes".equals(key) || "excludes".equals(key) || "exclusions".equals(key))
                {
                    skipping = true;
                    continue;
                }
                if (!line.startsWith("#") && !line.isBlank())
                {
                    skipping = false;
                }
            }
            if (!skipping)
            {
                filtered.append(line).append(System.lineSeparator());
            }
        }
        return filtered.toString();
    }

    private static Map<String, List<String>> readRuleGroups(Node node, String key)
    {
        if (node instanceof SequenceNode sequence)
        {
            return Map.of("all", readRules(sequence, key));
        }
        if (!(node instanceof MappingNode mapping))
        {
            throw new IllegalArgumentException("Config value must be a list or map: " + key);
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue())
        {
            if (!(tuple.getKeyNode() instanceof ScalarNode groupNode))
            {
                throw new IllegalArgumentException("Match group names must be strings: " + key);
            }
            if (!(tuple.getValueNode() instanceof SequenceNode rulesNode))
            {
                throw new IllegalArgumentException(
                        "Match group must be a list: " + key + '.' + groupNode.getValue());
            }
            groups.put(groupNode.getValue(), readRules(rulesNode, key + '.' + groupNode.getValue()));
        }
        return Collections.unmodifiableMap(groups);
    }

    private static List<String> readRules(SequenceNode sequence, String key)
    {
        List<String> rules = new ArrayList<>();
        for (int index = 0; index < sequence.getValue().size(); index++)
        {
            Node node = sequence.getValue().get(index);
            if (!(node instanceof ScalarNode scalar) ||
                !"tag:yaml.org,2002:str".equals(scalar.getTag().getValue()))
            {
                throw new IllegalArgumentException(
                        "Config value " + key + '[' + index + "] must be a string");
            }
            rules.add(scalar.getValue());
        }
        return List.copyOf(rules);
    }

    private static void rejectJsonSyntax(String document)
    {
        for (String line : document.split("\\R"))
        {
            String content = line.stripLeading();
            if (content.isEmpty() || content.startsWith("#") || content.equals("---"))
            {
                continue;
            }
            char value = content.charAt(0);
            if (value == '{' || value == '[')
            {
                throw new IllegalArgumentException("JSON configuration is not supported; use YAML");
            }
            return;
        }
        throw new IllegalArgumentException("Configuration document is empty");
    }
}
