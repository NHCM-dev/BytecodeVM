package nhcm.bytecodevm.Config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Builder
public class BytecodeVMConfig
{
    public final Path inputFile;
    public final Path outputFile;
    public final VMCreateMode createMode;
    public final VMLocation location;
    public final MutateMode mutateMode;
    public final RenameMode renameMode;
    public final InterpretMode interpretMode;
    public final String[] includes;
    public final String[] exclusions;

    public enum VMCreateMode
    {
        ONE_FOR_ALL,
        PER_METHOD,
        PER_CLASS,
        PER_PACKAGE
    }

    public enum VMLocation
    {
        SAME_PACKAGE_AS_TARGET,
        NEW_PACKAGE,
        ONE_PACKAGE
    }

    public enum MutateMode
    {
        ALL_RANDOM_INT,
        ALL_RESORT,
        ALL_AUTO_CHOOSE,
        NO_CHANGE
    }

    public enum RenameMode
    {
        ENABLE,
        DISABLE
    }

    public enum InterpretMode
    {
        SAVE_ALL_INSTRUCTION,
        SAVE_ONLY_REQUIRED_INSTRUCTION
    }

    public static BytecodeVMConfig parse(Path file) throws IOException
    {
        JsonObject json = new Gson().fromJson(Files.newBufferedReader(file), JsonObject.class);
        JsonArray exclusionsArr = requiredArray(json, "exclusions");
        String[] exclusions = new String[exclusionsArr.size()];
        for(int i = 0; i < exclusionsArr.size(); i++)
        {
            exclusions[i] = exclusionsArr.get(i).getAsString();
        }
        JsonArray includesArr = requiredArray(json, "includes");
        String[] includes = new String[includesArr.size()];
        for(int i = 0; i < includesArr.size(); i++)
        {
            includes[i] = includesArr.get(i).getAsString();
        }
        return BytecodeVMConfig
                .builder()
                .inputFile(Path.of(requiredString(json, "input")))
                .outputFile(Path.of(requiredString(json, "output")))
                .createMode(VMCreateMode.valueOf(requiredString(json, "createMode")))
                .location(VMLocation.valueOf(requiredString(json, "location")))
                .mutateMode(MutateMode.valueOf(requiredString(json, "mutateMode")))
                .interpretMode(InterpretMode.valueOf(requiredString(json, "interpretMode")))
                .renameMode(RenameMode.valueOf(requiredString(json, "renameMode")))
                .includes(includes)
                .exclusions(exclusions).build();
    }

    private static String requiredString(JsonObject json, String key)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            throw new IllegalArgumentException("Missing required config value: " + key);
        }
        return value.getAsString();
    }

    private static JsonArray requiredArray(JsonObject json, String key)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            throw new IllegalArgumentException("Missing required config array: " + key);
        }
        if(!value.isJsonArray())
        {
            throw new IllegalArgumentException("Config value must be an array: " + key);
        }
        return value.getAsJsonArray();
    }
}
