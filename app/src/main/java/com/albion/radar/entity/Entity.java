package com.albion.radar.entity;

import com.albion.radar.parser.EntityUpdate;

/**
 * Represents an entity in the game world
 */
public class Entity {

    // ID
    public int id;

    // Type
    public int entityType;
    public int characterType;
    public int harvestableType;
    public int mobType;

    // Position
    public float posX;
    public float posY;
    public boolean hasValidPosition;

    // Info
    public String name;
    public String guild;
    public String alliance;

    // Resource info
    public int tier;
    public int enchantment;
    public int rarity;

    // Combat
    public int faction;
    public float health;
    public float maxHealth;

    // Timestamps
    public long spawnTime;
    public long lastUpdate;

    /**
     * Check if this is a player
     */
    public boolean isPlayer() {
        return entityType == EntityUpdate.ENTITY_CHARACTER &&
               characterType == EntityUpdate.CHAR_TYPE_PLAYER;
    }

    /**
     * Check if this is a mob
     */
    public boolean isMob() {
        return entityType == EntityUpdate.ENTITY_MOB ||
               (entityType == EntityUpdate.ENTITY_CHARACTER &&
                characterType == EntityUpdate.CHAR_TYPE_MOB);
    }

    /**
     * Check if this is a boss
     */
    public boolean isBoss() {
        return mobType == EntityUpdate.MOB_TYPE_BOSS ||
               mobType == EntityUpdate.MOB_TYPE_MINIBOSS ||
               mobType == EntityUpdate.MOB_TYPE_MIST_BOSS;
    }

    /**
     * Check if this is a harvestable
     */
    public boolean isHarvestable() {
        return entityType == EntityUpdate.ENTITY_HARVESTABLE;
    }

    /**
     * Check if this is a living harvestable (skinnable)
     */
    public boolean isLivingHarvestable() {
        return entityType == EntityUpdate.ENTITY_CHARACTER &&
               characterType == EntityUpdate.CHAR_TYPE_SKINNABLE;
    }

    /**
     * Check if entity is enchanted
     */
    public boolean isEnchanted() {
        return enchantment > 0;
    }

    /**
     * Get distance to a point
     */
    public float getDistanceTo(float x, float y) {
        if (!hasValidPosition) return Float.MAX_VALUE;
        float dx = posX - x;
        float dy = posY - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Get distance to another entity
     */
    public float getDistanceTo(Entity other) {
        if (!hasValidPosition || !other.hasValidPosition) return Float.MAX_VALUE;
        return getDistanceTo(other.posX, other.posY);
    }

    /**
     * Get health percentage
     */
    public float getHealthPercent() {
        if (maxHealth <= 0) return 0;
        return (health / maxHealth) * 100;
    }

    /**
     * Check if entity is dead
     */
    public boolean isDead() {
        return maxHealth > 0 && health <= 0;
    }

    /**
     * Get tier as Roman numeral string
     */
    public String getTierString() {
        String[] tiers = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII"};
        String tierStr = tiers[Math.min(tier, 8)];

        if (enchantment > 0) {
            tierStr += "." + enchantment;
        }

        return tierStr;
    }

    /**
     * Get base tier color
     */
    public int getTierColor() {
        int[] tierColors = {
            0xFF1C1C1C,  // T1 - Dark Grey
            0xFF1C1C1C,  // T1
            0xFF808080,  // T2 - Grey
            0xFF00FF00,  // T3 - Green
            0xFF0000FF,  // T4 - Blue
            0xFFFF0000,  // T5 - Red
            0xFFFF8000,  // T6 - Orange
            0xFFFFFF00,  // T7 - Yellow
            0xFFFFFFFF   // T8 - White
        };

        return tierColors[Math.min(tier, 8)];
    }

    /**
     * Get enchantment outline color
     */
    public int getEnchantmentColor() {
        int[] enchantColors = {
            0x00000000,  // .0 - No enchant
            0xFF006400,  // .1 - Dark Green
            0xFF00008B,  // .2 - Dark Blue
            0xFF800080,  // .3 - Purple
            0xFFFFD700   // .4 - Gold
        };

        return enchantColors[Math.min(enchantment, 4)];
    }

    /**
     * Get rarity color for Mist entities
     */
    public int getRarityColor() {
        int[] rarityColors = {
            0xFF0000FF,  // Common - Blue
            0xFF00FF00,  // Uncommon - Green
            0xFF00008B,  // Rare - Dark Blue
            0xFF800080,  // Epic - Purple
            0xFFFFD700   // Legendary - Gold
        };

        return rarityColors[Math.min(rarity, 4)];
    }

    /**
     * Get display name
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // Generate name based on type
        if (isHarvestable()) {
            return getHarvestableTypeName() + " " + getTierString();
        }

        if (isMob()) {
            String prefix = isBoss() ? "[BOSS] " : "";
            return prefix + getMobTypeName();
        }

        return "Unknown";
    }

    /**
     * Get harvestable type name
     */
    public String getHarvestableTypeName() {
        String[] types = {
            "Fiber", "Wood", "Ore", "Rock", "Hide", "Fish", "Treasure", "Essence"
        };

        if (harvestableType >= 0 && harvestableType < types.length) {
            return types[harvestableType];
        }

        return "Resource";
    }

    /**
     * Get mob type name
     */
    public String getMobTypeName() {
        // This would need to be mapped from mob type IDs
        // For now return generic name
        if (isBoss()) {
            switch (mobType) {
                case EntityUpdate.MOB_TYPE_BOSS: return "Boss";
                case EntityUpdate.MOB_TYPE_MINIBOSS: return "Mini Boss";
                case EntityUpdate.MOB_TYPE_MIST_BOSS: return "Mist Boss";
            }
        }

        return "Mob";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Entity{");
        sb.append("id=").append(id);

        if (name != null) {
            sb.append(", name='").append(name).append("'");
        }

        if (hasValidPosition) {
            sb.append(", pos=(").append(String.format("%.1f", posX))
              .append(", ").append(String.format("%.1f", posY)).append(")");
        }

        if (tier > 0) {
            sb.append(", tier=").append(getTierString());
        }

        sb.append("}");
        return sb.toString();
    }
}
