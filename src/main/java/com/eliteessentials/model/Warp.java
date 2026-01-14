package com.eliteessentials.model;

import java.time.Instant;

/**
 * Represents a server warp location.
 * Warps can be restricted to OP-only or available to all players.
 */
public class Warp {

    public enum Permission {
        ALL,  // Everyone can use this warp
        OP    // Only OPs/admins can use this warp
    }

    private String name;
    private Location location;
    private Permission permission;
    private String createdBy;
    private long createdAt;

    public Warp() {
        // For Gson deserialization
    }

    public Warp(String name, Location location, Permission permission, String createdBy) {
        this.name = name;
        this.location = location;
        this.permission = permission;
        this.createdBy = createdBy;
        this.createdAt = Instant.now().toEpochMilli();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isOpOnly() {
        return permission == Permission.OP;
    }

    @Override
    public String toString() {
        return String.format("Warp{name='%s', permission=%s, location=%s}", name, permission, location);
    }
}
