# Agent instructions (SneakyBagOfHolding)

Follow the Team-Sneakymouse cross-repo rules in EmptyPlugin's AGENTS.md; this file adds rules specific to this repo.

## Repo-specific rules

- **Write bag JSON atomically.** `PlayerDataStore.save()` currently uses `file.writeText`. Use `com.sneakyutils.util.io.AtomicFiles` (as SneakyKnives and SneakySpiritBinder do) when you touch it, and on a corrupt read move the file aside instead of returning empty data that the next save would overwrite.
- **Snapshot before the async save.** The autosave runs `saveDirty` on an async timer. Serialize to a JSON string on the main thread (or copy `PlayerData`) first; don't let Gson walk live maps from the async task.
- **`BagOfHoldingApi` is a contract.** SneakyGroundItems calls `BagOfHoldingApi.get()` (null when absent). Keep it null-safe and keep `WithdrawResult` / `WithdrawFailureReason` stable; make additive changes only.
- **Capacity is `item + categories + global`.** `CapacityService` computes `effectiveMax` this way; categories without a menu icon still count. Config changes must keep it.
- **Stored keys are MagicSpells item ids** (e.g. `item-banana`) in `stored`, `autopickup` and `itemCapacity`. Renaming an item in MagicSpells-Data orphans stored amounts. Check `tools/migrate_*.py` before changing the format.
- **Menu clicks cancel and defer.** Inventory handlers cancel the click and defer (re)opens with `runTask`; never mutate the open inventory inside the event callback.
