package com.fishbreedingmanager;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Client-only entrypoint for Fish Breeding Manager.
 *
 * <p>This class is only loaded on the physical client. Client-side concerns (keybinds for opening the
 * management GUI, screen factories, the juvenile visual-scaling render hook, and the 3D entity preview)
 * will be wired here as those features are implemented in later phases.
 */
@Mod(value = FishBreedingManager.MOD_ID, dist = Dist.CLIENT)
public final class FishBreedingManagerClient {
    public FishBreedingManagerClient(ModContainer container) {
        // Client setup is added incrementally per phase.
    }
}
