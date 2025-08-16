package me.nd.loja.commands;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;

public abstract class Commands extends Command {
	
	  public Commands(String name, String... aliases) {
		    super(name);
		    setAliases(Arrays.asList(aliases));
		    
		    try {
		      SimpleCommandMap simpleCommandMap = (SimpleCommandMap) Bukkit.getServer().getClass().getDeclaredMethod("getCommandMap").invoke(Bukkit.getServer());
		      simpleCommandMap.register(getName(), "ndloja", this);
		    } catch (ReflectiveOperationException ex) {
		    	
		    }
		  }
	  
	  public static void setupCommands() {
		  new ArmorsBonus();
	  }
	  
	  public abstract void perform(CommandSender sender, String label, String[] args);
	  
	  @Override
	  public boolean execute(CommandSender sender, String commandLabel, String[] args) {
	    perform(sender, commandLabel, args);
	    return true;
	  }
	
}
