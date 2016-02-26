package io.minimum.minecraft.rbclean;

import java.util.Calendar;
import java.util.UUID;

/**
 * Created by tux on 2/26/16.
 */
class CachedUUIDEntry {
    private final String name;
    private final UUID uuid;
    private final Calendar expiry;

    private CachedUUIDEntry(String name, UUID uuid, Calendar expiry) {
        this.name = name;
        this.uuid = uuid;
        this.expiry = expiry;
    }

    public boolean expired() {
        return Calendar.getInstance().after(expiry);
    }
}
