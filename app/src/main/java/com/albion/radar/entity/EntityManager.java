package com.albion.radar.entity;

import com.albion.radar.parser.EntityUpdate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity Manager
 *
 * Tracks all entities (players, mobs, resources) in the game world
 * Provides filtering and query capabilities for the radar
 */
public class EntityManager {

    private static EntityManager instance;

    // Entity storage
    private final ConcurrentHashMap<Integer, Entity> entities;

    // Local player info
    private int localPlayerId = -1;
    private float localPosX = 0;
    private float localPosY = 0;

    // Configuration
    private boolean showPlayers = true;
    private boolean showMobs = true;
    private boolean showHarvestables = true;
    private boolean showBosses = true;
    private boolean showLivingHarvestables = true;

    // Minimum tier filter
    private int minTierFilter = 0;

    // Maximum distance filter (0 = unlimited)
    private float maxDistanceFilter = 0;

    // Entity expiration time (ms)
    private static final long ENTITY_EXPIRY = 60000;  // 60 seconds

    private EntityManager() {
        entities = new ConcurrentHashMap<>();
    }

    public static synchronized EntityManager getInstance() {
        if (instance == null) {
            instance = new EntityManager();
        }
        return instance;
    }

    /**
     * Process an entity update from packet parser
     */
    public void processUpdate(EntityUpdate update) {
        if (update == null) return;

        switch (update.type) {
            case EntityUpdate.TYPE_SPAWN:
                handleSpawn(update);
                break;

            case EntityUpdate.TYPE_MOVE:
                handleMove(update);
                break;

            case EntityUpdate.TYPE_REMOVE:
                handleRemove(update);
                break;

            case EntityUpdate.TYPE_HEALTH_UPDATE:
                handleHealthUpdate(update);
                break;
        }
    }

    /**
     * Handle entity spawn
     */
    private void handleSpawn(EntityUpdate update) {
        Entity entity = entities.get(update.id);

        if (entity == null) {
            entity = new Entity();
            entity.id = update.id;
            entity.spawnTime = System.currentTimeMillis();
        }

        // Update entity data
        entity.entityType = update.entityType;
        entity.characterType = update.characterType;
        entity.harvestableType = update.harvestableType;
        entity.mobType = update.mobType;
        entity.name = update.name;
        entity.guild = update.guild;
        entity.alliance = update.alliance;
        entity.tier = update.tier;
        entity.enchantment = update.enchantment;
        entity.rarity = update.rarity;
        entity.faction = update.faction;
        entity.health = update.health;
        entity.maxHealth = update.maxHealth;
        entity.lastUpdate = System.currentTimeMillis();

        if (update.hasValidPosition) {
            entity.posX = update.posX;
            entity.posY = update.posY;
            entity.hasValidPosition = true;
        }

        entities.put(update.id, entity);
    }

    /**
     * Handle entity movement
     */
    private void handleMove(EntityUpdate update) {
        Entity entity = entities.get(update.id);

        if (entity != null && update.hasValidPosition) {
            entity.posX = update.posX;
            entity.posY = update.posY;
            entity.hasValidPosition = true;
            entity.lastUpdate = System.currentTimeMillis();
        } else if (entity == null && update.hasValidPosition) {
            // Entity not found but has position - spawn it
            handleSpawn(update);
        }
    }

    /**
     * Handle entity removal
     */
    private void handleRemove(EntityUpdate update) {
        entities.remove(update.id);
    }

    /**
     * Handle health update
     */
    private void handleHealthUpdate(EntityUpdate update) {
        Entity entity = entities.get(update.id);

        if (entity != null) {
            entity.health = update.health;
            entity.maxHealth = update.maxHealth;
            entity.lastUpdate = System.currentTimeMillis();

            // Remove dead entities
            if (entity.maxHealth > 0 && entity.health <= 0) {
                entities.remove(update.id);
            }
        }
    }

    /**
     * Update local player position
     */
    public void updateLocalPosition(float x, float y) {
        this.localPosX = x;
        this.localPosY = y;
    }

