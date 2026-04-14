package com.albion.radar.parser;

import java.util.Hashtable;

/**
 * Albion Online Entity Parser
 *
 * Parses Albion-specific game events from Photon packets
 * Handles entity spawning, movement, and removal
 */
public class AlbionEntityParser {

    // Albion event codes (these are game-specific)
    public static final int EVENT_NEW_CHARACTER = 29;
    public static final int EVENT_MOVE = 3;
    public static final int EVENT_CHARACTER_MOVE = 49;
    public static final int EVENT_REMOVE_CHARACTER = 252;
    public static final int EVENT_HEALTH_UPDATE = 10;
    public static final int EVENT_NEW_HARVESTABLE = 38;
    public static final int EVENT_REMOVE_HARVESTABLE = 39;
    public static final int EVENT_NEW_MOB = 40;
    public static final int EVENT_SIMPLE_MOB = 41;
    public static final int EVENT_UPDATE_POSITION = 52;

    // Parameter keys in event data
    public static final int PARAM_ID = 0;
    public static final int PARAM_TYPE = 1;
    public static final int PARAM_NAME = 2;
    public static final int PARAM_GUILD = 3;
    public static final int PARAM_ALLIANCE = 4;
    public static final int PARAM_POSITION_X = 5;
    public static final int PARAM_POSITION_Y = 6;
    public static final int PARAM_POSITION = 7;
    public static final int PARAM_HEALTH = 10;
    public static final int PARAM_MAX_HEALTH = 11;
    public static final int PARAM_TIER = 20;
    public static final int PARAM_ENCHANTMENT = 21;
    public static final int PARAM_RARITY = 22;
    public static final int PARAM_FACTION = 30;
    public static final int PARAM_TARGET_ID = 40;
    public static final int PARAM_EFFECTS = 50;

    /**
     * Parse an event and return structured entity data
     */
    public static EntityUpdate parseEvent(int eventCode, Hashtable<Object, Object> parameters) {
        if (parameters == null) return null;

        switch (eventCode) {
            case EVENT_NEW_CHARACTER:
                return parseNewCharacter(parameters);

            case EVENT_MOVE:
            case EVENT_CHARACTER_MOVE:
                return parseMove(parameters);

            case EVENT_REMOVE_CHARACTER:
                return parseRemoveCharacter(parameters);

            case EVENT_NEW_HARVESTABLE:
                return parseNewHarvestable(parameters);

            case EVENT_REMOVE_HARVESTABLE:
                return parseRemoveHarvestable(parameters);

            case EVENT_NEW_MOB:
            case EVENT_SIMPLE_MOB:
                return parseNewMob(parameters);

            case EVENT_HEALTH_UPDATE:
                return parseHealthUpdate(parameters);

            default:
                return null;
        }
    }

    /**
     * Parse NewCharacter event (players, mobs, NPCs)
     */
    private static EntityUpdate parseNewCharacter(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_SPAWN;
        update.entityType = EntityUpdate.ENTITY_CHARACTER;

        // Extract ID (can be int or long)
        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Integer) {
            update.id = (Integer) idObj;
        } else if (idObj instanceof Long) {
            update.id = ((Long) idObj).intValue();
        } else if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        // Extract type (determines if player, mob, etc.)
        Object typeObj = params.get(PARAM_TYPE);
        if (typeObj instanceof Number) {
            update.characterType = ((Number) typeObj).intValue();
        }

        // Extract name
        Object nameObj = params.get(PARAM_NAME);
        if (nameObj instanceof String) {
            update.name = (String) nameObj;
        }

        // Extract guild
        Object guildObj = params.get(PARAM_GUILD);
        if (guildObj instanceof String) {
            update.guild = (String) guildObj;
        }

        // Extract alliance
        Object allianceObj = params.get(PARAM_ALLIANCE);
        if (allianceObj instanceof String) {
            update.alliance = (String) allianceObj;
        }

        // Extract position - this is critical!
        // Albion uses either separate X/Y or a combined position array
        parsePosition(params, update);

        // Extract health
        Object healthObj = params.get(PARAM_HEALTH);
        if (healthObj instanceof Number) {
            update.health = ((Number) healthObj).floatValue();
        }

        Object maxHealthObj = params.get(PARAM_MAX_HEALTH);
        if (maxHealthObj instanceof Number) {
            update.maxHealth = ((Number) maxHealthObj).floatValue();
        }

        // Extract faction (for faction mobs)
        Object factionObj = params.get(PARAM_FACTION);
        if (factionObj instanceof Number) {
            update.faction = ((Number) factionObj).intValue();
        }

