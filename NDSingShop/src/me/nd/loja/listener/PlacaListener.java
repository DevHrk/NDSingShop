package me.nd.loja.listener;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.nd.loja.Main;
import me.nd.loja.api.FormatterAPI;
import me.nd.loja.dados.DataManager;
import me.nd.loja.dados.ShopData;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlacaListener implements Listener {
	public static final HashMap<String, ShopData> shops = new HashMap<>();
    public static final HashMap<String, ShopData> pendingShops = new HashMap<>();
    public static final String SHOP_PREFIX = "§e[Loja] ";
    public static final String CONFIG_MENU_PREFIX = "Configurar Loja - ";
    public static final File shopsFile = new File(Main.get().getDataFolder(), "shops.yml");
    public static FileConfiguration shopsConfig;

    public PlacaListener() {
        DataManager.loadShopsFromFile();
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) return;
        Sign sign = (Sign) event.getBlock().getState();
        if (!sign.getLine(0).startsWith(SHOP_PREFIX)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        Player player = event.getPlayer();
        String[] lines = event.getLines();

        if (!lines[0].equalsIgnoreCase("Loja")) return;
        if (!player.hasPermission("factionsutils.shop.create")) {
            player.sendMessage("§cVocê não tem permissão para criar lojas!");
            event.setCancelled(true);
            return;
        }

        ItemStack heldItem = player.getInventory().getItemInHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            player.sendMessage("§cVocê deve segurar um item para criar a loja!");
            event.setCancelled(true);
            return;
        }

        String id = DataManager.generateNextShopId();
        String locationKey = DataManager.getLocationKey(event.getBlock());
        int initialStock = 100;
        // Initialize maxPrice and minPrice with defaults; they can be changed in the config menu
        ShopData shop = new ShopData(id, heldItem.clone(), 100.0, 95.0, initialStock, initialStock,
                false, true, true, 1000.0, 1.0, 0.2, 1, event.getBlock(), player.getUniqueId());
        String key = id + ":" + locationKey;
        shops.put(key, shop);
        pendingShops.put(key, shop);
        DataManager.saveShopToFile(shop);

        updateSign(shop);
        openShopConfigMenu(player, shop);
    }
    
    private int getAvailableInventorySpace(Player player, ItemStack shopItem) {
        int maxStackSize = shopItem.getMaxStackSize();
        int availableSpace = 0;

        // Iterate through the player's inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                // Empty slot can hold a full stack
                availableSpace += maxStackSize;
            } else if (item.isSimilar(shopItem)) {
                // Slot with matching item can hold up to maxStackSize - current amount
                availableSpace += maxStackSize - item.getAmount();
            }
        }
        return availableSpace;
    }
    
    @SuppressWarnings("unused")
    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign)) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Sign sign = (Sign) block.getState();
        String[] lines = sign.getLines();

        if (!lines[0].startsWith(SHOP_PREFIX)) return;

        String rawItemName = ChatColor.stripColor(lines[1]);
        String itemName = rawItemName.replaceAll("\\s*\\d+\\s*x\\s*$", "").trim();
        String locationKey = DataManager.getLocationKey(block);
        ShopData shop = null;
        for (Map.Entry<String, ShopData> entry : shops.entrySet()) {
            if (entry.getValue().signBlock.equals(block) && DataManager.getFriendlyItemName(entry.getValue().item).equals(itemName)) {
                shop = entry.getValue();
                break;
            }
        }
        if (shop == null) {
            for (Map.Entry<String, ShopData> entry : pendingShops.entrySet()) {
                if (entry.getValue().signBlock.equals(block) && DataManager.getFriendlyItemName(entry.getValue().item).equals(itemName)) {
                    shop = entry.getValue();
                    break;
                }
            }
        }
        if (shop == null) {
            player.sendMessage("§cLoja não encontrada!");
            return;
        }

        long currentTime = System.currentTimeMillis();

        boolean isOwner = shop.owner != null && shop.owner.equals(player.getUniqueId());
        boolean canEdit = isOwner || player.hasPermission("factionsutils.shop.edit");
        boolean isAdmin = player.hasPermission("factionsutils.shop.admin");

        Economy economy = Main.get().getEconomy();
        double buyDiscount = Main.get().getBuyDiscount(player); // Assume method exists in Main
        double sellBonus = Main.get().getSellBonus(player); // Assume method exists in Main
        double pricePerItem = shop.currentPrice * (1 - buyDiscount);
        if (player.isSneaking() && event.getAction() == Action.LEFT_CLICK_BLOCK && isAdmin && player.getGameMode() == GameMode.CREATIVE) {
            openShopConfigMenu(player, shop);
            return;
        } else if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK && shop.buyEnabled && shop.isOpen) {
            // Shift + Right-click: Buy maximum possible
            int maxAmount = (int) (economy.getBalance(player) / pricePerItem);
            if (!shop.unlimitedStock && maxAmount > shop.stock) {
                maxAmount = shop.stock;
            }
            // Check available inventory space
            int inventorySpace = getAvailableInventorySpace(player, shop.item);
            if (inventorySpace == 0) {
                player.sendMessage("§cSeu inventário está lotado! Libere espaço para comprar.");
                return;
            }
            if (maxAmount > inventorySpace) {
                maxAmount = inventorySpace;
            }
            if (maxAmount > 0) {
                handleBuy(player, shop, maxAmount);
            } else {
                player.sendMessage("§cVocê não tem dinheiro suficiente ou o estoque está esgotado!");
            }
        } else if (player.isSneaking() && event.getAction() == Action.LEFT_CLICK_BLOCK && shop.sellEnabled && shop.isOpen) {
            int maxAmount = countItems(player.getInventory(), shop.item);
            if (maxAmount > 0) {
                handleSell(player, shop, maxAmount);
            } else {
                player.sendMessage("§cVocê não tem este item para vender!");
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && shop.buyEnabled && shop.isOpen) {
            // Regular right-click: Buy transactionAmount
            int amount = shop.transactionAmount;
            if (economy.getBalance(player) < pricePerItem * amount) {
                amount = (int) (economy.getBalance(player) / pricePerItem);
            }
            if (!shop.unlimitedStock && amount > shop.stock) {
                amount = shop.stock;
            }
            // Check available inventory space
            int inventorySpace = getAvailableInventorySpace(player, shop.item);
            if (inventorySpace == 0) {
                player.sendMessage("§cSeu inventário está lotado! Libere espaço para comprar.");
                return;
            }
            if (amount > inventorySpace) {
                amount = inventorySpace;
            }
            if (amount <= 0) {
                player.sendMessage("§cVocê não tem dinheiro suficiente ou o estoque está esgotado!");
                return;
            }
            handleBuy(player, shop, amount);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && shop.sellEnabled && shop.isOpen) {
            int amount = shop.transactionAmount;
            int inventoryAmount = countItems(player.getInventory(), shop.item);
            if (inventoryAmount < amount) {
                amount = inventoryAmount;
            }
            if (amount > 0) {
                handleSell(player, shop, amount);
            } else {
                player.sendMessage("§cVocê não tem este item para vender!");
            }
        } else if (!shop.isOpen) {
            player.sendMessage("§cEsta loja está fechada!");
        }
    }

    private enum EditableField {
        BASE_PRICE, STOCK, MAX_FLUCTUATION, TRANSACTION_AMOUNT, SELL_PRICE, MAX_PRICE, MIN_PRICE
    }

    private final Map<UUID, Pair<ShopData, EditableField>> awaitingInput = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String inventoryName = event.getInventory().getName();
        if (!inventoryName.startsWith(CONFIG_MENU_PREFIX)) return;

        event.setCancelled(true);
        String shopId = inventoryName.substring(CONFIG_MENU_PREFIX.length());
        ShopData shop = null;
        String locationKey = null;
        for (String key : pendingShops.keySet()) {
            if (key.startsWith(shopId + ":")) {
                shop = pendingShops.get(key);
                locationKey = key.substring(shopId.length() + 1);
                break;
            }
        }
        if (shop == null) {
            for (String key : shops.keySet()) {
                if (key.startsWith(shopId + ":")) {
                    shop = shops.get(key);
                    locationKey = key.substring(shopId.length() + 1);
                    break;
                }
            }
        }
        if (shop == null) {
            player.closeInventory();
            player.sendMessage("§cErro: Dados da loja não encontrados!");
            return;
        }

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        boolean isAdmin = player.hasPermission("factionsutils.shop.admin");

        if (event.isShiftClick() && event.isLeftClick()) {
            EditableField field = null;
            switch (slot) {
                case 28:
                    field = EditableField.BASE_PRICE;
                    player.sendMessage("§aDigite o preço base desejado (número decimal, ex: 50.0):");
                    break;
                case 30:
                    if (!shop.unlimitedStock) {
                        field = EditableField.STOCK;
                        player.sendMessage("§aDigite a quantidade de estoque desejada (número inteiro):");
                    }
                    break;
                case 32:
                    field = EditableField.MAX_FLUCTUATION;
                    player.sendMessage("§aDigite a flutuação máxima desejada (número decimal, ex: 10.0):");
                    break;
                case 34:
                    field = EditableField.MAX_PRICE;
                    player.sendMessage("§aDigite o preço máximo desejado (número decimal, ex: 150000000000.0):");
                    break;
                case 36:
                    field = EditableField.MIN_PRICE;
                    player.sendMessage("§aDigite o preço mínimo desejado (número decimal, ex: 0.5):");
                    break;
                case 38:
                    field = EditableField.TRANSACTION_AMOUNT;
                    player.sendMessage("§aDigite a quantidade por transação desejada (número inteiro):");
                    break;
                case 40:
                    field = EditableField.SELL_PRICE;
                    player.sendMessage("§aDigite o preço de venda base desejado (número decimal, ex: 50.0):");
                    break;
            }
            if (field != null) {
                awaitingInput.put(player.getUniqueId(), new Pair<>(shop, field));
                player.closeInventory();
                return;
            }
        }

        switch (slot) {
            case 10:
                ItemStack heldItem = player.getInventory().getItemInHand();
                if (heldItem != null && heldItem.getType() != Material.AIR) {
                    shop.item = heldItem.clone();
                    DataManager.saveShopToFile(shop);
                    updateSign(shop);
                    updateConfigMenu(player, shop);
                } else {
                    player.sendMessage("§cSegure um item na mão para definir!");
                }
                break;
            case 1:
                String key = shopId + ":" + locationKey;
                shops.remove(key);
                pendingShops.remove(key);
                DataManager.deleteShopFromFile(shopId, locationKey);
                player.sendMessage("§aLoja " + shopId + " removida com sucesso!");
                player.closeInventory();
                break;
            case 12:
                shop.buyEnabled = !shop.buyEnabled;
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 14:
                shop.sellEnabled = !shop.sellEnabled;
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 16:
                shop.unlimitedStock = !shop.unlimitedStock;
                if (shop.unlimitedStock) shop.stock = -1;
                else shop.stock = shop.initialStock;
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 28:
                if (event.isRightClick()) {
                    shop.basePrice -= 10;
                } else {
                    shop.basePrice += 10;
                }
                shop.currentPrice = shop.basePrice;
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 30:
                if (!shop.unlimitedStock) {
                    if (event.isRightClick()) {
                        shop.stock -= 10;
                    } else {
                        shop.stock += 10;
                    }
                    double stockRatio = shop.initialStock > 0 ? (double) (shop.initialStock - shop.stock) / shop.initialStock : 0;
                    shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.basePrice * (1 - stockRatio * shop.maxFluctuation)));
                    shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.baseSellPrice * (1 + stockRatio * shop.maxFluctuation)));
                    DataManager.saveShopToFile(shop);
                    updateSign(shop);
                    updateConfigMenu(player, shop);
                }
                break;
            case 32:
                if (event.isRightClick()) {
                    shop.maxFluctuation -= 1;
                } else {
                    shop.maxFluctuation += 1;
                }
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 34:
                if (event.isRightClick()) {
                    shop.maxPrice -= 1000;
                } else {
                    shop.maxPrice += 1000;
                }
                shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.currentPrice));
                shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.sellPrice));
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 36:
                if (event.isRightClick()) {
                    shop.minPrice -= 0.1;
                } else {
                    shop.minPrice += 0.1;
                }
                shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.currentPrice));
                shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.sellPrice));
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 38:
                if (event.isRightClick()) {
                    shop.transactionAmount -= 1;
                } else {
                    shop.transactionAmount += 1;
                }
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 40:
                if (event.isRightClick()) {
                    shop.baseSellPrice -= 10;
                } else {
                    shop.baseSellPrice += 10;
                }
                double stockRatio = shop.initialStock > 0 ? (double) (shop.initialStock - shop.stock) / shop.initialStock : 0;
                shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.baseSellPrice * (1 + stockRatio * shop.maxFluctuation)));
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                updateConfigMenu(player, shop);
                break;
            case 42:
                shops.remove(shop.id + ":" + locationKey);
                pendingShops.remove(shop.id + ":" + locationKey);
                DataManager.deleteShopFromFile(shop.id, locationKey);
                shop.signBlock.setType(Material.AIR);
                player.sendMessage("§cConfiguração da loja " + shop.id + " cancelada!");
                player.closeInventory();
                break;
            case 44:
                if (!shop.buyEnabled && !shop.sellEnabled) {
                    player.sendMessage("§cA loja deve permitir compra ou venda!");
                    return;
                }
                pendingShops.remove(shop.id + ":" + locationKey);
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                player.closeInventory();
                player.sendMessage("§aLoja " + DataManager.getFriendlyItemName(shop.item) + " criada com sucesso!");
                break;
            case 46:
                if (isAdmin) {
                    shop.isOpen = !shop.isOpen;
                    DataManager.saveShopToFile(shop);
                    updateSign(shop);
                    updateConfigMenu(player, shop);
                    player.sendMessage("§aLoja " + (shop.isOpen ? "aberta" : "fechada") + "!");
                }
                break;
        }
    }

    @EventHandler
    public void chat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (awaitingInput.containsKey(playerUUID)) {
            e.setCancelled(true);
            Pair<ShopData, EditableField> pair = awaitingInput.get(playerUUID);
            ShopData shop = pair.getKey();
            EditableField field = pair.getValue();
            String message = e.getMessage().trim();

            try {
                switch (field) {
                    case BASE_PRICE:
                    case SELL_PRICE:
                    case MAX_FLUCTUATION:
                    case MAX_PRICE:
                    case MIN_PRICE:
                        double value = Double.parseDouble(message);
                        if (field == EditableField.BASE_PRICE) {
                            shop.basePrice = value;
                            shop.currentPrice = value;
                            player.sendMessage("§aPreço base definido para $" + String.format("%.2f", value) + "!");
                        } else if (field == EditableField.SELL_PRICE) {
                            shop.baseSellPrice = value;
                            double stockRatio = shop.initialStock > 0 ? (double) (shop.initialStock - shop.stock) / shop.initialStock : 0;
                            shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, value * (1 + stockRatio * shop.maxFluctuation)));
                            player.sendMessage("§aPreço de venda base definido para $" + String.format("%.2f", value) + "!");
                        } else if (field == EditableField.MAX_FLUCTUATION) {
                            shop.maxFluctuation = value;
                            player.sendMessage("§aFlutuação máxima definida para " + String.format("%.2f", value) + "!");
                        } else if (field == EditableField.MAX_PRICE) {
                            shop.maxPrice = value;
                            shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.currentPrice));
                            shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.sellPrice));
                            player.sendMessage("§aPreço máximo definido para $" + String.format("%.2f", value) + "!");
                        } else if (field == EditableField.MIN_PRICE) {
                            shop.minPrice = value;
                            shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.currentPrice));
                            shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.sellPrice));
                            player.sendMessage("§aPreço mínimo definido para $" + String.format("%.2f", value) + "!");
                        }
                        break;
                    case STOCK:
                    case TRANSACTION_AMOUNT:
                        int intValue = Integer.parseInt(message);
                        if (field == EditableField.STOCK) {
                            shop.stock = intValue;
                            shop.initialStock = intValue;
                            player.sendMessage("§aEstoque definido para " + intValue + "!");
                        } else {
                            shop.transactionAmount = intValue;
                            player.sendMessage("§aQuantidade por transação definida para " + intValue + "!");
                        }
                        break;
                }
                if (!shop.unlimitedStock) {
                    double stockRatio = shop.initialStock > 0 ? (double) (shop.initialStock - shop.stock) / shop.initialStock : 0;
                    shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.basePrice * (1 - stockRatio * shop.maxFluctuation)));
                    shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, shop.baseSellPrice * (1 + stockRatio * shop.maxFluctuation)));
                }
                DataManager.saveShopToFile(shop);
                updateSign(shop);
                openShopConfigMenu(player, shop);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cEntrada inválida! Use um número válido.");
            } finally {
                awaitingInput.remove(playerUUID);
            }
        }
    }

    private void openShopConfigMenu(Player player, ShopData shop) {
        if (shop == null || shop.signBlock == null || shop.id == null || shop.item == null) {
            player.sendMessage("§cErro: Dados da loja ou bloco inválidos!");
            Bukkit.getConsoleSender().sendMessage("§c[NDFactionUtils] Erro: Tentativa de abrir menu com shop nulo ou dados inválidos (id=" + (shop != null ? shop.id : "null") + ", item=" + (shop != null && shop.item != null ? shop.item.toString() : "null") + ", signBlock=" + (shop != null && shop.signBlock != null ? shop.signBlock.toString() : "null") + ") para jogador " + player.getName());
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, CONFIG_MENU_PREFIX + shop.id);

        ItemStack s = new ItemStack(Material.BARRIER);
        ItemMeta d = s.getItemMeta();
        d.setDisplayName("§eDeletar Loja");
        List<String> i = new ArrayList<>();
        i.add("§7Clique para deletar");
        i.add("§7está loja");
        d.setLore(i);
        s.setItemMeta(d);
        inv.setItem(1, s);

        ItemStack itemDisplay = shop.item.clone();
        ItemMeta itemMeta = itemDisplay.getItemMeta();
        itemMeta.setDisplayName("§eItem: " + DataManager.getFriendlyItemName(shop.item));
        List<String> itemLore = new ArrayList<>();
        itemLore.add("§7Clique para definir o item");
        itemLore.add("§7(Segure o item desejado na mão)");
        itemMeta.setLore(itemLore);
        itemDisplay.setItemMeta(itemMeta);
        inv.setItem(10, itemDisplay);

        ItemStack buyToggle = new ItemStack(shop.buyEnabled ? Material.INK_SACK : Material.INK_SACK, 1, (short) (shop.buyEnabled ? 10 : 8));
        ItemMeta buyMeta = buyToggle.getItemMeta();
        buyMeta.setDisplayName("§eCompra: " + (shop.buyEnabled ? "§aAtivada" : "§cDesativada"));
        List<String> buyLore = new ArrayList<>();
        buyLore.add("§7Clique para alternar");
        buyMeta.setLore(buyLore);
        buyToggle.setItemMeta(buyMeta);
        inv.setItem(12, buyToggle);

        ItemStack sellToggle = new ItemStack(shop.sellEnabled ? Material.INK_SACK : Material.INK_SACK, 1, (short) (shop.sellEnabled ? 10 : 8));
        ItemMeta sellMeta = sellToggle.getItemMeta();
        sellMeta.setDisplayName("§eVenda: " + (shop.sellEnabled ? "§aAtivada" : "§cDesativada"));
        List<String> sellLore = new ArrayList<>();
        sellLore.add("§7Clique para alternar");
        sellMeta.setLore(sellLore);
        sellToggle.setItemMeta(sellMeta);
        inv.setItem(14, sellToggle);

        ItemStack stockindicator = new ItemStack(shop.unlimitedStock ? Material.ENDER_CHEST : Material.CHEST);
        ItemMeta stockMeta = stockindicator.getItemMeta();
        stockMeta.setDisplayName("§eEstoque: " + (shop.unlimitedStock ? "§aIlimitado" : "§eLimitado"));
        List<String> stockLore = new ArrayList<>();
        stockLore.add("§7Clique para alternar");
        stockMeta.setLore(stockLore);
        stockindicator.setItemMeta(stockMeta);
        inv.setItem(16, stockindicator);

        ItemStack priceItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta priceMeta = priceItem.getItemMeta();
        priceMeta.setDisplayName("§ePreço Base (Compra): §a$" + String.format("%.2f", shop.basePrice));
        List<String> priceLore = new ArrayList<>();
        priceLore.add("§7Esquerdo: +10");
        priceLore.add("§7Direito: -10");
        priceLore.add("§7Shift + Esquerdo: Definir valor");
        priceMeta.setLore(priceLore);
        priceItem.setItemMeta(priceMeta);
        inv.setItem(28, priceItem);

        ItemStack stockItem = new ItemStack(Material.HOPPER);
        ItemMeta stockItemMeta = stockItem.getItemMeta();
        stockItemMeta.setDisplayName("§eEstoque: " + (shop.unlimitedStock ? "§aIlimitado" : "§e" + shop.stock));
        List<String> stockItemLore = new ArrayList<>();
        stockItemLore.add("§7Esquerdo: +10");
        stockItemLore.add("§7Direito: -10");
        if (!shop.unlimitedStock) {
            stockItemLore.add("§7Shift + Esquerdo: Definir valor");
        }
        stockItemMeta.setLore(stockItemLore);
        stockItem.setItemMeta(stockItemMeta);
        inv.setItem(30, stockItem);

        ItemStack fluctuationItem = new ItemStack(Material.COMPASS);
        ItemMeta fluctuationMeta = fluctuationItem.getItemMeta();
        fluctuationMeta.setDisplayName("§eFlutuação Máxima: §a$" + String.format("%.2f", shop.maxFluctuation));
        List<String> fluctuationLore = new ArrayList<>();
        fluctuationLore.add("§7Esquerdo: +1");
        fluctuationLore.add("§7Direito: -1");
        fluctuationLore.add("§7Shift + Esquerdo: Definir valor");
        fluctuationMeta.setLore(fluctuationLore);
        fluctuationItem.setItemMeta(fluctuationMeta);
        inv.setItem(32, fluctuationItem);

        ItemStack maxPriceItem = new ItemStack(Material.EMERALD);
        ItemMeta maxPriceMeta = maxPriceItem.getItemMeta();
        maxPriceMeta.setDisplayName("§ePreço Máximo: §a$" + String.format("%.2f", shop.maxPrice));
        List<String> maxPriceLore = new ArrayList<>();
        maxPriceLore.add("§7Esquerdo: +1000");
        maxPriceLore.add("§7Direito: -1000");
        maxPriceLore.add("§7Shift + Esquerdo: Definir valor");
        maxPriceMeta.setLore(maxPriceLore);
        maxPriceItem.setItemMeta(maxPriceMeta);
        inv.setItem(34, maxPriceItem);

        ItemStack minPriceItem = new ItemStack(Material.IRON_INGOT);
        ItemMeta minPriceMeta = minPriceItem.getItemMeta();
        minPriceMeta.setDisplayName("§ePreço Mínimo: §a$" + String.format("%.2f", shop.minPrice));
        List<String> minPriceLore = new ArrayList<>();
        minPriceLore.add("§7Esquerdo: +0.1");
        minPriceLore.add("§7Direito: -0.1");
        minPriceLore.add("§7Shift + Esquerdo: Definir valor");
        minPriceMeta.setLore(minPriceLore);
        minPriceItem.setItemMeta(minPriceMeta);
        inv.setItem(36, minPriceItem);

        ItemStack amountItem = new ItemStack(Material.PAPER);
        ItemMeta amountMeta = amountItem.getItemMeta();
        amountMeta.setDisplayName("§eQuantidade por Transação: §a" + shop.transactionAmount);
        List<String> amountLore = new ArrayList<>();
        amountLore.add("§7Esquerdo: +1");
        amountLore.add("§7Direito: -1");
        amountLore.add("§7Shift + Esquerdo: Definir valor");
        amountMeta.setLore(amountLore);
        amountItem.setItemMeta(amountMeta);
        inv.setItem(38, amountItem);

        ItemStack sellPriceItem = new ItemStack(Material.DIAMOND);
        ItemMeta sellPriceMeta = sellPriceItem.getItemMeta();
        sellPriceMeta.setDisplayName("§ePreço de Venda Base: §a$" + String.format("%.2f", shop.baseSellPrice));
        List<String> sellPriceLore = new ArrayList<>();
        sellPriceLore.add("§7Esquerdo: +10");
        sellPriceLore.add("§7Direito: -10");
        sellPriceLore.add("§7Shift + Esquerdo: Definir valor");
        sellPriceMeta.setLore(sellPriceLore);
        sellPriceItem.setItemMeta(sellPriceMeta);
        inv.setItem(40, sellPriceItem);

        ItemStack cancelItem = new ItemStack(Material.WOOL, 1, (short) 14);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName("§cCancelar");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("§7Clique para cancelar a criação da loja");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(42, cancelItem);

        ItemStack confirmItem = new ItemStack(Material.WOOL, 1, (short) 5);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName("§aConfirmar");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("§7Clique para finalizar a configuração");
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(44, confirmItem);

        if (player.hasPermission("factionsutils.shop.admin")) {
            ItemStack toggleOpenItem = new ItemStack(shop.isOpen ? Material.REDSTONE_TORCH_ON : Material.REDSTONE);
            ItemMeta toggleMeta = toggleOpenItem.getItemMeta();
            toggleMeta.setDisplayName("§eStatus: " + (shop.isOpen ? "§aAberta" : "§cFechada"));
            List<String> toggleLore = new ArrayList<>();
            toggleLore.add("§7Clique para alternar (Admin)");
            toggleMeta.setLore(toggleLore);
            toggleOpenItem.setItemMeta(toggleMeta);
            inv.setItem(46, toggleOpenItem);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        String inventoryName = event.getInventory().getName();
        if (!inventoryName.startsWith(CONFIG_MENU_PREFIX)) return;

        String shopId = inventoryName.substring(CONFIG_MENU_PREFIX.length());
        ShopData shop = null;
        String locationKey = null;
        for (String key : pendingShops.keySet()) {
            if (key.startsWith(shopId + ":")) {
                shop = pendingShops.get(key);
                locationKey = key.substring(shopId.length() + 1);
                break;
            }
        }
        if (shop != null && shop.signBlock != null && pendingShops.containsKey(shop.id + ":" + locationKey)) {
        }
    }

    private void updateConfigMenu(Player player, ShopData shop) {
        Bukkit.getScheduler().runTaskLater(Main.get(), () -> openShopConfigMenu(player, shop), 1L);
    }

    @SuppressWarnings("unused")
    private void showPreviewMenu(Player player, ShopData shop) {
        Inventory inv = Bukkit.createInventory(null, 27, "Loja - " + shop.id);

        ItemStack item = shop.item.clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        double buyDiscount = Main.get().getBuyDiscount(player); // Assume method exists
        double sellBonus = Main.get().getSellBonus(player); // Assume method exists
        lore.add("§7Preço de compra: §e$" + String.format("%.2f", shop.currentPrice * (1 - buyDiscount)));
        lore.add("§7Preço de venda: §e$" + String.format("%.2f", shop.sellPrice * (1 + sellBonus)));
        lore.add("§7Estoque: " + (shop.unlimitedStock ? "§aIlimitado" : "§e" + shop.stock));
        lore.add("§7Quantidade por transação: §e" + shop.transactionAmount);
        lore.add("§7Status: " + (shop.isOpen ? "§aAberta" : "§cFechada"));
        meta.setDisplayName("§e" + DataManager.getFriendlyItemName(shop.item));
        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(13, item);

        player.openInventory(inv);
    }

    private void handleBuy(Player player, ShopData shop, int amount) {
        if (!shop.buyEnabled) {
            player.sendMessage("§cEsta loja não permite compras!");
            return;
        }

        if (!shop.unlimitedStock && shop.stock < amount) {
            amount = shop.stock;
            if (amount == 0) {
                player.sendMessage("§cEstoque esgotado!");
                return;
            }
        }

        double buyDiscount = Main.get().getBuyDiscount(player); // Assume method exists
        double pricePerItem = shop.currentPrice * (1 - buyDiscount);
        Economy economy = Main.get().getEconomy();
        double playerBalance = economy.getBalance(player);

        if (playerBalance < pricePerItem * amount) {
            amount = (int) (playerBalance / pricePerItem);
            if (amount == 0) {
                player.sendMessage("§cVocê não tem dinheiro suficiente!");
                return;
            }
        }

        double totalPrice = pricePerItem * amount;

        if (!shop.unlimitedStock) {
            shop.stock -= amount;
            double stockRatio = shop.initialStock > 0 ? (double) (shop.initialStock - shop.stock) / shop.initialStock : 0;
            double newBuyPrice = shop.basePrice * (1 - stockRatio * shop.maxFluctuation);
            shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, newBuyPrice));
            double newSellPrice = shop.baseSellPrice * (1 + stockRatio * shop.maxFluctuation);
            shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, newSellPrice));
        }

        economy.withdrawPlayer(player, totalPrice);
        ItemStack item = shop.item.clone();
        item.setAmount(amount);
        player.getInventory().addItem(item);
        player.sendMessage("§aComprou " + amount + "x " + DataManager.getFriendlyItemName(shop.item) + " por $" + String.format("%.2f", totalPrice));

        DataManager.saveShopToFile(shop);
        updateSign(shop);
    }

    private void handleSell(Player player, ShopData shop, int amount) {
        if (!shop.sellEnabled) {
            player.sendMessage("§cEsta loja não permite vendas!");
            return;
        }

        int available = countItems(player.getInventory(), shop.item);
        if (available < amount) {
            amount = available;
            if (amount == 0) {
                player.sendMessage("§cVocê não tem este item para vender!");
                return;
            }
        }

        double sellBonus = Main.get().getSellBonus(player); // Assume method exists
        double pricePerItem = shop.sellPrice * (1 + sellBonus);
        double totalPrice = pricePerItem * amount;

        removeItems(player.getInventory(), shop.item, amount);
        Economy economy = Main.get().getEconomy();
        economy.depositPlayer(player, totalPrice);

        if (!shop.unlimitedStock) {
            shop.stock += amount;
            double stockRatio = shop.initialStock > 0 ? (double) (shop.initialStock - shop.stock) / shop.initialStock : 0;
            double newBuyPrice = shop.basePrice * (1 - stockRatio * shop.maxFluctuation);
            shop.currentPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, newBuyPrice));
            double newSellPrice = shop.baseSellPrice * (1 + stockRatio * shop.maxFluctuation);
            shop.sellPrice = Math.max(shop.minPrice, Math.min(shop.maxPrice, newSellPrice));
        }

        player.sendMessage("§aVendeu " + amount + "x " + DataManager.getFriendlyItemName(shop.item) + " por $" + String.format("%.2f", totalPrice));

        DataManager.saveShopToFile(shop);
        updateSign(shop);
    }

    private int countItems(Inventory inv, ItemStack shopItem) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(shopItem)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Inventory inv, ItemStack shopItem, int amount) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(shopItem)) {
                int currentAmount = item.getAmount();
                if (currentAmount <= amount) {
                    inv.remove(item);
                    amount -= currentAmount;
                } else {
                    item.setAmount(currentAmount - amount);
                    break;
                }
            }
        }
    }

    private static void updateSign(ShopData shop) {
        if (shop.signBlock != null && shop.signBlock.getState() instanceof Sign) {
            Sign sign = (Sign) shop.signBlock.getState();
            sign.setLine(0, SHOP_PREFIX);
            String itemName = DataManager.getFriendlyItemName(shop.item);
            sign.setLine(1, itemName + " " + shop.transactionAmount + "x");
            StringBuilder priceLine = new StringBuilder();
            if (shop.buyEnabled) {
                priceLine.append("§aC§0 ").append(FormatterAPI.formatNumbers(shop.currentPrice));
            }
            if (shop.buyEnabled && shop.sellEnabled) {
                priceLine.append(" | ");
            }
            if (shop.sellEnabled) {
                priceLine.append("§cV§0 ").append(FormatterAPI.formatNumbers(shop.sellPrice));
            }
            String priceText = priceLine.toString();
            sign.setLine(2, priceText);
            sign.setLine(3, shop.unlimitedStock ? "§0Estoque: §b∞" : "§0Estoque: §b" + shop.stock);
            sign.update();
        }
    }

    public static void toggleShop(String id, String locationKey, boolean open) {
        ShopData shop = shops.get(id + ":" + locationKey);
        if (shop != null) {
            shop.isOpen = open;
            updateSign(shop);
            DataManager.saveShopToFile(shop);
        }
    }

    public class Pair<K, V> {
        private final K key;
        private final V value;
        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
        public K getKey() { return key; }
        public V getValue() { return value; }
    }
}