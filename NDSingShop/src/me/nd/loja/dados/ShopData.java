package me.nd.loja.dados;

import java.util.UUID;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public class ShopData {

		public String id;
		public ItemStack item;
        public double basePrice;
        public double currentPrice;
        public double baseSellPrice;
        public double sellPrice;
        public int stock;
        public int initialStock;
        public boolean unlimitedStock;
        public boolean buyEnabled;
        public boolean sellEnabled;
        public boolean isOpen;
        public double maxPrice;
        public double minPrice;
        public double maxFluctuation;
        public int transactionAmount;
        public Block signBlock;
        public UUID owner;

        public ShopData(String id, ItemStack item, double basePrice, double baseSellPrice, int stock, int initialStock,
                boolean unlimitedStock, boolean buyEnabled, boolean sellEnabled, double maxPrice, double minPrice,
                double maxFluctuation, int transactionAmount, Block signBlock, UUID owner) {
            this.id = id;
            this.item = item;
            this.basePrice = basePrice;
            this.currentPrice = basePrice;
            this.baseSellPrice = baseSellPrice;
            this.sellPrice = baseSellPrice;
            this.stock = stock;
            this.initialStock = initialStock;
            this.unlimitedStock = unlimitedStock;
            this.buyEnabled = buyEnabled;
            this.sellEnabled = sellEnabled;
            this.isOpen = true;
            this.maxPrice = maxPrice;
            this.minPrice = minPrice;
            this.maxFluctuation = maxFluctuation;
            this.transactionAmount = transactionAmount;
            this.signBlock = signBlock;
            this.owner = owner;
        }
}