    /**
     * Set local player ID
     */
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
    }

    /**
     * Get all visible entities matching filters
     */
    public List<Entity> getVisibleEntities() {
        List<Entity> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<Integer, Entity>> iterator = entities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Entity> entry = iterator.next();
            Entity entity = entry.getValue();

            // Remove expired entities
            if (now - entity.lastUpdate > ENTITY_EXPIRY) {
                iterator.remove();
                continue;
            }

            // Skip local player
            if (entity.id == localPlayerId) {
                continue;
            }

            // Apply filters
            if (!passesFilters(entity)) {
                continue;
            }

            result.add(entity);
        }

        return result;
    }

    /**
     * Check if entity passes all filters
     */
    private boolean passesFilters(Entity entity) {
        // Entity type filter
        if (entity.isPlayer() && !showPlayers) return false;
        if (entity.isMob() && !showMobs) return false;
        if (entity.isHarvestable() && !showHarvestables) return false;
        if (entity.isBoss() && !showBosses) return false;
        if (entity.isLivingHarvestable() && !showLivingHarvestables) return false;

        // Tier filter
        if (entity.tier > 0 && entity.tier < minTierFilter) return false;

        // Distance filter
        if (maxDistanceFilter > 0 && entity.hasValidPosition) {
            float distance = entity.getDistanceTo(localPosX, localPosY);
            if (distance > maxDistanceFilter) return false;
        }

        // Position validity
        if (!entity.hasValidPosition) return false;

        return true;
    }

    /**
     * Get entities by type
     */
    public List<Entity> getEntitiesByType(int entityType) {
        List<Entity> result = new ArrayList<>();

        for (Entity entity : entities.values()) {
            if (entity.entityType == entityType && entity.hasValidPosition) {
                result.add(entity);
            }
        }

        return result;
    }

    /**
     * Get all players
     */
    public List<Entity> getPlayers() {
        List<Entity> result = new ArrayList<>();

        for (Entity entity : entities.values()) {
            if (entity.isPlayer() && entity.hasValidPosition && entity.id != localPlayerId) {
                result.add(entity);
            }
        }

        return result;
    }

    /**
     * Get all mobs
     */
    public List<Entity> getMobs() {
        List<Entity> result = new ArrayList<>();

        for (Entity entity : entities.values()) {
            if (entity.isMob() && entity.hasValidPosition) {
                result.add(entity);
            }
        }

        return result;
    }

    /**
     * Get all harvestables
     */
    public List<Entity> getHarvestables() {
        List<Entity> result = new ArrayList<>();

        for (Entity entity : entities.values()) {
            if (entity.isHarvestable() && entity.hasValidPosition) {
                result.add(entity);
            }
        }

        return result;
    }

    /**
     * Get all bosses
     */
    public List<Entity> getBosses() {
        List<Entity> result = new ArrayList<>();

        for (Entity entity : entities.values()) {
            if (entity.isBoss() && entity.hasValidPosition) {
                result.add(entity);
            }
        }

        return result;
    }

    /**
     * Get entities within range
     */
    public List<Entity> getEntitiesInRange(float x, float y, float range) {
        List<Entity> result = new ArrayList<>();

        for (Entity entity : entities.values()) {
            if (entity.hasValidPosition && entity.getDistanceTo(x, y) <= range) {
                result.add(entity);
            }
        }

        return result;
    }

    /**
     * Get nearest entity of type
     */
    public Entity getNearestEntity(int entityType, float x, float y) {
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;

        for (Entity entity : entities.values()) {
            if (entity.entityType == entityType && entity.hasValidPosition) {
                float dist = entity.getDistanceTo(x, y);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = entity;
                }
            }
        }

        return nearest;
    }

    /**
     * Clear all entities
     */
    public void clear() {
        entities.clear();
    }

    /**
     * Get entity count
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Get entity by ID
     */
    public Entity getEntity(int id) {
        return entities.get(id);
    }

    // Getters and setters for filters

    public boolean isShowPlayers() {
        return showPlayers;
    }

    public void setShowPlayers(boolean showPlayers) {
        this.showPlayers = showPlayers;
    }

    public boolean isShowMobs() {
        return showMobs;
    }

    public void setShowMobs(boolean showMobs) {
        this.showMobs = showMobs;
    }

    public boolean isShowHarvestables() {
        return showHarvestables;
    }

    public void setShowHarvestables(boolean showHarvestables) {
        this.showHarvestables = showHarvestables;
    }

    public boolean isShowBosses() {
        return showBosses;
    }

    public void setShowBosses(boolean showBosses) {
        this.showBosses = showBosses;
    }

    public boolean isShowLivingHarvestables() {
        return showLivingHarvestables;
    }

    public void setShowLivingHarvestables(boolean showLivingHarvestables) {
        this.showLivingHarvestables = showLivingHarvestables;
    }

    public int getMinTierFilter() {
        return minTierFilter;
    }

    public void setMinTierFilter(int minTierFilter) {
        this.minTierFilter = minTierFilter;
    }

    public float getMaxDistanceFilter() {
        return maxDistanceFilter;
    }

    public void setMaxDistanceFilter(float maxDistanceFilter) {
        this.maxDistanceFilter = maxDistanceFilter;
    }

    public float getLocalPosX() {
        return localPosX;
    }

    public float getLocalPosY() {
        return localPosY;
    }
        }
