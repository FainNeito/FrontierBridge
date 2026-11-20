package net.lumalyte.frontier.bridge;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable, exact-snapshot tables; unknown IDs never silently become air. */
public final class BridgeMappings {
    public static final int SOURCE_PROTOCOL = 1073742170;
    public static final int TARGET_PROTOCOL = 777;
    private final int[] states;
    private final int[] blocks;
    private final int oldStateCount;
    private final int[] sounds;
    private final int[] entities;
    private final int fallingBlockEntity;
    private final java.util.Map<String,String> soundNames;

    public BridgeMappings() {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/bridge-mappings.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("protocol").getAsInt() != SOURCE_PROTOCOL || root.get("targetProtocol").getAsInt() != TARGET_PROTOCOL)
                throw new IllegalStateException("Wrong bridge protocol tables");
            states = read(root.getAsJsonArray("blockStates"));
            blocks = read(root.getAsJsonArray("blocks"));
            oldStateCount = root.get("oldStateCount").getAsInt();
            sounds = read(root.getAsJsonArray("sounds"));
            entities=read(root.getAsJsonArray("entities"));
            fallingBlockEntity=root.get("fallingBlockEntity").getAsInt();
            var names = new java.util.HashMap<String,String>();
            root.getAsJsonObject("soundFallbacks").entrySet().forEach(e -> names.put(e.getKey(),e.getValue().getAsString()));
            soundNames = java.util.Map.copyOf(names);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static int[] read(com.google.gson.JsonArray values) {
        var result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i).getAsInt();
            if (result[i] < 0) throw new IllegalStateException("Incomplete mapping at " + i);
        }
        return result;
    }

    public int state(int id) { return lookup(states, id); }
    public int block(int id) { return lookup(blocks, id); }
    public int stateCount() { return states.length; }
    public int oldStateCount() { return oldStateCount; }
    public int sound(int id) { return lookup(sounds, id); }
    public int soundCount() { return sounds.length; }
    public int entity(int id) { return lookup(entities,id); }
    public boolean fallingBlock(int id) { return id==fallingBlockEntity; }
    public String soundName(String name) { return soundNames.getOrDefault(name.contains(":")?name:"minecraft:"+name,name); }
    private static int lookup(int[] table, int id) {
        if (id < 0 || id >= table.length) throw new IllegalArgumentException("Unmapped registry ID: " + id);
        return table[id];
    }
}
