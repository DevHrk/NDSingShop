package me.nd.loja.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.nd.loja.Main;

import org.bukkit.Material;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ArmorsBonus extends Commands {
    private static File armorsFile = new File(Main.get().getDataFolder(), "armors.yml");
    private static FileConfiguration armorsConfig;
    private static final HashMap<String, ArmorData> armors = new HashMap<>();

    public static class ArmorData {
        String id;
        String displayName;
        Material material;
        String name;
        List<String> lore;
        double bonus;
        double discount;

        public ArmorData(String id, String displayName, Material material, String name, List<String> lore, double bonus, double discount) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.bonus = bonus;
            this.discount = discount;
        }
    }

    public ArmorsBonus() {
        super("armors");
        loadArmorsConfig();
    }

    private void loadArmorsConfig() {
        if (!armorsFile.exists()) {
            try {
                armorsFile.getParentFile().mkdirs();
                armorsFile.createNewFile();
                FileConfiguration defaultConfig = new YamlConfiguration();
                // Define a single example armor with lore as a list
                defaultConfig.set("armors.example_helmet.display-name", "&aCapacete Exemplo");
                defaultConfig.set("armors.example_helmet.item.material", "DIAMOND_HELMET");
                defaultConfig.set("armors.example_helmet.item.name", "&aCapacete Exemplo");
                defaultConfig.set("armors.example_helmet.item.lore", String.join("\n",
                    "&7Este capacete garante buffs",
                    "&7na venda da &floja&7.",
                    "",
                    "&6Informações",
                    " &fBônus &6(bonus)&f: &7{bonus}%",
                    " &fDisconto &6(disconto)&f: &7{disconto}%",
                    ""
                ));
                defaultConfig.set("armors.example_helmet.bonus", 5.0);
                defaultConfig.set("armors.example_helmet.disconto", 1.0);

                // Define messages
                defaultConfig.set("messages.syntax", "&cUse: /{command} {syntax}");
                defaultConfig.set("messages.target", "&cJogador {player} não encontrado.");
                defaultConfig.set("messages.number", "&cO argumento não é um número.");
                defaultConfig.set("messages.permission", "&cVocê não tem permissão para fazer isto.");
                defaultConfig.set("messages.console", "&cApenas jogadores in-game podem realizar esta ação.");
                defaultConfig.set("messages.cancelled", "&cVocê cancelou a ação.");
                defaultConfig.set("messages.reload", "&aConfigurações recarregadas com sucesso.");
                defaultConfig.set("messages.inv-full", "&cO seu inventário está cheio.");
                defaultConfig.set("messages.help", String.join("\n",
                    "&aArmors-Bonus comandos:",
                    "&a> /armors give <player> <armor>",
                    "&a> /armors reload"
                ));
                defaultConfig.set("messages.armor-give", "&aVocê deu &71x {armor}&a para o jogador &7{player}&a.");
                defaultConfig.set("messages.armor-received", "&aVocê recebeu &71x {armor}&a.");
                defaultConfig.set("messages.armor-list", String.join("\n",
                    "&cArmadura não encontrada.",
                    "&cArmaduras disponíveis: &f{list}"
                ));
                defaultConfig.save(armorsFile);
            } catch (IOException e) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Erro ao criar armors.yml:");
                e.printStackTrace();
            }
        }
        armorsConfig = YamlConfiguration.loadConfiguration(armorsFile);
        loadArmors();
    }

    private void loadArmors() {
        armors.clear();
        ConfigurationSection armorsSection = armorsConfig.getConfigurationSection("armors");
        if (armorsSection == null) {
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] No armors section found in armors.yml");
            return;
        }

        for (String key : armorsSection.getKeys(false)) {
            ConfigurationSection armor = armorsSection.getConfigurationSection(key);
            if (armor == null) continue;

            String displayName = armor.getString("display-name", "");
            ConfigurationSection itemSection = armor.getConfigurationSection("item");
            if (itemSection == null) continue;

            Material material = Material.getMaterial(itemSection.getString("material", "STONE").toUpperCase());
            if (material == null) continue;

            String name = itemSection.getString("name", "");
            List<String> lore = itemSection.getStringList("lore");
            if (lore.isEmpty()) {
                String singleLore = itemSection.getString("lore", "");
                if (!singleLore.isEmpty()) {
                    lore = new ArrayList<>();
                    for (String line : singleLore.split("\n")) {
                        lore.add(line);
                    }
                }
            }
            double bonus = armor.getDouble("bonus", 0.0);
            double discount = armor.getDouble("disconto", 0.0);

            armors.put(key, new ArmorData(key, displayName, material, name, lore, bonus, discount));
        }
    }

    public static ItemStack createArmorItem(String armorId) {
        ArmorData armor = armors.get(armorId);
        if (armor == null) return null;

        ItemStack item = new ItemStack(armor.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', armor.name));
        List<String> formattedLore = new ArrayList<>();
        if (armor.lore != null && !armor.lore.isEmpty()) {
            for (String line : armor.lore) {
                // Replace placeholders and translate color codes for each line
                String formattedLine = line.replace("{bonus}", String.format("%.1f", armor.bonus))
                                         .replace("{disconto}", String.format("%.1f", armor.discount));
                formattedLore.add(ChatColor.translateAlternateColorCodes('&', formattedLine));
            }
            meta.setLore(formattedLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static double getBuyDiscount(Player player) {
        // Armor-based discount
        double armorDiscount = 0.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasDisplayName()) {
                String displayName = armor.getItemMeta().getDisplayName();
                for (ArmorData data : armors.values()) {
                    if (ChatColor.translateAlternateColorCodes('&', data.name).equals(displayName)) {
                        armorDiscount += data.discount / 100.0; // Convert percentage to decimal
                    }
                }
            }
        }

        // Permission-based discount
        double permissionDiscount = 0.0;
        for (int level = 100; level >= 0; level -= 5) {
            if (player.hasPermission("factionsutils.shop.discount." + level)) {
                permissionDiscount = level / 100.0; // Convert percentage to decimal
                break;
            }
        }

        // Combine both discounts
        return armorDiscount + permissionDiscount;
    }

    public static double getSellBonus(Player player) {
        // Armor-based bonus
        double armorBonus = 0.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasDisplayName()) {
                String displayName = armor.getItemMeta().getDisplayName();
                for (ArmorData data : armors.values()) {
                    if (ChatColor.translateAlternateColorCodes('&', data.name).equals(displayName)) {
                        armorBonus += data.bonus / 100.0; // Convert percentage to decimal
                    }
                }
            }
        }

        // Permission-based bonus
        double permissionBonus = 0.0;
        for (int level = 100; level >= 0; level -= 5) {
            if (player.hasPermission("factionsutils.shop.bonus." + level)) {
                permissionBonus = level / 100.0; // Convert percentage to decimal
                break;
            }
        }

        // Combine both bonuses
        return armorBonus + permissionBonus;
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            String helpMessage = armorsConfig.getString("messages.help", "");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', helpMessage));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("armors.admin")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.permission")));
                return;
            }
            loadArmorsConfig();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.reload")));
            return;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("armors.admin")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.permission")));
                return;
            }
            if (args.length != 3) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.syntax").replace("{command}", label).replace("{syntax}", "give <player> <armor>")));
                return;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.target").replace("{player}", args[1])));
                return;
            }

            ArmorData armor = armors.get(args[2]);
            if (armor == null) {
                String list = String.join(", ", armors.keySet());
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.armor-list").replace("{list}", list)));
                return;
            }

            ItemStack armorItem = createArmorItem(args[2]);
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.inv-full")));
                return;
            }

            target.getInventory().addItem(armorItem);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.armor-give").replace("{armor}", armor.displayName).replace("{player}", target.getName())));
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.armor-received").replace("{armor}", armor.displayName)));
            return;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', armorsConfig.getString("messages.syntax").replace("{command}", label).replace("{syntax}", "give <player> <armor> | reload")));
    }
}
