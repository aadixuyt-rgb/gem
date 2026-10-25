package pl.gemshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("gemshop.json"); }

    public int accent = 3;          // 0 niebieski, 1 zolty, 2 zielony, 3 rozowy, 4 fiolet
    public int theme = 0;           // 0 Midnight, 1 Glass, 2 Frost
    public boolean closeFade = true; // false = Rozpad
    public boolean animations = true;
    public boolean parallax = true;
    public boolean blur = true;

    public static ModConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ModConfig c = GSON.fromJson(Files.readString(p), ModConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) { }
        ModConfig c = new ModConfig();
        c.save();
        return c;
    }

    public void save() {
        try { Files.writeString(path(), GSON.toJson(this)); } catch (Exception ignored) { }
    }
}
