package com.pokato.skywyrms.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.platform.Platform;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * config/skywyrms.json. Read once on first use; missing keys fall back to the defaults and the file is written back
 * with every key (plus a "_help" section). Every default is the behaviour the mod had before the key existed.
 */
public final class ModConfig {
   public boolean naturalSpawning = true;
   public int spawnIntervalTicks = 400;
   public float spawnChance = 0.12F;
   public int spawnSpacing = 96;
   public boolean breathAffectsBlocks = true;
   public float eggHatchMinutes = 15.0F;
   public float babyGrowMinutes = 40.0F;
   public float wildHybridChance = 0.08F;
   // 1.2.4: taming, food and breeding items (empty list = the built-in default)
   public List<String> tameItems = new ArrayList<>();
   public float tameChance = 1.0F / 3.0F;
   public List<String> foodItems = new ArrayList<>();
   public List<String> breedItems = new ArrayList<>();
   // 1.2.4: stat multipliers (1.0 = unchanged); they scale each dragon's own inherited value
   public float healthMultiplier = 1.0F;
   public float attackDamageMultiplier = 1.0F;
   public float armorMultiplier = 1.0F;
   public float walkSpeedMultiplier = 1.0F;
   public float flySpeedMultiplier = 1.0F;
   public float abilityDamageMultiplier = 1.0F;
   // 1.2.4: ability switches, keyed by DragonKind.Ability ids
   public final Map<String, Boolean> abilities = new LinkedHashMap<>();
   // 1.2.4: client side - the dragon you ride drawn see-through: "off", "first_person" or "always"
   public String riderSeeThrough = "off";
   public float wingSoundVolume = 1.0F;
   public float wingSoundPitch = 1.0F;
   // 1.2.8: true = while the dragon is in the air the sneak key flies down and only dismounts on the ground or in water.
   // The default stays as it always was: the sneak key dismounts, also in the air.
   public boolean sneakFliesDown = false;
   private static ModConfig instance;

   public static final String[] ABILITY_IDS = {"fire_breath", "frost_breath", "lightning", "venom_spit", "void_blink", "tidal_jet"};

   private ModConfig() {
      for (String id : ABILITY_IDS) {
         this.abilities.put(id, true);
      }
   }

   public int eggHatchTicks() {
      return Math.max(20, Math.round(this.eggHatchMinutes * 1200.0F));
   }

   public int babyGrowTicks() {
      return Math.max(20, Math.round(this.babyGrowMinutes * 1200.0F));
   }

   public boolean abilityEnabled(String id) {
      return this.abilities.getOrDefault(id, true);
   }

   /** 0 = off, 1 = first person only, 2 = always */
   public int seeThroughMode() {
      String m = this.riderSeeThrough == null ? "off" : this.riderSeeThrough.toLowerCase(Locale.ROOT);
      return m.equals("always") ? 2 : m.equals("first_person") || m.equals("firstperson") ? 1 : 0;
   }

   /** Does an item (id + its tags) match a configured list? Entries: "minecraft:diamond", "diamond", "#minecraft:meat". */
   public static boolean matches(List<String> list, String itemId, Stream<String> tags) {
      if (list == null || list.isEmpty()) {
         return false;
      }
      List<String> tagList = null;
      for (String raw : list) {
         if (raw == null) {
            continue;
         }
         String e = raw.trim().toLowerCase(Locale.ROOT);
         if (e.isEmpty()) {
            continue;
         }
         if (e.startsWith("#")) {
            if (tagList == null) {
               tagList = tags.toList();
            }
            if (tagList.contains(withNamespace(e.substring(1)))) {
               return true;
            }
         } else if (withNamespace(e).equals(itemId)) {
            return true;
         }
      }
      return false;
   }

   private static String withNamespace(String id) {
      return id.indexOf(':') < 0 ? "minecraft:" + id : id;
   }

   public static synchronized ModConfig get() {
      if (instance == null) {
         instance = load();
      }
      return instance;
   }

