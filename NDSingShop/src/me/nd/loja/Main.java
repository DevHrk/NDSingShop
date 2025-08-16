package me.nd.loja;

import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import me.nd.loja.commands.ArmorsBonus;
import me.nd.loja.commands.Commands;
import me.nd.loja.listener.Listeners;
import net.milkbowl.vault.economy.Economy;

public class Main extends JavaPlugin {
	
	public static Economy economy;
	
	@Override
	public void onEnable() {
		setupEconomy();
		saveDefaultConfig();
		Commands.setupCommands();
		Listeners.setupListeners();
	}
	
    public boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        return economy != null;
    }
    
    public Economy getEconomy() {
        return economy;
    }

    public double getBuyDiscount(Player player) {
        return ArmorsBonus.getBuyDiscount(player);
    }

    public double getSellBonus(Player player) {
        return ArmorsBonus.getSellBonus(player);
    }
	
	public static Main get() {
        return (Main)JavaPlugin.getPlugin((Class)Main.class);
    }
	
}
