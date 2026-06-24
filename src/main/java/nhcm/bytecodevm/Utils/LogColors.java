package nhcm.bytecodevm.Utils;

public class LogColors
{
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String RED = "\u001B[31m";

    private LogColors()
    {
    }

    public static String lifecycle(String message)
    {
        return color(CYAN, message);
    }

    public static String scan(String message)
    {
        return color(YELLOW, message);
    }

    public static String virtualize(String message)
    {
        return color(MAGENTA, message);
    }

    public static String jarRead(String message)
    {
        return color(CYAN, message);
    }

    public static String jarWrite(String message)
    {
        return color(YELLOW, message);
    }

    public static String success(String message)
    {
        return color(GREEN, message);
    }

    public static String error(String message)
    {
        return color(RED, message);
    }

    public static String path(Object path)
    {
        return String.valueOf(path);
    }

    public static String strong(Object value)
    {
        return String.valueOf(value);
    }

    private static String color(String color, String message)
    {
        return color + message + RESET;
    }
}
