package com.eliteessentials.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a player-created warp location.
 * Player warps can be public (visible to everyone) or private (owner only).
 */
public class PlayerWarp {

    public enum Visibility {
        PUBLIC,
        PRIVATE
    }

    private String name;
    private Location location;
    private UUID ownerId;
    private String ownerName;
    private Visibility visibility;
    private String description;
    private long createdAt;

    public PlayerWarp() {
        // For Gson deserialization
    }

    public PlayerWarp(String name, Location location, UUID ownerId, String ownerName,
                      Visibility visibility, String description) {
        this.name = name;
        this.location = location;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.visibility = visibility;
        this.description = description != null ? description : "";
        this.createdAt = Instant.now().toEpochMilli();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public boolean isPublic() { return visibility == Visibility.PUBLIC; }
    public boolean isPrivate() { return visibility == Visibility.PRIVATE; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description != null ? description : ""; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    /**
     * Check if a player can access this warp.
     * Public warps are accessible to everyone, private warps only to the owner.
     */
    public boolean canAccess(UUID playerId) {
        return isPublic() || ownerId.equals(playerId);
    }

    @Override
    public String toString() {
        return String.format("PlayerWarp{name='%s', owner='%s', visibility=%s, location=%s}",
                name, ownerName, visibility, location);
    }
}
