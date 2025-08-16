package me.nd.loja.api;

import java.text.DecimalFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.file.FileConfiguration;

import me.nd.loja.Main;

public class FormatterAPI {
	static FileConfiguration config1 = Main.get().getConfig();
    @SuppressWarnings("unused")
	private static Main main;
    private static  Pattern PATTERN;
    @SuppressWarnings("unused")
	private static FileConfiguration config;
    private static List<String> suffixes;
    
    public static void changeSuffixes( List<String> suffixes) {
    	FormatterAPI.suffixes = suffixes;
    }
    
    public static String formatNumber(double value) {
        int index;
        double tmp;
        for (index = 0; (tmp = value / 1000.0) >= 1.0; value = tmp, ++index) {}
         DecimalFormat decimalFormat = new DecimalFormat("#.#");
        return String.valueOf(decimalFormat.format(value)) + FormatterAPI.suffixes.get(index);
    }
    
    public static String formatNumbers(double value) {
        int index;
        double tmp;
        for (index = 0; (tmp = value / 1000.0) >= 1.0; value = tmp, ++index) {}
        DecimalFormat decimalFormat = new DecimalFormat("0.00"); // Changed to ensure two decimal places
        return decimalFormat.format(value) + FormatterAPI.suffixes.get(index);
    }
    
    public static double parseString( String value) throws Exception {
        try {
            return Double.parseDouble(value);
        }
        catch (Exception exception) {
             Matcher matcher = FormatterAPI.PATTERN.matcher(value);
            if (!matcher.find()) {
                throw new Exception("Invalid format");
            }
             double amount = Double.parseDouble(matcher.group(1));
             String suffix = matcher.group(2);
             int index = FormatterAPI.suffixes.indexOf(suffix.toUpperCase());
            return amount * Math.pow(1000.0, index);
        }
    }
    
    static {
    	FormatterAPI.main = (Main)Main.getPlugin((Class)Main.class);
        PATTERN = Pattern.compile("^(\\d+\\.?\\d*)(\\D+)");
        FormatterAPI.config = Main.get().getConfig();
        FormatterAPI.suffixes = (List<String>)FormatterAPI.config1.getStringList("Formatação");
    }
}