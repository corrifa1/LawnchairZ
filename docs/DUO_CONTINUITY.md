# Duo continuity

Unfolding feels like the cover screen growing to the left. The cover-screen page you were on appears in the
inner screen's pane next to the dock, with a *companion* page in the other pane. Folding returns to the same
cover page.

It is a setting on foldables only: **Dock → Grid → Unfolded layout**.

| Value | Behavior |
| --- | --- |
| **Page pairs** (default) | Pages pair as (0, 1), (2, 3) with the even page on the left, and the cover shows every page. Unchanged. |
| **Cover page + companion** | The mode described below. |

Status: implemented and unit tested; **not yet tested on a device**. See the device checklist and known gaps.

## Behavior

- Screens pair as (2k, 2k + 1), the same pairs `Workspace.getScreenPair` already uses. The even screen is the
  **dock-side screen**: the cover-screen page, and the pane next to the dock when unfolded. The odd screen is its
  **companion**, shown only when unfolded.
- **Cover screen:** shows only dock-side screens. Companions are not bound (their items are not inflated). The
  page dots count pairs, so they read the same folded and unfolded.
- **Unfolded:** one swipe moves one pair. The dock-side page is next to the dock.

  | Dock | Cover page sits in | View order change |
  | --- | --- | --- |
  | Right | Right pane | Swap within each pair in LTR, none in RTL |
  | Left | Left pane | None in LTR, swap in RTL |
  | Bottom | Right pane | Same as Right |

- **Fold and unfold:** unfolding from cover page N shows the pair whose dock-side screen is N. Folding from either
  pane returns to that pair's dock-side page, never the companion. Both run inside the one rebind a fold already
  does.
- **Editing:**
  - New pages are dock-side (even) screens, and the next id is their companion. Extra empty screens are ordered
    as a pair too.
  - New apps go to cover pages and never fill a companion.
  - On the cover, an empty page is only dropped when its hidden companion has no items and isn't a page the
    user kept; a drag counts the companion's items before treating a page as the last one.
  - Moving a page on the cover moves its companion with it in the database.
- **Default home page** is saved as the pair's first screen (2k), the number "Page pairs" mode already uses.
- **Feed:** it attaches as an overscroll edge effect, not at a page index, so swiping right from the leftmost
  pane opens it as before.
- **Turning the mode off** restores today's layout. Nothing moves: the saved screen order always lists both
  screens of each pair, unswapped, and companions keep their ids and items. Companions that held items just
  show on the cover again as ordinary pages.
- **No database schema change.** Parity of the screen id encodes "companion".

## Design and why

The prompt's four options, in short (full comparison in the Phase 0 report):

| Option | Outcome |
| --- | --- |
| A. Swap the panes only | Cover page 1, 3, ... would unfold into the companion pane, so continuity fails for half the pages. Rejected. |
| **B. Parity companions** | Chosen. Every cover page unfolds to the dock side; no schema change. |
| C. Flagged companions / Today column | Needs a model change. B doesn't block it: `DuoPairing` is the abstraction C would re-implement with flags. |
| D. Sliding window (N-1, N) | Large `PagedView` rework and no companion; not pursued. |

Companions appear only when unfolded because, if they were cover pages, they would unfold into the wrong pane.

### How it is built

New code in `android/lawnchair/src/app/lawnchair/fold/duo/`:

| Class | Role |
| --- | --- |
| `DuoPairing` | The pairing rule, pure Kotlin: dock-side and companion ids, view order, cover screens, saved order, new ids |
| `DuoFoldMapping` | Which page shows across a fold, and the default page conversion, pure Kotlin |
| `DuoContinuity` | Reads the preference and adapts the above for the Java and Kotlin callers |

Preference: `pref_unfoldedLayout` (`pairs` / `continuity`). It rebinds the workspace through
`LauncherModel.rebindCallbacks()` rather than `reloadGrid`, because the grid and database don't change.

The two screens of a pair are swapped in the **view order** (`mScreenOrder`), so every index-based piece of
`PagedView` (snapping, page groups, dots, spring-loaded nudge) is untouched. The saved order is always written
un-swapped, with hidden companions kept next to their dock-side screen.

Hooks in Launcher3 files, each marked `// LawnchairZ: duo continuity.`:

| File | Hook |
| --- | --- |
| `Workspace.java` | `reorderBoundWorkspaceScreens` (arrange pairs), `getPersistableScreenOrder` (un-swap, keep hidden companions), `getCurrentPageScreenIds` (a pair stands for its dock-side screen), `onDuoDeviceProfileChanged` (in-place fold step), `stripEmptyScreens`, `addExtraEmptyScreenOnDrag`, `commitExtraEmptyScreen(s)`, `movePageGroup` / `addDuoCompanionsToSwapMap`, `getDefaultPage` / `setDefaultPage`, `arrangeDuoScreens` after anything that appends screens |
| `ModelCallbacks.kt` | `bindAddScreens` (cover binds dock-side screens only), `getPagesToBindSynchronously`, bind partition (skip companion items, note which have items), `bindItemsAdded` (re-arrange) |
| `model/WorkspaceItemSpaceFinder.java` | New items skip companions; new pages get even ids |
| `LawnchairLauncher.kt` | `dispatchDeviceProfileChanged` calls the Workspace hook, so no second relayout path |

