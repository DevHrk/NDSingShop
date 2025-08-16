package me.nd.loja.dados;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import me.nd.loja.listener.PlacaListener;


public class DataManager {
	
	public static String getFriendlyItemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "Desconhecido";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            return displayName.length() > 15 ? displayName.substring(0, 15) : displayName;
        }
        String name = item.getType().name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder friendlyName = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                friendlyName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
            }
        }
        String result = friendlyName.toString().trim();
        return result.length() > 15 ? result.substring(0, 15) : result;
    }

    public static String getLocationKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    public static String generateNextShopId() {
        int maxId = 0;
        ConfigurationSection shopsSection = PlacaListener.shopsConfig.getConfigurationSection("shops");
        if (shopsSection != null) {
            for (String key : shopsSection.getKeys(false)) {
                try {
                    int id = Integer.parseInt(key.split(":")[0]);
                    if (id > maxId) {
                        maxId = id;
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid IDs
                }
            }
        }
        return String.valueOf(maxId + 1);
    }

    public static void loadShopsFromFile() {
        if (!PlacaListener.shopsFile.exists()) {
            try {
            	PlacaListener.shopsFile.getParentFile().mkdirs();
            	PlacaListener.shopsFile.createNewFile();
            } catch (IOException e) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Erro ao criar arquivo shops.yml:");
                e.printStackTrace();
                return;
            }
        }
        PlacaListener.shopsConfig = YamlConfiguration.loadConfiguration(PlacaListener.shopsFile);
        ConfigurationSection shopsSection = PlacaListener.shopsConfig.getConfigurationSection("shops");
        if (shopsSection == null) {
        	PlacaListener.shopsConfig.createSection("shops");
            saveShopsFile();
            return;
        }

        for (String key : shopsSection.getKeys(false)) {
            ConfigurationSection shopSection = shopsSection.getConfigurationSection(key);
            if (shopSection == null) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Seção de loja inválida para chave " + key);
                continue;
            }

            String id = key.split(":")[0];
            String itemData = shopSection.getString("item");
            ItemStack item = null;

            if (itemData == null || itemData.trim().isEmpty()) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Item nulo ou vazio para loja " + id);
                continue;
            }

            try {
                // Parse itemData as a YAML string
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(itemData);
                ConfigurationSection itemSection = yaml.getConfigurationSection("item");
                if (itemSection != null) {
                    Map<String, Object> itemMap = itemSection.getValues(false);
                    if (itemMap.containsKey("type") && itemMap.get("type") != null) {
                        item = ItemStack.deserialize(itemMap);
                        if (item == null || item.getType() == Material.AIR) {
                            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Item inválido após desserialização para loja " + id + ": " + itemData);
                            continue;
                        }
                    } else {
                        Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Item sem 'type' na seção item para loja " + id + ": " + itemData);
                        continue;
                    }
                } else {
                    // Fallback: try to interpret itemData as a Material name
                    Material material = Material.getMaterial(itemData.trim().toUpperCase());
                    if (material != null && material != Material.AIR) {
                        item = new ItemStack(material);
                        Bukkit.getConsoleSender().sendMessage("§e[NDFactionUtils] Convertido itemData como Material simples " + itemData + " para ItemStack na loja " + id);
                    } else {
                        Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Item sem seção 'item' ou material inválido para loja " + id + ": " + itemData);
                        continue;
                    }
                }
            } catch (Exception e) {
                // Fallback: try to interpret itemData as a Material name
                Material material = Material.getMaterial(itemData.trim().toUpperCase());
                if (material != null && material != Material.AIR) {
                    item = new ItemStack(material);
                    Bukkit.getConsoleSender().sendMessage("§e[NDFactionUtils] Convertido material legado " + itemData + " para ItemStack na loja " + id);
                } else {
                    Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Falha ao processar itemData para loja " + id + ": " + itemData + ". Erro: " + e.getMessage());
                    continue;
                }
            }

            double basePrice = shopSection.getDouble("base_price");
            double currentPrice = shopSection.getDouble("current_price");
            double baseSellPrice = shopSection.getDouble("base_sell_price");
            double sellPrice = shopSection.getDouble("sell_price");
            int stock = shopSection.getInt("stock");
            int initialStock = shopSection.getInt("initial_stock");
            boolean unlimitedStock = shopSection.getBoolean("unlimited_stock");
            boolean buyEnabled = shopSection.getBoolean("buy_enabled");
            boolean sellEnabled = shopSection.getBoolean("sell_enabled");
            boolean isOpen = shopSection.getBoolean("is_open");
            double maxPrice = shopSection.getDouble("max_price");
            double minPrice = shopSection.getDouble("min_price");
            double maxFluctuation = shopSection.getDouble("max_fluctuation");
            int transactionAmount = shopSection.getInt("transaction_amount");
            String ownerUUID = shopSection.getString("owner");
            UUID owner = ownerUUID != null ? UUID.fromString(ownerUUID) : null;
            String[] locParts = shopSection.getString("sign_location").split(",");
            if (locParts.length != 4) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Localização inválida para loja " + id);
                continue;
            }
            Block signBlock = Bukkit.getWorld(locParts[0]).getBlockAt(
                    Integer.parseInt(locParts[1]),
                    Integer.parseInt(locParts[2]),
                    Integer.parseInt(locParts[3]));
            if (!(signBlock.getState() instanceof Sign)) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Bloco não é uma placa para loja " + id);
                continue;
            }

            // Validate prices
            double stockRatio = initialStock > 0 ? (double) (initialStock - stock) / initialStock : 0;
            if (currentPrice < minPrice || currentPrice > maxPrice || Math.abs(currentPrice - basePrice * (1 + stockRatio * maxFluctuation)) > 0.01) {
                currentPrice = Math.max(minPrice, Math.min(maxPrice, basePrice * (1 + stockRatio * maxFluctuation)));
            }
            if (sellPrice < minPrice || sellPrice > maxPrice || Math.abs(sellPrice - baseSellPrice * (1 + stockRatio * maxFluctuation)) > 0.01) {
                sellPrice = Math.max(minPrice, Math.min(maxPrice, baseSellPrice * (1 + stockRatio * maxFluctuation)));
            }

            ShopData shop = new ShopData(id, item, basePrice, baseSellPrice, stock, initialStock,
                    unlimitedStock, buyEnabled, sellEnabled, maxPrice, minPrice, maxFluctuation,
                    transactionAmount, signBlock, owner);
            shop.currentPrice = currentPrice;
            shop.sellPrice = sellPrice;
            shop.isOpen = isOpen;
            PlacaListener.shops.put(id + ":" + getLocationKey(signBlock), shop);
        }
    }

    public static void saveShopToFile(ShopData shop) {
        if (PlacaListener.shopsConfig == null) {
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Configuração YAML não inicializada!");
            return;
        }
        try {
            String key = shop.id + ":" + getLocationKey(shop.signBlock);
            ConfigurationSection shopsSection = PlacaListener.shopsConfig.getConfigurationSection("shops");
            if (shopsSection == null) {
                shopsSection = PlacaListener.shopsConfig.createSection("shops");
            }
            ConfigurationSection shopSection = shopsSection.createSection(key);

            if (shop.item == null || shop.item.getType() == Material.AIR) {
                Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Tentativa de salvar loja com item nulo ou AIR: ID=" + shop.id);
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.createSection("item", shop.item.serialize());
            shopSection.set("item", yaml.saveToString());
            shopSection.set("base_price", shop.basePrice);
            shopSection.set("current_price", shop.currentPrice);
            shopSection.set("base_sell_price", shop.baseSellPrice);
            shopSection.set("sell_price", shop.sellPrice);
            shopSection.set("stock", shop.stock);
            shopSection.set("initial_stock", shop.initialStock);
            shopSection.set("unlimited_stock", shop.unlimitedStock);
            shopSection.set("buy_enabled", shop.buyEnabled);
            shopSection.set("sell_enabled", shop.sellEnabled);
            shopSection.set("is_open", shop.isOpen);
            shopSection.set("max_price", shop.maxPrice);
            shopSection.set("min_price", shop.minPrice);
            shopSection.set("max_fluctuation", shop.maxFluctuation);
            shopSection.set("transaction_amount", shop.transactionAmount);
            shopSection.set("sign_location", shop.signBlock.getWorld().getName() + "," + shop.signBlock.getX() + "," +
                    shop.signBlock.getY() + "," + shop.signBlock.getZ());
            shopSection.set("owner", shop.owner != null ? shop.owner.toString() : null);

            saveShopsFile();
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Erro ao salvar loja no arquivo YAML: ID=" + shop.id);
            e.printStackTrace();
        }
    }

    public static void deleteShopFromFile(String shopId, String locationKey) {
        if (PlacaListener.shopsConfig == null) {
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Configuração YAML não inicializada!");
            return;
        }
        try {
            String key = shopId + ":" + locationKey;
            ConfigurationSection shopsSection = PlacaListener.shopsConfig.getConfigurationSection("shops");
            if (shopsSection != null) {
                shopsSection.set(key, null);
                saveShopsFile();
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Erro ao deletar loja do arquivo YAML: ID=" + shopId);
            e.printStackTrace();
        }
    }

    public static void saveShopsFile() {
        try {
        	PlacaListener.shopsConfig.save(PlacaListener.shopsFile);
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Erro ao salvar arquivo shops.yml:");
            e.printStackTrace();
        }
    }
}
