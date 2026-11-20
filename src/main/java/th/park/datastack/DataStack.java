package th.park.datastack;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import th.park.datastack.mob.MobDeathListener;

public final class DataStack extends JavaPlugin {
    public record Config(double mobRadius, double itemRadius, double pullRadius, int maxMobStack, int maxItemStack, double labelRange) {}

    private static DataStack instance;
    private Config cfg;
    private th.park.datastack.label.LabelRange labelRange;

    @Override public void onEnable() {
        instance = this;
        saveDefaultConfig();
        cfg = new Config(
            getConfig().getDouble("mob-radius", 5.0),
            getConfig().getDouble("item-radius", 2.5),
            getConfig().getDouble("pull-radius", 4.0),
            getConfig().getInt("max-mob-stack", 10000),
            getConfig().getInt("max-item-stack", 10000),
            getConfig().getDouble("label-range", 10.0));
        getServer().getPluginManager().registerEvents(new MobDeathListener(), this);
        getServer().getPluginManager().registerEvents(new th.park.datastack.mob.MobMerger(this), this);
        getServer().getPluginManager().registerEvents(new th.park.datastack.mob.MobInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new th.park.datastack.mob.SpawnCapListener(), this);
        getServer().getPluginManager().registerEvents(new th.park.datastack.item.ItemStackListener(this), this);
        if (cfg.labelRange() > 0) {
            if (getServer().getPluginManager().isPluginEnabled("packetevents")) {
                labelRange = new th.park.datastack.label.LabelRange(cfg.labelRange());
                labelRange.start(this);
            } else {
                getLogger().warning("packetevents not found: label-range ignored, labels show at any distance");
            }
        }
        // chunks loaded before enable fire no EntitiesLoadEvent for us
        getServer().getScheduler().runTask(this, () -> {
            for (var w : getServer().getWorlds()) for (var en : w.getEntities()) StackCount.relabel(en);
        });
    }

    /** Op-only debug: /datastack set <n> [selector] | /datastack info [selector]. */
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        boolean set = args[0].equalsIgnoreCase("set");
        if (!set && !args[0].equalsIgnoreCase("info")) return false;
        int selIdx = set ? 2 : 1;
        if (set && args.length < 2) return false;
        List<LivingEntity> targets = new ArrayList<>();
        if (args.length > selIdx) {
            try {
                for (Entity e : Bukkit.selectEntities(sender, args[selIdx]))
                    if (e instanceof LivingEntity le && !(e instanceof Player)) targets.add(le);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage("bad selector: " + ex.getMessage());
                return true;
            }
        } else if (sender instanceof Player p) {
            Entity t = p.getTargetEntity(6);
            if (t instanceof LivingEntity le && !(t instanceof Player)) targets.add(le);
        }
        if (targets.isEmpty()) { sender.sendMessage("no mob found"); return true; }
        for (LivingEntity le : targets) {
            if (set) {
                long n;
                try { n = Long.parseLong(args[1]); } catch (NumberFormatException ex) { return false; }
                StackCount.set(le, Math.max(1, n));
            }
            sender.sendMessage("[DataStack] " + le.getType() + " " + le.getUniqueId()
                + " count=" + StackCount.get(le) + " health=" + le.getHealth());
        }
        return true;
    }

    @Override public void onDisable() {
        if (labelRange != null) { labelRange.stop(); labelRange = null; }
        th.park.datastack.mob.MobMerger.clear();
        th.park.datastack.mob.MobInteractListener.clear();
        instance = null;
    }

    public Config cfg() { return cfg; }

    public static DataStack get() { return instance; }
}
