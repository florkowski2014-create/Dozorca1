package pl.xxxxx;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.event.server.AsyncTabCompleteEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class XxXxX extends JavaPlugin implements Listener {

    private static final String PERM = "xxxxx.dozorca";
    private static final Component UNKNOWN = Component.text("Unknown command. Type \"/help\" for help.", NamedTextColor.RED);

    /** Dozorcy aktualnie ukryci. */
    private final Set<UUID> hidden = new HashSet<>();
    /** Totemy spawnera: lokalizacja sensora -> wlasciciel. */
    private final Map<Location, UUID> totems = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("d1234").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p) || !p.hasPermission(PERM)) {
                sender.sendMessage(UNKNOWN);
                return true;
            }
            openGood(p);
            return true;
        });

        // totemy: co 10 s wither skeleton tam, gdzie patrzy dozorca
        Bukkit.getScheduler().runTaskTimer(this, this::tickTotems, 200L, 200L);
    }

    // ------------------------------------------------------------------ ukrywanie

    private void setHidden(Player d, boolean on) {
        if (on) {
            hidden.add(d.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o != d && !o.hasPermission(PERM)) o.hidePlayer(this, d);
            }
            d.setCollidable(false);
            d.setSleepingIgnored(true);
        } else {
            hidden.remove(d.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o != d) o.showPlayer(this, d);
            }
            d.setCollidable(true);
            d.setSleepingIgnored(false);
        }
    }

    private boolean isHidden(Player p) {
        return hidden.contains(p.getUniqueId());
    }

    private Set<String> hiddenNamesLower() {
        Set<String> names = new HashSet<>();
        for (UUID id : hidden) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) names.add(p.getName().toLowerCase());
        }
        return names;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(PERM)) {
            e.joinMessage(null);
            setHidden(p, true); // dozorca zawsze wchodzi niewidzialny
        } else {
            for (UUID id : hidden) {
                Player d = Bukkit.getPlayer(id);
                if (d != null) p.hidePlayer(this, d);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isHidden(p)) {
            e.quitMessage(null);
            hidden.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (isHidden(e.getPlayer())) e.deathMessage(null);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (isHidden(e.getPlayer())) e.message(null);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (isHidden(e.getPlayer())) {
            e.renderer(ChatRenderer.viewerUnaware((source, name, msg) ->
                    Component.text("<Dozorca> ", NamedTextColor.GRAY).append(msg)));
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && isHidden(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        Set<String> names = hiddenNamesLower();
        if (names.isEmpty()) return;
        e.getListedPlayers().removeIf(info -> names.contains(info.name().toLowerCase()));
        e.setNumPlayers(Math.max(0, e.getNumPlayers() - names.size()));
    }

    @EventHandler
    public void onTab(AsyncTabCompleteEvent e) {
        if (e.getSender().hasPermission(PERM)) return;
        Set<String> names = hiddenNamesLower();
        if (names.isEmpty()) return;
        e.setCompletions(e.completions().stream()
                .filter(c -> !names.contains(c.suggestion().toLowerCase()))
                .toList());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(PERM)) return;
        Set<String> names = hiddenNamesLower();
        if (names.isEmpty()) return;

        String msg = e.getMessage().substring(1);
        String cmd = msg.split(" ")[0].toLowerCase();

        // /list pokazuje tylko widocznych
        if (cmd.equals("list") || cmd.equals("minecraft:list")) {
            e.setCancelled(true);
            List<String> visible = Bukkit.getOnlinePlayers().stream()
                    .filter(o -> !isHidden(o)).map(Player::getName).toList();
            p.sendMessage("There are " + visible.size() + " of a max of "
                    + Bukkit.getMaxPlayers() + " players online: " + String.join(", ", visible));
            return;
        }

        // komendy z nickiem dozorcy (/msg, /tp, @...) -> jakby gracza nie bylo
        for (String n : names) {
            if (Pattern.compile("(?i)\\b" + Pattern.quote(n) + "\\b").matcher(msg).find()) {
                e.setCancelled(true);
                p.sendMessage(Component.text("No player was found", NamedTextColor.RED));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ panele

    private static final class Panel implements InventoryHolder {
        final boolean evil;
        Inventory inv;
        Panel(boolean evil) { this.evil = evil; }
        @Override public Inventory getInventory() { return inv; }
    }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore)
                    .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        it.setItemMeta(meta);
        return it;
    }

    private void openGood(Player p) {
        Panel panel = new Panel(false);
        Inventory inv = Bukkit.createInventory(panel, 27, Component.text("Dozorca", NamedTextColor.DARK_GREEN));
        panel.inv = inv;

        boolean on = isHidden(p);
        inv.setItem(10, item(on ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "Niewidzialnosc: " + (on ? "WLACZONA" : "wylaczona"), "Kliknij, aby przelaczyc"));
        inv.setItem(12, item(Material.MUSIC_DISC_CAT, "Plyta: Cat", "Dostaje najblizszy gracz"));
        inv.setItem(13, item(Material.MUSIC_DISC_PIGSTEP, "Plyta: Pigstep", "Dostaje najblizszy gracz"));
        inv.setItem(14, item(Material.MUSIC_DISC_OTHERSIDE, "Plyta: Otherside", "Dostaje najblizszy gracz"));
        inv.setItem(15, item(Material.MUSIC_DISC_RELIC, "Plyta: Relic", "Dostaje najblizszy gracz"));
        // prawy dolny rog -> panel zlego dozorcy
        inv.setItem(26, item(Material.WITHER_SKELETON_SKULL, "Panel zlego dozorcy", "...jesli sie odwazysz"));
        p.openInventory(inv);
    }

    private void openEvil(Player p) {
        Panel panel = new Panel(true);
        Inventory inv = Bukkit.createInventory(panel, 27, Component.text("Zly dozorca", NamedTextColor.DARK_RED));
        panel.inv = inv;

        inv.setItem(10, item(Material.SCULK_SENSOR, "Totem spawnera",
                "Daje 2x sculk + sculk sensor", "Ustaw sensor na 2 sculkach:",
                "co 10 s wither skeleton tam, gdzie patrzysz"));
        inv.setItem(12, item(Material.INK_SAC, "Ciemnosc", "Darkness 15 s graczom w 25 blokach"));
        inv.setItem(13, item(Material.LIGHTNING_ROD, "Piorun", "Piorun w najblizszego gracza"));
        inv.setItem(14, item(Material.IRON_PICKAXE, "Zmeczenie", "Mining Fatigue 30 s graczom w 25 blokach"));
        inv.setItem(18, item(Material.ARROW, "Wroc", "Panel dobrego dozorcy"));
        p.openInventory(inv);
    }

    private Player nearestOther(Player d, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player o : d.getWorld().getPlayers()) {
            if (o == d || o.hasPermission(PERM)) continue;
            double dist = o.getLocation().distanceSquared(d.getLocation());
            if (dist <= bestDist) { bestDist = dist; best = o; }
        }
        return best;
    }

    private void giveOrDrop(Player to, ItemStack it) {
        to.getInventory().addItem(it).values()
                .forEach(left -> to.getWorld().dropItemNaturally(to.getLocation(), left));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Panel panel)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission(PERM)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getSlot();
        if (!panel.evil) {
            switch (slot) {
                case 10 -> { setHidden(p, !isHidden(p)); openGood(p); }
                case 12 -> disc(p, Material.MUSIC_DISC_CAT);
                case 13 -> disc(p, Material.MUSIC_DISC_PIGSTEP);
                case 14 -> disc(p, Material.MUSIC_DISC_OTHERSIDE);
                case 15 -> disc(p, Material.MUSIC_DISC_RELIC);
                case 26 -> openEvil(p);
                default -> { }
            }
        } else {
            switch (slot) {
                case 10 -> {
                    giveOrDrop(p, new ItemStack(Material.SCULK, 2));
                    giveOrDrop(p, new ItemStack(Material.SCULK_SENSOR, 1));
                }
                case 12 -> effectNear(p, PotionEffectType.DARKNESS, 15 * 20);
                case 13 -> {
                    Player t = nearestOther(p, 60);
                    if (t != null) t.getWorld().strikeLightning(t.getLocation());
                }
                case 14 -> effectNear(p, PotionEffectType.MINING_FATIGUE, 30 * 20);
                case 18 -> openGood(p);
                default -> { }
            }
        }
    }

    private void disc(Player d, Material m) {
        Player target = nearestOther(d, 10);
        giveOrDrop(target != null ? target : d, new ItemStack(m));
    }

    private void effectNear(Player d, PotionEffectType type, int ticks) {
        for (Player o : d.getWorld().getPlayers()) {
            if (o == d || o.hasPermission(PERM)) continue;
            if (o.getLocation().distanceSquared(d.getLocation()) <= 25 * 25) {
                o.addPotionEffect(new PotionEffect(type, ticks, 0));
            }
        }
    }

    // ------------------------------------------------------------------ totem spawnera

    private boolean validTotem(Block sensor) {
        return sensor.getType() == Material.SCULK_SENSOR
                && sensor.getRelative(0, -1, 0).getType() == Material.SCULK
                && sensor.getRelative(0, -2, 0).getType() == Material.SCULK;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission(PERM)) return;
        Block b = e.getBlockPlaced();
        if (validTotem(b)) totems.put(b.getLocation(), p.getUniqueId());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        totems.remove(e.getBlock().getLocation());
    }

    private void tickTotems() {
        Iterator<Map.Entry<Location, UUID>> it = totems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, UUID> en = it.next();
            Location loc = en.getKey();
            if (loc.getWorld() == null || !loc.isChunkLoaded()) continue;
            if (!validTotem(loc.getBlock())) { it.remove(); continue; }

            Player owner = Bukkit.getPlayer(en.getValue());
            if (owner == null) continue;

            Block target = owner.getTargetBlockExact(60);
            Location spawn = target != null
                    ? target.getLocation().add(0.5, 1, 0.5)
                    : owner.getEyeLocation().add(owner.getEyeLocation().getDirection().multiply(8));
            spawn.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
        }
    }
        }
