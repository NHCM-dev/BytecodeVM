package nhcm.bytecodevm.Config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Builder
public class BytecodeVMConfig
{
    public final VMCreateMode createMode;
    public final VMLocation location;
    public final MutateMode mutateMode;
    public final RenameMode renameMode;
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
        ONLY_FOR_VMCLASS,
        ONLY_FOR_VMPACKAGE,
        ENABLE,
        DISABLE
    }

    public static BytecodeVMConfig parse(Path file) throws IOException
    {
        JsonObject json = new Gson().fromJson(Files.newBufferedReader(file), JsonObject.class);
        JsonArray exclusionsArr = json.getAsJsonArray("exclusions");
        String[] exclusions = new String[exclusionsArr.size()];
        for(int i = 0; i < exclusionsArr.size(); i++)
        {
            exclusions[i] = exclusionsArr.get(i).getAsString();
        }
        return BytecodeVMConfig.builder()
                .createMode(VMCreateMode.valueOf(json.get("createMode").getAsString()))
                .location(VMLocation.valueOf(json.get("location").getAsString()))
                .mutateMode(MutateMode.valueOf(json.get("mutateMode").getAsString()))
                .renameMode(RenameMode.valueOf(json.get("renameMode").getAsString()))
                .exclusions(exclusions).build();
    }

    public void saveConfigTo(Path file) throws IOException
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject json = new JsonObject();
        json.addProperty("createMode", createMode.name());
        json.addProperty("location", location.name());
        json.addProperty("mutateMode", mutateMode.name());
        json.addProperty("renameMode", renameMode.name());
        JsonArray exclusionsArr = new JsonArray();
        for(String exclusion : exclusions)
        {
            exclusionsArr.add(exclusion);
        }
        json.add("exclusions", exclusionsArr);
        Files.writeString(file, gson.toJson(json));
    }
}
