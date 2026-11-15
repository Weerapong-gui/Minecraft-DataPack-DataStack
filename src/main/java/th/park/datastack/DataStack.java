package th.park.datastack;

import org.bukkit.plugin.java.JavaPlugin;

public final class DataStack extends JavaPlugin {
    public record Config(double mobRadius, double itemRadius, int maxMobStack, int maxItemStack) {}

    private static DataStack instance;
    private Config cfg;

    @Override public void onEnable() {
        instance = this;
        saveDefaultConfig();
        cfg = new Config(
            getConfig().getDouble("mob-radius", 5.0),
            getConfig().getDouble("item-radius", 2.5),
            getConfig().getInt("max-mob-stack", 100),
            getConfig().getInt("max-item-stack", 10000));
    }

    @Override public void onDisable() { instance = null; }

    public Config cfg() { return cfg; }

    public static DataStack get() { return instance; }
}