   private static ModConfig load() {
      ModConfig config = new ModConfig();
      Path file;
      try {
         file = Platform.get().configDir().resolve("skywyrms.json");
      } catch (RuntimeException e) {
         return config;
      }
      try {
         if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
               config = fromJson(JsonParser.parseReader(reader).getAsJsonObject());
            }
         }
         Files.createDirectories(file.getParent());
         try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(config.toJson(), writer);
         }
      } catch (Exception e) {
         Skywyrms.LOGGER.warn("Could not read or write {}: {}", file, e.toString());
      }
      return config;
   }

   /** The settings in a parsed skywyrms.json (missing or broken keys keep their defaults). */
   public static ModConfig fromJson(JsonObject json) {
      ModConfig c = new ModConfig();
      if (json.has("naturalSpawning")) {
         c.naturalSpawning = bool(json, "naturalSpawning", c.naturalSpawning);
      }
      c.spawnIntervalTicks = Math.round(number(json, "spawnIntervalTicks", c.spawnIntervalTicks, 20, Integer.MAX_VALUE));
      c.spawnChance = number(json, "spawnChance", c.spawnChance, 0.0F, 1.0F);
      c.spawnSpacing = Math.round(number(json, "spawnSpacing", c.spawnSpacing, 16, Integer.MAX_VALUE));
      c.breathAffectsBlocks = bool(json, "breathAffectsBlocks", c.breathAffectsBlocks);
      c.eggHatchMinutes = number(json, "eggHatchMinutes", c.eggHatchMinutes, 0.1F, 1000.0F);
      c.babyGrowMinutes = number(json, "babyGrowMinutes", c.babyGrowMinutes, 0.1F, 1000.0F);
      c.wildHybridChance = number(json, "wildHybridChance", c.wildHybridChance, 0.0F, 1.0F);
      c.tameItems = strings(json, "tameItems");
      c.tameChance = number(json, "tameChance", c.tameChance, 0.0F, 1.0F);
      c.foodItems = strings(json, "foodItems");
      c.breedItems = strings(json, "breedItems");
      c.healthMultiplier = number(json, "healthMultiplier", 1.0F, 0.05F, 100.0F);
      c.attackDamageMultiplier = number(json, "attackDamageMultiplier", 1.0F, 0.0F, 100.0F);
      c.armorMultiplier = number(json, "armorMultiplier", 1.0F, 0.0F, 100.0F);
      c.walkSpeedMultiplier = number(json, "walkSpeedMultiplier", 1.0F, 0.05F, 10.0F);
      c.flySpeedMultiplier = number(json, "flySpeedMultiplier", 1.0F, 0.05F, 10.0F);
      c.abilityDamageMultiplier = number(json, "abilityDamageMultiplier", 1.0F, 0.0F, 100.0F);
      if (json.has("abilities") && json.get("abilities").isJsonObject()) {
         JsonObject a = json.getAsJsonObject("abilities");
         for (String id : ABILITY_IDS) {
            c.abilities.put(id, bool(a, id, true));
         }
      }
      if (json.has("riderSeeThrough")) {
         try {
            c.riderSeeThrough = json.get("riderSeeThrough").getAsString();
         } catch (RuntimeException ignored) {
         }
      }
      c.wingSoundVolume = number(json, "wingSoundVolume", 1.0F, 0.0F, 4.0F);
      c.wingSoundPitch = number(json, "wingSoundPitch", 1.0F, 0.25F, 2.0F);
      c.sneakFliesDown = bool(json, "sneakFliesDown", false);
      return c;
   }

   /** All settings (and the explanations) as written to skywyrms.json. */
   public JsonObject toJson() {
      JsonObject out = new JsonObject();
      out.addProperty("naturalSpawning", this.naturalSpawning);
      out.addProperty("spawnIntervalTicks", this.spawnIntervalTicks);
      out.addProperty("spawnChance", this.spawnChance);
      out.addProperty("spawnSpacing", this.spawnSpacing);
      out.addProperty("breathAffectsBlocks", this.breathAffectsBlocks);
      out.addProperty("eggHatchMinutes", this.eggHatchMinutes);
      out.addProperty("babyGrowMinutes", this.babyGrowMinutes);
      out.addProperty("wildHybridChance", this.wildHybridChance);
      out.add("tameItems", array(this.tameItems));
      out.addProperty("tameChance", this.tameChance);
      out.add("foodItems", array(this.foodItems));
      out.add("breedItems", array(this.breedItems));
      out.addProperty("healthMultiplier", this.healthMultiplier);
      out.addProperty("attackDamageMultiplier", this.attackDamageMultiplier);
      out.addProperty("armorMultiplier", this.armorMultiplier);
      out.addProperty("walkSpeedMultiplier", this.walkSpeedMultiplier);
      out.addProperty("flySpeedMultiplier", this.flySpeedMultiplier);
      out.addProperty("abilityDamageMultiplier", this.abilityDamageMultiplier);
      JsonObject a = new JsonObject();
      for (Map.Entry<String, Boolean> e : this.abilities.entrySet()) {
         a.addProperty(e.getKey(), e.getValue());
      }
      out.add("abilities", a);
      out.addProperty("riderSeeThrough", this.riderSeeThrough);
      out.addProperty("wingSoundVolume", this.wingSoundVolume);
      out.addProperty("wingSoundPitch", this.wingSoundPitch);
      out.addProperty("sneakFliesDown", this.sneakFliesDown);
      JsonObject h = new JsonObject();
      help(h, "items", "Item lists take item ids (\"minecraft:diamond\" or just \"diamond\") and tags (\"#minecraft:meat\"). An empty list [] keeps the default.");
      help(h, "tameItems", "Items that tame a wild adult dragon, e.g. [\"diamond\", \"emerald\", \"gold_ingot\"]. Default: meat (Tide Wyrm also fish).");
      help(h, "tameChance", "Chance per item that the dragon is tamed (0..1). Default 0.333.");
      help(h, "foodItems", "Items that heal a tamed dragon and help a hatchling grow. Default: meat (Tide Wyrm also fish).");
      help(h, "breedItems", "Items for breeding. Default: golden apples.");
      help(h, "multipliers", "healthMultiplier, attackDamageMultiplier, armorMultiplier, walkSpeedMultiplier, flySpeedMultiplier, abilityDamageMultiplier: 1.0 = normal, 2.0 = double.");
      help(h, "abilities", "false switches an ability off for every dragon (riders and wild dragons, and a hybrid's blood effect).");
      help(h, "riderSeeThrough", "\"off\", \"first_person\" or \"always\": the dragon you ride is drawn see-through (only for you) so it does not block the view.");
      help(h, "wingSound", "wingSoundVolume and wingSoundPitch: 1.0 = normal; a lower pitch sounds heavier.");
      help(h, "sneakFliesDown", "false (default): the sneak key (Left Shift) dismounts, also in the air. true: in the air the sneak key flies down and you only dismount on the ground or in water. In multiplayer set it on the server and in your own game.");
      help(h, "restart", "Changes take effect after restarting the game / server.");
      out.add("_help", h);
      return out;
   }

   private static boolean bool(JsonObject json, String key, boolean def) {
      try {
         return json.has(key) ? json.get(key).getAsBoolean() : def;
      } catch (RuntimeException e) {
         return def;
      }
   }

   private static float number(JsonObject json, String key, float def, float min, float max) {
      try {
         if (json.has(key)) {
            float v = json.get(key).getAsFloat();
            if (!Float.isNaN(v)) {
               return Math.max(min, Math.min(max, v));
            }
         }
      } catch (RuntimeException ignored) {
      }
      return def;
   }

   private static List<String> strings(JsonObject json, String key) {
      List<String> out = new ArrayList<>();
      if (json.has(key)) {
         JsonElement e = json.get(key);
         try {
            if (e.isJsonArray()) {
               for (JsonElement x : e.getAsJsonArray()) {
                  out.add(x.getAsString());
               }
            } else if (e.isJsonPrimitive()) {
               out.add(e.getAsString());
            }
         } catch (RuntimeException ignored) {
         }
      }
      return out;
   }

   private static JsonArray array(List<String> list) {
      JsonArray a = new JsonArray();
      for (String s : list) {
         a.add(s);
      }
      return a;
   }

   private static void help(JsonObject h, String key, String text) {
      h.addProperty(key, text);
   }
}
