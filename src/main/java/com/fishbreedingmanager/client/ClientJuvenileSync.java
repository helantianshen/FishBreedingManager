package com.fishbreedingmanager.client;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fishbreedingmanager.network.JuvenileStatePayload;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side store of juvenile state received from the server.
 *
 * <p>Maps an entity UUID to the absolute game-time tick at which it matures. A render hook (added in
 * the next step) reads this to apply the 0.5→1.0 visual scale to juveniles. Entries expire lazily
 * when read past {@code adultAt} (requirement §10).
 */
public final class ClientJuvenileSync {
    /** entity UUID → (adultAt, growthTicks). */
    private static final ConcurrentHashMap<UUID, long[]> JUVENILES = new ConcurrentHashMap<>();

    private ClientJuvenileSync() {
    }

    /** Server→client payload handler (registered in {@link com.fishbreedingmanager.network.ModNetworking}). */
    public static void handleJuvenileState(JuvenileStatePayload payload, IPayloadContext context) {
        JUVENILES.put(payload.entityUuid(), new long[]{payload.adultAt(), payload.growthTicks()});
    }

    /** @return the visual scale (0.5 juvenile → 1.0 adult) for the given entity, or 1.0 if unknown/mature. */
    public static float scaleFor(UUID entityUuid, long now) {
        long[] data = JUVENILES.get(entityUuid);
        if (data == null) {
            return 1.0F;
        }
        long adultAt = data[0];
        if (now >= adultAt) {
            JUVENILES.remove(entityUuid);
            return 1.0F;
        }
        long growth = Math.max(1L, data[1]);
        float progress = (float) (now - (adultAt - growth)) / (float) growth;
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        return 0.5F + 0.5F * progress;
    }

    /** Removes the entry (e.g. when an entity is unloaded). */
    public static void forget(UUID entityUuid) {
        JUVENILES.remove(entityUuid);
    }
}
