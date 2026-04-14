package com.albion.radar.parser;

/**
 * Represents an update for an entity in the game world
 */
public class EntityUpdate {

    // Update types
    public static final int TYPE_SPAWN = 1;
    public static final int TYPE_MOVE = 2;
    public static final int TYPE_REMOVE = 3;
    public static final int TYPE_HEALTH_UPDATE = 4;

    // Entity types
    public static final int ENTITY_CHARACTER = 1;
    public static final int ENTITY_HARVESTABLE = 2;
    public static final int ENTITY_MOB = 3;
    public static final int ENTITY_PLAYER = 4;

    // Character types (Albion-specific)
    public static final int CHAR_TYPE_PLAYER = 1;
    public static final int CHAR_TYPE_MOB = 2;
    public static final int CHAR_TYPE_NPC = 3;
    public static final int CHAR_TYPE_SKINNABLE = 4;

    // Mob types
    public static final int MOB_TYPE_NORMAL = 0;
    public static final int MOB_TYPE_AGGRESSIVE = 1;
    public static final int MOB_TYPE_PASSIVE = 2;
    public static final int MOB_TYPE_BOSS = 3;
    public static final int MOB_TYPE_MINIBOSS = 4;
    public static final int MOB_TYPE_DRONE = 5;
    public static final int MOB_TYPE_MIST_BOSS = 6;

    // Harvestable types
    public static final int HARVEST_TYPE_FIBER = 0;
    public static final int HARVEST_TYPE_WOOD = 1;
    public static final int HARVEST_TYPE_ORE = 2;
    public static final int HARVEST_TYPE_ROCK = 3;
    public static final int HARVEST_TYPE_HIDE = 4;
    public static final int HARVEST_TYPE_FISH = 5;
    public static final int HARVEST_TYPE_TREASURE = 6;
    public static final int HARVEST_TYPE_ESSENCE = 7;

    // Rarity types for Mists
    public static final int RARITY_COMMON = 0;
    public static final int RARITY_UNCOMMON = 1;
    public static final int RARITY_RARE = 2;
    public static final int RARITY_EPIC = 3;
    public static final int RARITY_LEGENDARY = 4;

    // Update type
    public int type;

    // Entity type
    public int entityType;

    // Entity ID
    public int id;

    // Position
    public float posX;
    public float posY;
    public boolean hasValidPosition;

    // Character info
    public int characterType;
    public String name;
    public String guild;
    public String alliance;

    // Health
    public float health;
    public float maxHealth;

    // Faction
    public int faction;

    // Harvestable info
    public int harvestableType;
    public int tier;
    public int enchantment;
    public int rarity;

    // Mob info
    public int mobType;

    // Target (for combat)
    public int targetId;

    // Timestamp
    public long timestamp;

    public EntityUpdate() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Check if this is a player
     */
    public boolean isPlayer() {
        return entityType == ENTITY_CHARACTER && characterType == CHAR_TYPE_PLAYER;
    }

    /**
     * Check if this is a mob
     */
    public boolean isMob() {
        return entityType == ENTITY_MOB ||
               (entityType == ENTITY_CHARACTER && characterType == CHAR_TYPE_MOB);
    }

    /**
     * Check if this is a boss
     */
    public boolean isBoss() {
        return mobType == MOB_TYPE_BOSS || mobType == MOB_TYPE_MINIBOSS || mobType == MOB_TYPE_MIST_BOSS;
    }

    /**
     * Check if this is a harvestable resource
     */
    public boolean isHarvestable() {
        return entityType == ENTITY_HARVESTABLE;
    }

    /**
     * Check if this is a living harvestable (skinnable)
     */
    public boolean isLivingHarvestable() {
        return entityType == ENTITY_CHARACTER && characterType == CHAR_TYPE_SKINNABLE;
    }

    /**
     * Check if entity is enchanted
     */
    public boolean isEnchanted() {
        return enchantment > 0;
    }

    /**
     * Get tier color based on tier and enchantment
     * Returns a color integer (ARGB)
     */
    public int getTierColor() {
        // Base tier colors (without alpha)
        int[] tierColors = {
            0xFF000000, // T0 - Black (unused)
            0xFF1C1C1C, // T1 - Dark Grey
            0xFF808080, // T2 - Grey
            0xFF00FF00, // T3 - Green
            0xFF0000FF, // T4 - Blue
            0xFFFF0000, // T5 - Red
            0xFFFF8000, // T6 - Orange
            0xFFFFFF00, // T7 - Yellow
            0xFFFFFFFF  // T8 - White
        };

        int color = tierColors[Math.min(tier, 8)];

        // Enchantment adds outline color
        // This would be handled in the renderer, but we return the base color

        return color;
    }

    /**
     * Get enchantment outline color
     */
    public int getEnchantmentColor() {
        int[] enchantColors = {
            0x00000000, // .0 - No enchant
            0xFF006400, // .1 - Dark Green
            0xFF00008B, // .2 - Dark Blue
            0xFF800080, // .3 - Purple
            0xFFFFD700  // .4 - Gold
        };

        return enchantColors[Math.min(enchantment, 4)];
    }

    /**
     * Get rarity color for Mists
     */
    public int getRarityColor() {
        int[] rarityColors = {
            0xFF0000FF, // Common - Blue
            0xFF00FF00, // Uncommon - Green
            0xFF00008B, // Rare - Dark Blue
            0xFF800080, // Epic - Purple
            0xFFFFD700  // Legendary - Gold
        };

        return rarityColors[Math.min(rarity, 4)];
    }

    /**
     * Get distance from another position
     */
    public float distanceTo(float x, float y) {
        if (!hasValidPosition) return Float.MAX_VALUE;
        float dx = posX - x;
        float dy = posY - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityUpdate{");
        sb.append("id=").append(id);
        sb.append(", type=").append(type);
        sb.append(", entityType=").append(entityType);

        if (hasValidPosition) {
            sb.append(", pos=(").append(posX).append(", ").append(posY).append(")");
        }

        if (name != null) {
            sb.append(", name='").append(name).append("'");
        }

        if (tier > 0) {
            sb.append(", tier=").append(tier);
            if (enchantment > 0) {
                sb.append(".").append(enchantment);
            }
        }

        sb.append("}");
        return sb.toString();
    }
}
