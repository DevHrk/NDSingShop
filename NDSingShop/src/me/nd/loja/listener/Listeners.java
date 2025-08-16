package me.nd.loja.listener;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import me.nd.loja.Main;

public class Listeners {

	public static void setupListeners() {
		PluginManager pm = Bukkit.getPluginManager();
		// Crie uma lista de classes de listeners
		List<Class<? extends Listener>> listenerClasses = Arrays.asList(
				PlacaListener.class);
		// Registre todos os listeners em um loop
		
		listenerClasses.forEach(listenerClass -> {
			if (Listener.class.isAssignableFrom(listenerClass)) {
				try {
					Listener listenerInstance = listenerClass.getDeclaredConstructor().newInstance();
					pm.registerEvents(listenerInstance, Main.get());
				} catch (ReflectiveOperationException e) {
					Bukkit.getLogger().severe(
							"Failed to register listener: " + listenerClass.getSimpleName() + " - " + e.getMessage());
				}
			} else {
				Bukkit.getLogger().warning("Class " + listenerClass.getSimpleName() + " does not implement Listener!");
			}
		});

	}
}
