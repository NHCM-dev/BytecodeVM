package nhcm.bytecodevm.config;

import nhcm.bytecodevm.BytecodeVM;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Renders resolved values into the canonical commented default-config layout. */
final class DocumentedConfigRenderer
{
    private DocumentedConfigRenderer()
    {
    }

    static String render(BytecodeVMConfig config)
    {
        Map<String, Object> values = config.toMap();
        String[] template = BytecodeVM.defaultConfig().split("\\R", -1);
        List<String> output = new ArrayList<>(template.length + 16);
        for (int index = 0; index < template.length; index++)
        {
            String line = template[index];
            String key = topLevelKey(line);
            if (key == null || !values.containsKey(key))
            {
                output.add(line);
                continue;
            }

            Object value = values.get(key);
            if (value instanceof Map<?, ?> map)
            {
                output.addAll(renderMap(key, map));
                while (index + 1 < template.length && isChildLine(template[index + 1]))
                {
                    index++;
                }
                continue;
            }
            output.add(key + ": " + scalar(pathValue(key, value)));
        }
        return String.join(System.lineSeparator(), output);
    }

    private static List<String> renderMap(String key, Map<?, ?> map)
    {
        if (map.isEmpty())
        {
            return List.of(key + ": {}");
        }
        List<String> lines = new ArrayList<>();
        lines.add(key + ':');
        for (Map.Entry<?, ?> entry : map.entrySet())
        {
            String child = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof List<?> list)
            {
                if (list.isEmpty())
                {
                    lines.add("  " + child + ": []");
                    continue;
                }
                lines.add("  " + child + ':');
                for (Object item : list)
                {
                    lines.add("    - " + quoted(String.valueOf(item)));
                }
            }
            else
            {
                lines.add("  " + child + ": " + scalar(value));
            }
        }
        return lines;
    }

    private static String scalar(Object value)
    {
        if (value instanceof Boolean bool)
        {
            return Boolean.toString(bool);
        }
        if (value instanceof List<?> list)
        {
            return '[' + String.join(", ", list.stream().map(DocumentedConfigRenderer::scalar).toList()) + ']';
        }
        if (value instanceof Number)
        {
            return String.valueOf(value);
        }
        String text = String.valueOf(value);
        return needsQuotes(text) ? quoted(text) : text;
    }

    private static Object pathValue(String key, Object value)
    {
        if (("input".equals(key) || "output".equals(key)) && value instanceof String path)
        {
            return path.replace('\\', '/');
        }
        return value;
    }

    private static boolean needsQuotes(String value)
    {
        return value.isEmpty() ||
               value.startsWith("*") ||
               value.startsWith("!") ||
               value.contains(": ") ||
               value.contains(" #") ||
               value.indexOf('\n') >= 0 ||
               value.indexOf('\r') >= 0;
    }

    private static String quoted(String value)
    {
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + '"';
    }

    private static String topLevelKey(String line)
    {
        if (line.isBlank() || Character.isWhitespace(line.charAt(0)) || line.startsWith("#"))
        {
            return null;
        }
        int separator = line.indexOf(':');
        return separator <= 0 ? null : line.substring(0, separator).trim();
    }

    private static boolean isChildLine(String line)
    {
        return !line.isEmpty() && Character.isWhitespace(line.charAt(0));
    }
}
