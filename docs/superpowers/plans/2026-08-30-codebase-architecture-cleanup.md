# Codebase Architecture Cleanup Implementation Plan

**Goal:** Reorganize FBM's source layout around explicit runtime responsibilities while preserving all gameplay, persistence, command, and compatibility behavior.

**Architecture:** Keep `FishBreedingManager` as the composition root, move server lifecycle work into a dedicated coordinator, route optional third-party integrations through one compatibility boundary, and split the overloaded breeding package into focused `feed` and `spawn` subpackages. Document the resulting dependency direction so later P1 GUI/network work has a stable placement guide.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1, ModDevGradle, JUnit 5, Mockito.

---

## Task 1: Protect the refactor with a clean baseline

**Files:**
- Verify: `src/test/java/com/fishbreedingmanager/**`

1. Run `.\gradlew.bat test` before any production change.
2. Require the existing 26 suites / 78 tests to pass.
3. Treat this suite as the characterization boundary for package moves and lifecycle extraction.

## Task 2: Add a generic compatibility coordination boundary

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/compat/CompatibilityCoordinator.java`
- Create: `src/test/java/com/fishbreedingmanager/compat/CompatibilityCoordinatorTest.java`
- Modify: `src/main/java/com/fishbreedingmanager/event/EntityLifecycleHandler.java`
- Modify: `src/main/java/com/fishbreedingmanager/breeding/WorldBreedingService.java`

1. Write tests proving that all registered compatibility modules are invoked and that one optional module failure does not prevent later modules from running.
2. Run the focused test and confirm it fails before the coordinator exists.
3. Implement a production singleton whose current module delegates to Animal Feeding Trough without introducing a hard dependency on third-party Java types.
4. Replace core references to the feeding-trough-specific class with the generic coordinator.
5. Run the focused compatibility, lifecycle, and world-service tests.

## Task 3: Extract server lifecycle orchestration from the mod entrypoint

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/server/ServerLifecycleHandler.java`
- Modify: `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`

1. Move rule loading, loaded-entity restoration, discovery rebuilds, tag reload handling, and per-server cleanup into `ServerLifecycleHandler` without changing event timing.
2. Leave the mod entrypoint responsible only for registrations and composition.
3. Run compilation plus lifecycle/discovery tests after the extraction.

## Task 4: Split the breeding runtime by capability

**Files:**
- Move: `breeding/BreedingFeedService.java` -> `breeding/feed/BreedingFeedService.java`
- Move: `breeding/LoveParticleEmitter.java` -> `breeding/feed/LoveParticleEmitter.java`
- Move: `breeding/ChildSpawner.java` -> `breeding/spawn/ChildSpawner.java`
- Move: `breeding/ChildSpawnResult.java` -> `breeding/spawn/ChildSpawnResult.java`
- Move: `breeding/ChildSpawnStatus.java` -> `breeding/spawn/ChildSpawnStatus.java`
- Move matching tests into mirrored packages.
- Modify all affected imports in runtime, events, and feeding-trough compatibility.

1. Move one capability group at a time and update only package declarations/imports.
2. Preserve package-private test seams by moving the corresponding tests with each capability.
3. Run the focused feed tests, then the focused spawn/controller tests.
4. Run the full test suite after both moves.

## Task 5: Document the resulting architecture and handoff

**Files:**
- Create: `docs/ARCHITECTURE.md`
- Modify: `.agent/HANDOFF.md`

1. Record the composition root, server lifecycle, breeding runtime, persistence, discovery, compatibility, network/client, and command boundaries.
2. State dependency and placement rules for future P1 features.
3. Refresh the handoff to reflect the new paths, current verification, and next work.

## Task 6: Final verification and review

**Files:**
- Verify: entire project

1. Run `.\gradlew.bat test --rerun-tasks`.
2. Run `.\gradlew.bat javadoc --rerun-tasks`.
3. Run `.\gradlew.bat build --rerun-tasks`.
4. Perform an independent code review focused on behavior drift, event registration, compatibility failure isolation, package visibility, and stale paths.
5. Address any confirmed issue and repeat the affected checks.