        return update;
    }

    /**
     * Parse movement event
     */
    private static EntityUpdate parseMove(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_MOVE;
        update.entityType = EntityUpdate.ENTITY_CHARACTER;

        // Extract ID
        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        // Extract new position
        parsePosition(params, update);

        return update;
    }

    /**
     * Parse remove character event
     */
    private static EntityUpdate parseRemoveCharacter(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_REMOVE;
        update.entityType = EntityUpdate.ENTITY_CHARACTER;

        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        return update;
    }

    /**
     * Parse harvestable spawn (resources, chests, etc.)
     */
    private static EntityUpdate parseNewHarvestable(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_SPAWN;
        update.entityType = EntityUpdate.ENTITY_HARVESTABLE;

        // Extract ID
        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        // Extract type (resource type)
        Object typeObj = params.get(PARAM_TYPE);
        if (typeObj instanceof Number) {
            update.harvestableType = ((Number) typeObj).intValue();
        }

        // Extract tier
        Object tierObj = params.get(PARAM_TIER);
        if (tierObj instanceof Number) {
            update.tier = ((Number) tierObj).intValue();
        }

        // Extract enchantment level
        Object enchantObj = params.get(PARAM_ENCHANTMENT);
        if (enchantObj instanceof Number) {
            update.enchantment = ((Number) enchantObj).intValue();
        }

        // Extract rarity
        Object rarityObj = params.get(PARAM_RARITY);
        if (rarityObj instanceof Number) {
            update.rarity = ((Number) rarityObj).intValue();
        }

        // Extract position
        parsePosition(params, update);

        return update;
    }

    /**
     * Parse harvestable removal
     */
    private static EntityUpdate parseRemoveHarvestable(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_REMOVE;
        update.entityType = EntityUpdate.ENTITY_HARVESTABLE;

        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        return update;
    }

    /**
     * Parse mob spawn
     */
    private static EntityUpdate parseNewMob(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_SPAWN;
        update.entityType = EntityUpdate.ENTITY_MOB;

        // Extract ID
        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        // Extract mob type
        Object typeObj = params.get(PARAM_TYPE);
        if (typeObj instanceof Number) {
            update.mobType = ((Number) typeObj).intValue();
        }

        // Extract tier
        Object tierObj = params.get(PARAM_TIER);
        if (tierObj instanceof Number) {
            update.tier = ((Number) tierObj).intValue();
        }

        // Extract enchantment
        Object enchantObj = params.get(PARAM_ENCHANTMENT);
        if (enchantObj instanceof Number) {
            update.enchantment = ((Number) enchantObj).intValue();
        }

        // Extract position
        parsePosition(params, update);

        // Extract health
        Object healthObj = params.get(PARAM_HEALTH);
        if (healthObj instanceof Number) {
            update.health = ((Number) healthObj).floatValue();
        }

        Object maxHealthObj = params.get(PARAM_MAX_HEALTH);
        if (maxHealthObj instanceof Number) {
            update.maxHealth = ((Number) maxHealthObj).floatValue();
        }

        return update;
    }

    /**
     * Parse health update
     */
    private static EntityUpdate parseHealthUpdate(Hashtable<Object, Object> params) {
        EntityUpdate update = new EntityUpdate();
        update.type = EntityUpdate.TYPE_HEALTH_UPDATE;
        update.entityType = EntityUpdate.ENTITY_CHARACTER;

        Object idObj = params.get(PARAM_ID);
        if (idObj instanceof Number) {
            update.id = ((Number) idObj).intValue();
        }

        Object healthObj = params.get(PARAM_HEALTH);
        if (healthObj instanceof Number) {
            update.health = ((Number) healthObj).floatValue();
        }

        Object maxHealthObj = params.get(PARAM_MAX_HEALTH);
        if (maxHealthObj instanceof Number) {
            update.maxHealth = ((Number) maxHealthObj).floatValue();
        }

        return update;
    }

    /**
     * Parse position from parameters
     * Albion can send position in different formats:
     * 1. Separate X/Y parameters
     * 2. Position array [x, y]
     * 3. Compressed single value
     */
    private static void parsePosition(Hashtable<Object, Object> params, EntityUpdate update) {
        // Try separate X/Y first
        Object xObj = params.get(PARAM_POSITION_X);
        Object yObj = params.get(PARAM_POSITION_Y);

        if (xObj != null && yObj != null) {
            if (xObj instanceof Number && yObj instanceof Number) {
                // Check if values are very large (999999) - invalid spawn position
                float x = ((Number) xObj).floatValue();
                float y = ((Number) yObj).floatValue();

                if (x < 999999 && y < 999999) {
                    update.posX = x;
                    update.posY = y;
                    update.hasValidPosition = true;
                    return;
                }
            }
        }

        // Try position array
        Object posObj = params.get(PARAM_POSITION);
        if (posObj instanceof Object[]) {
            Object[] posArray = (Object[]) posObj;
            if (posArray.length >= 2) {
                if (posArray[0] instanceof Number && posArray[1] instanceof Number) {
                    float x = ((Number) posArray[0]).floatValue();
                    float y = ((Number) posArray[1]).floatValue();

                    if (x < 999999 && y < 999999) {
                        update.posX = x;
                        update.posY = y;
                        update.hasValidPosition = true;
                        return;
                    }
                }
            }
        }

        // Try int array
        if (posObj instanceof int[]) {
            int[] posArray = (int[]) posObj;
            if (posArray.length >= 2) {
                float x = posArray[0];
                float y = posArray[1];

                if (x < 999999 && y < 999999) {
                    update.posX = x;
                    update.posY = y;
                    update.hasValidPosition = true;
                }
            }
        }

        // Try float array
        if (posObj instanceof float[]) {
            float[] posArray = (float[]) posObj;
            if (posArray.length >= 2) {
                float x = posArray[0];
                float y = posArray[1];

                if (x < 999999 && y < 999999) {
                    update.posX = x;
                    update.posY = y;
                    update.hasValidPosition = true;
                }
            }
        }

        update.hasValidPosition = false;
    }
}