## Known gaps

- **Not tested on a device.** Everything above is unit tested at the pairing and mapping level only; the
  `Workspace` and binding hooks have no automated test.
- **Fold transitions:** on fold, the in-place step moves to the dock-side page. On unfold it hides the cover
  pages until the rebind brings the real pairs, so expect a very short blank workspace instead of mispaired
  pages. Check this on the device.
- **Bottom dock** uses the right pane (a design choice, not derived from hardware).
- **The settings preview** (`LauncherPreviewRenderer`) doesn't show the swapped pair.
- **The hybrid hotseat education flow** still takes its page from `getNewScreenId()` directly.
- **`ModelCallbacks.bindItemsAdded`:** a new screen added at runtime is arranged afterwards, so a pair can
  briefly be in the other order.
- **The grid flip** (4×6 / 5×5) seen on 2026-10-14/15 wasn't investigated; the grid comes from one pair of
  preferences, so it is the same on both screens.
- **Backup and restore:** the preference is an ordinary preference and the database is unchanged, but the
  round trip hasn't been run with the mode on.
- A companion page that already held items before the mode was turned on is not moved; it is simply not shown
  on the cover.

## Device checklist

Use a Fold with some items on pages 0 to 3 (including at least one widget on page 1 and one on page 3). Dock
position **Right** unless a step says otherwise. Mode: **Cover page + companion**.

1. **Turn it on.** Settings → Dock → Grid → Unfolded layout → Cover page + companion.
   *Cover screen shows pages 0, 2, 4 ... only; items from pages 1 and 3 are gone from the cover; the dots
   count the same as before.*
2. **Unfold from cover page 0.**
   *Pair (companion 1 | page 0): page 0 is in the right pane next to the dock; page 1 is on the left. One
   rebind, no restart, no reload.*
3. **Fold again.** *Back on cover page 0.*
4. **Unfold from cover page 1** (the second cover page, which is screen 2).
   *Screen 2 in the right pane, screen 3 on the left.*
5. **Unfold from cover page 3** (the fourth cover page, if it exists). *Its pair, with it on the right.*
6. **Fold from the left pane** (swipe so you're on pair 2 and fold). *Cover shows the dock-side page, not the
   companion.*
7. **Fold from the right pane.** *Same page.*
8. **Watch the fold itself** (record the screen if you can). *No flash of a companion on fold; on unfold a
   brief blank is acceptable, mispaired pages are not.*
9. **Swipe unfolded.** *One swipe moves one pair; the dots count pairs; the dock stays on the right edge.*
10. **Drag across panes.** Move an icon from the right pane to the left pane and back. *Drops where released;
    both survive a fold/unfold.*
11. **Drag a widget onto the companion pane.** *It stays on the companion; it is not on the cover.*
12. **Add a page.** Drag an icon to the empty page at the end (unfolded). *Two new pages appear; after the drop,
    fold: the cover has a new page for the pane next to the dock.*
13. **Remove an empty page.** Remove all items from a pair's companion only, fold and leave edit mode. *The
    page stays while its companion has items; when both are empty the pair goes.*
14. **Install an app** (or use "Add to home"). *It lands on a cover page, never in a companion.*
15. **Reorder pages** in edit mode on the cover. *The page and its companion move together; unfolded, the
    pair order matches.*
16. **Default home page.** Set it on pair 2 (long-press → default) unfolded; fold. *Home goes to the cover
    page of pair 2 on the cover, and to that pair unfolded.*
17. **Spring-loaded and edit mode** (long-press, overview of pages). *Both panes are shown; no crash.*
18. **Turn the mode off.** *Cover shows every page again; no item moved or lost; unfolded shows (0 | 1)
    again with the even page on the left.*
19. **Turn it on again.** *Same layout as step 1.*
20. **Left dock.** Dock position → Left side. *Unfolded, the cover page is in the left pane next to the
    dock.*
21. **Bottom dock.** Dock position → Bottom. *Cover page in the right pane.*
22. **Rotate the inner screen** to portrait and back. *Two panes, the dock on the physical right edge, the
    cover page next to it.*
23. **The feed.** From the leftmost pane swipe right. *The feed opens as before.*
24. **Backup and restore.** With the mode on, make a backup, change the layout, restore. *Mode and layout
    come back; both screens of every pair are intact.*
