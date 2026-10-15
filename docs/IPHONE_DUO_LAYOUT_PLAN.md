# iPhone Duo-style home layout: plan

Status: **Steps 0–3 and 6 implemented, plus the parts of Steps 4, 5 and 7 they need.**
Written 2026-10-14 against `rename/lawnchairz` at `3bee6590ac` (after the fold fixes `8838535f89`
and `3bee6590ac`).

## Progress (2026-10-14)

- **Step 0:** passed. Unfolded: `isTablet:true`, `isTwoPanels:true`, landscape 2448×1848, 5×5 per
  pane (the grid had been changed to 5×5 since the morning's 4×6), 8-icon bottom dock.
- **Steps 1–3:** the `pref_dockPosition` preference (`bottom` / `right` / `left`), a "Dock position"
  picker in Dock settings, `DeviceProfile.isSideDock` / `hasVerticalHotseat()` / `isDockOnLeft()` /
  `isVerticalHotseatTopDown()`, side-dock workspace padding, centered dock column, folder bounds,
  and the Hotseat view.
- **Side dock icon spacing:** a "Space between dock icons" slider (`pref_sideDockSpacing`, 0–100%,
  default 40%), shown under Dock position when a side is chosen. Each icon gets between its own
  size (0%) and one workspace row (100%, the first version's spacing); the column stays centered.
- **Step 6, search button** (replaces the first version's pill under the grid, which cost the
  grid a row of height):
  - `app.lawnchair.qsb.SideDockSearchButton`: a round button at the bottom edge of the dock
    column, below the dock icons, the size of a dock icon. `LawnchairLauncher` adds it to the drag
    layer next to the dock.
  - It shows the icon the dock search bar shows on its left (the provider's icon, themed the same
    way, via `setThemedIconResource`) on the search bar's fill color and transparency. It follows
    changes to the provider and theming.
  - Tap and long-press do what they do on the search bar: `LawnQsbLayout.launchHotseatSearch` and
    `openHotseatSearchOptions`, now shared by both.
  - The grid keeps all its space. The dock column reserves the button's slot at its bottom
    (`getHotseatLayoutPadding`), and `DeviceProfile.getSideDockSearchButtonBounds` places it.
  - `WorkspaceStateTransitionAnimation` fades and moves it with the dock between launcher states.
  - Hidden while the dock is at the bottom, and when the dock search bar is turned off.
  - Checked on the Fold: inner and cover screens. A tap opened the configured provider (Sesame).
- **Pulled forward because Step 3 needs them:** the top-down `CellPosMapper` mode (Step 4), the
  page-indicator position (Step 5), and the Dock position picker (part of Step 7).
- **Fixes found on the device:**
  - `Workspace.setInsets` assumed equal left and right padding, so the next page showed under the
    dock on the cover screen.
  - Vertical-dock ranks past the column now become hidden overflow items instead of landing
    outside the grid. This also fixes the stock landscape dock with two dock rows.
- **Checked on the Fold:**
  - Inner and cover screens both show the right-side dock, 5 icons top to bottom.
  - Over 4 fold transitions: one rebind each; the first unfold after install does the documented
    one-time reload.
  - No crashes.
- **Not done yet:**
  - The rest of the settings work (Step 7: hiding rows and pages, the dock preview).
  - Tests (Step 8).
  - Dock background styling not checked on the device.
  - Drag and drop into the side dock not checked.
  - Rotation not checked.
  - Turning the setting back to Bottom not checked.

## What "iPhone Duo-like" means here

Apple announced the foldable iPhone Duo on 2026-09-09. Its home screen, as reported by Apple and
press coverage:

- The **Dock is vertical, on the right side**, on both the outer and the inner display, and stays
  in the same place whether the phone is open or closed.
- **Open, the inner display shows two Home Screen pages side by side**, with a vertically
  scrolling widget column (Today View) on the left.
- Both displays have the same aspect ratio, so content scales between them.
- The status bar shrinks to a corner and the Dynamic Island runs vertically along the side.

Sources: [Apple Newsroom](https://www.apple.com/newsroom/2026/09/apple-unveils-iphone-duo/),
[MacRumors roundup](https://www.macrumors.com/roundup/iphone-duo/),
[Fast Company](https://www.fastcompany.com/91604701/apples-iphone-duo-has-a-new-intuitive-vertical-interface),
[The Gadgeteer](https://the-gadgeteer.com/2026/09/09/iphone-duo-brings-a-folding-screen-and-side-by-side-apps-to-apples-phone/).

Choices you made (2026-10-14):

| Question | Answer |
| --- | --- |
| Where the side dock appears | **Both screens** (cover and inner) |
| Pages on the inner screen | **Pairs of pages** (0+1, 2+3; a swipe moves by two) |
| Details that matter | Search bar placement, a settings toggle, iPhone-style dock look, the same grid per pane |

Out of scope: the status bar and the Dynamic Island belong to System UI, which a third-party home
app can't restyle.

## Target behavior

**Cover screen (folded, 1248×1972 px, 475 dp wide, portrait):**
- One page of the 4×6 grid.
- A one-column dock on the right edge, icons centered vertically and ordered top to bottom.
- The workspace loses the dock's width (about 90–100 dp) on the right instead of the dock's
  height at the bottom, so the grid gets taller cells and narrower columns.
- A compact search pill at the bottom, centered under the grid (see "Search placement").

**Inner screen (unfolded, 2448×1848 px, landscape-native, about 932×704 dp):**
- Two panes, each using the same 4×6 grid as the cover screen ("same grid per pane"), showing page
  pairs. Launcher3 already does this for `TYPE_MULTI_DISPLAY` devices. The 4_by_6 grid option is
  `phone|multi_display` in `lawnchair/res/xml/device_profiles.xml`.
- The same dock (same icons, same order, same count) on the right edge of the right pane.
- The search pill centered under both panes.
- Inner portrait (rotated, 1848×2346): two panes again, dock still on the physical right edge.

**Folding and unfolding:** the dock stays on the right; only its height and the pane count change.
No reload: one rebind per fold, as the fold fixes now guarantee.

**Today View:** v1 maps it to what LawnchairZ already has to the left of page 0 (the feed or
Smartspacer, see `GOOGLE_DISCOVER.md`). A pinned widget column inside the inner screen is a
separate, larger feature (see "Later").

## How it works today

| Piece | Where | What matters for this plan |
| --- | --- | --- |
| Vertical dock switch | `DeviceProfile.isVerticalBarLayout()` (`android/src/com/android/launcher3/DeviceProfile.java:2149`) | `isLandscape && transposeLayoutWithOrientation`. Transpose is set only for phones (`Builder.build()`, ~line 2614: `!(isTablet || isFixedLandscape)`), so today the side dock exists only on a landscape phone, and never on the inner screen. |
| Left or right | `isSeascape()` (line 2153) | Rotation 270 puts the vertical dock on the **left**. iPhone Duo wants the right edge regardless. |
| Dock size | `updateHotseatSizes()` (~line 897) | Has a vertical-bar branch: icon + edge padding + workspace gap. |
| Workspace padding | `updateWorkspacePadding()` (~line 1734) | Vertical branch reserves `hotseatBarSizePx` on the dock side; the bottom gets only `edgeMarginPx`. |
| Dock padding | `getHotseatLayoutPadding()` (~line 1872) | Vertical branch pads top and bottom with insets. |
| Folder bounds | `getAbsoluteOpenFolderBounds()` (~line 2117) | Vertical branch keeps folders off the dock. |
| Usable width | ~lines 614–622 | Subtracts the dock from width when vertical, from height otherwise. |
| Scalable grid | line 400 | `mIsScalableGrid = inv.isScalable && !isVerticalBarLayout() && …`: a landscape phone turns the scalable grid **off**. |
| Dock view | `Hotseat.setInsets()` (`Hotseat.java:480`) | Vertical: QSB and page indicator hidden, `MATCH_PARENT` height, gravity left/right. |
| Dock pages | `Hotseat.resetLayout()`, `HotseatPagedView.resetPages()` | Vertical: one page, 1 × `numShownHotseatIcons`. Multi-row docks and dock pages are disabled. |
| Fold relayout | `Hotseat.relayoutForDeviceProfile()` (line 354), called from `LawnchairLauncher.dispatchDeviceProfileChanged()` | Already re-lays the dock in place when the orientation or counts change. Items that don't fit go to `mOverflowItems` (hidden, not deleted). |
| Rank ↔ cell | `Hotseat.getCellX/YFromOrder()`, `CellPosMapper` (`Launcher.java:753`, `ActivityContext.java:557`) | The database stores a dock item's **rank** in `screenId`. Vertical maps rank 0 to the **bottom** cell (`numHotseat - rank - 1`). |
| Database dock size | `InvariantDeviceProfile.numDatabaseHotseatIcons`; `LoaderCursor.java:609` | The loader **deletes** dock items whose rank is ≥ `numDatabaseHotseatIcons`. |
| Foldable dock counts | `DeviceProfileOverrides.Options` (`lawnchair/src/app/lawnchair/DeviceProfileOverrides.kt`) | Shown icons on the fold = `min(hotseatColumns, hotseatColumnsUnfolded)`; database icons = `hotseatColumnsUnfolded`, raised to rows × pages × shown. |
| Two panes | `DeviceProperties.isTwoPanels = isTablet && isMultiDisplay`; `Workspace.isTwoPanelEnabled()`; `ModelCallbacks.bindAddScreens()` / `filterTwoPanelScreenIds()` | Page pairs already work. `FOLDABLE_SINGLE_PAGE` is off, so the plain `CellPosMapper` is used. |
| QSB in dock | `HotseatMode` (`lawnchair/src/app/lawnchair/hotseat/HotseatMode.kt`), `isQsbOnTop()` = `isTaskbarPresent \|\| isTwoPanels` | The QSB lives inside `Hotseat` and is hidden when vertical. |
| Dock background | `Hotseat.setUpBackground()` (line 211); prefs `hotseatBG`, `hotseatBGAlpha`, insets, `hotseatBackgroundColor`, `hotseatBackgroundCornerRadius` | A rounded, tinted `InsetDrawable` over the whole dock view. |
| Dock settings | `ui/preferences/destinations/DockPreferences.kt` | Already shows a separate unfolded column count when `TYPE_MULTI_DISPLAY`. |

Device check (2026-10-14, folded): `isPhone:true`, `transposeLayoutWithOrientation:true`,
`isTwoPanels:false`, 4×6, `numShownHotseatIcons:5`, `hotseatBarSizePx:599`. Not yet checked
**unfolded**: that the inner screen reports `isTablet:true`, `isTwoPanels:true` now that the
window proxy remembers both screens. Step 0 confirms it.

## Design options

### Where the "side dock" flag lives

**A. Make `isVerticalBarLayout()` return true when the setting is on.** One line, and the dock,
padding and folder code follow. But ~25 callers read it as "landscape phone": the scalable grid
turns off (line 400), all-apps transitions (`AllAppsTransitionController`), overview sizing
(`LauncherActivityInterface`, `BackgroundAppState`), `SysUiScrim`, `DropTargetBar` (goes
vertical), `StaggeredWorkspaceAnim`, two-button nav, and `BaseActivity`'s rotation handling.
Portrait recents and all-apps would break. **Rejected.**

**B. A separate `DeviceProfile.isSideDock` flag (recommended).** Computed once in the constructor:
setting on, hotseat enabled, not multi-window, no taskbar. Add `hasVerticalHotseat()` =
`isVerticalBarLayout() || isSideDock`, and `isDockOnLeft()` (seascape for the landscape-phone case;
the side setting for side-dock mode). Only the dock-geometry call sites switch to these.
Overview, all-apps and quickstep keep reading `isVerticalBarLayout()`. More call sites to touch,
but each one is local and easy to review.

**C. A new LawnchairZ dock view outside `Hotseat`.** Clean drawing, but it duplicates drag and drop,
folders, predictions, the overflow logic and widget-stack rules. **Rejected.**

### Dock order

- **Top to bottom (recommended):** rank 0 at the top. It reads naturally in portrait and matches the
  bottom dock's left-to-right order, so toggling the setting doesn't scramble the dock. Needs a
  top-down variant in `Hotseat.getCellYFromOrder()`, `CellPosMapper` and
  `PinToTaskbarShortcut.kt:101`. Ranks in the database don't change.
- Bottom to top: reuses today's landscape mapping as is, but the order looks reversed in portrait.

### Search placement

1. **Bottom search pill (recommended).** A compact "Search" pill centered at the bottom of the
   workspace (left of the dock), as on iOS. On the inner screen it is centered across both panes.
   Reuse the existing QSB view (`LawnQsbLayout`, the provider from `hotseatQsbProvider`), moved to
   a strip owned by the drag layer instead of `Hotseat`. The page dots sit just above it.
2. Search at the top of the first page. Users already can place the search widget or Smartspace
   there. No new code, but it's not iPhone-like.
3. A search icon as the last dock cell. Small, but it takes a dock slot and needs a special cell.

### Dock count across screens

iPhone Duo keeps the same dock open or closed. In side-dock mode, use **`hotseatColumns` on both
screens**: `foldableShownHotseatIcons` is already `min(folded, unfolded)`. Never **lower**
`numDatabaseHotseatIcons` (it would make `LoaderCursor` delete dock items). Ranks past the visible
count stay in `mOverflowItems`, hidden, and come back when the setting is turned off.

### Dock look

The existing background prefs give a tinted rounded rectangle, but over the whole `MATCH_PARENT`
view. For the side dock, draw it around the icon column only (wrap the icons' height, center it
vertically, full corner radius), with an iOS-like default: light translucent tint, 28 dp radius,
labels off (`enableLabelInDock` is already off by default). Real frosted glass would need blurring
the wallpaper behind one view, which a launcher window can't do cheaply. `Window.setBackgroundBlurRadius`
blurs the whole window, so v1 uses a tint.

## Implementation steps

Each step builds and can be checked on the Fold on its own.

**Step 0: confirm the inner-screen profile.** Unfold, run the `dumpsys` in "Verification", and check
`isTablet:true`, `isTwoPanels:true` and the panel cell sizes. If `isTwoPanels` is false, fix device
typing first (`LawnchairWindowManagerProxy`, `InvariantDeviceProfile` device type). Everything else
depends on it.

**Step 1: setting.** `lawnchair/src/app/lawnchair/preferences/PreferenceManager.kt`: `dockPosition`
(`bottom` / `right` / `left`, default `bottom`) with the `reloadGrid` listener, like `hotseatRows`.
Optional preset that also sets the dock look defaults. Default off, so upgrades change nothing.

**Step 2: `DeviceProfile` geometry.** `android/src/com/android/launcher3/DeviceProfile.java`:
- add `isSideDock`, `hasVerticalHotseat()`, `isDockOnLeft()`, and print them in `dump()`;
- switch to them in `updateHotseatSizes`, `updateWorkspacePadding`, `getHotseatLayoutPadding`,
  `getAbsoluteOpenFolderBounds`, the usable width/height at ~614–622, `getHorizontalMarginPx`, and the
  responsive-grid branch at ~710;
- keep `mIsScalableGrid` as is (it only depends on `isVerticalBarLayout()`), so the cover screen
  keeps the scalable grid and only its width shrinks;
- two panes: take the dock's width off the outer edge only, keep both panes' cells equal width, and
  make the horizontal-dock span code (`recalculateHotseatWidthAndBorderSpace`, `mHotseatColumnSpan`)
  a no-op when the dock is vertical.

**Step 3: dock view.** `android/src/com/android/launcher3/Hotseat.java` (and `HotseatPagedView`):
`setInsets`, `resetLayout`, `relayoutForDeviceProfile`, `gridDiffersFromDeviceProfile`,
`getCellX/YFromOrder`, `getPageFromOrder` and the page indicator read `hasVerticalHotseat()` /
`isDockOnLeft()`; top-down order in side-dock mode; side-dock background (`setUpBackground`).

**Step 4: rank mapping.** `CellPosMapper` gets a top-down flag; `Launcher.java:753`,
`ActivityContext.java:557`, `LauncherPreviewRenderer` (dock preview in settings),
`PinToTaskbarShortcut.kt` and `StaggeredWorkspaceAnim` (dock stagger) pass the new flags. Check that
`Launcher.initDeviceProfile` rebuilds the mapper after a fold. Add cases to
`tests/multivalentTests/.../celllayout/CellPosMapperTest.java`.

**Step 5: workspace and page dots.** `Workspace.java:416` uses the vertical branch for the page
indicator in side-dock mode and moves it above the search pill.

**Step 6: search pill.** New LawnchairZ view (Apache-2.0 header, fork style) in
`lawnchair/src/app/lawnchair/qsb/`, placed by `LawnchairLauncher` in the drag layer, sized from
`DeviceProfile` (bottom reserve added to `workspacePadding.bottom`). Hidden when the dock is at the
bottom. The settings choice is pill / none.

**Step 7: settings UI.** `ui/preferences/destinations/DockPreferences.kt`: "Dock position"
(Bottom / Right / Left). When it's a side: hide rows, pages and the unfolded column count (with a
note that extra icons are kept), swap "search bar in dock" for "search pill". Disable the side
options with an explanation when the taskbar on phones is on. New strings in
`lawnchair/res/values/strings.xml`.

**Step 8: dumps and tests.** Update `DeviceProfileDumpTest` assets for the new dump lines; unit
tests for the rank mapping and the "never lower `numDatabaseHotseatIcons`" rule in
`DeviceProfileOverrides`.

## Risks

- **Fold and unfold transitions.** The dock stays vertical on both screens, so only its count and
  height change; `relayoutForDeviceProfile()` already handles that in place. The risk is
  `setInsets` not running again before the next layout pass, leaving the old width for a frame.
  Watch for one rebind per fold (no `LoaderTask`, no `onIdpChanged`), as after `3bee6590ac`.
- **Grid and dock counts in the database.** Never change `numDatabaseHotseatIcons`, the database
  file name (`launcher_<rows>_<cols>_<hotseat>.db` from `hotseatColumns`) or the grid. Otherwise
  toggling the setting migrates or deletes dock items. With rows × pages > 1, extra ranks hide via
  `mOverflowItems` and return when the setting is off.
- **Widget stacks.** The cover screen's columns get about 20% narrower, so widgets (and stacks)
  receive new size options once when the setting changes. Not on every fold: both screens' sizes
  are already known. Stacks can't be in the dock, so that rule doesn't change. Check that a stack's
  trailing page dots and resize frame don't sit under the dock on the right pane.
- **Drag and drop.** Dropping onto a vertical dock, reordering inside it, folders created in it,
  and fling-to-delete (`FlingToDeleteHelper` has a vertical branch; keep it on `isVerticalBarLayout`).
- **Taskbar.** With the taskbar on phones enabled, the inner screen uses taskbar/dock alignment
  animations that assume a bottom dock. v1 turns the side dock off whenever `isTaskbarPresent`.
- **Gestures.** Swipe up for all apps, and the edge-back gesture next to the right dock. Icons need
  enough padding from the edge so back swipes don't launch dock apps.
- **Three-button navigation** puts the nav bar at the bottom in portrait and on a side in
  landscape. The dock must stay inside the insets (`mInsets.right` in landscape).
- **RTL.** "Right" stays the physical right edge. "Left" exists for anyone who wants it.
- **Upgrades and downgrades.** New preference, default off; no schema change (the database stays at
  33). An older build ignores the preference. Backups carry the preference.
- **Upstream rebases.** Hooks in `DeviceProfile`/`Hotseat` get `// LawnchairZ: side dock` markers,
  as widget stacks did.

## Verification on the Fold

Device `RFGL829RE0P` (SM-F971U1, One UI, SDK 37). Always pass `-s`; an emulator is often attached.

```bash
ANDROID_SERIAL=RFGL829RE0P ./gradlew :installLawnWithQuickstepExpressiveDebug
```

```bash
adb -s RFGL829RE0P shell dumpsys activity dev.launcher.lawnchairz.l3.debug/app.lawnchair.LawnchairLauncher | grep -E "isTwoPanels|isTablet|isSideDock|hotseatBarSizePx|numShownHotseatIcons|workspacePadding|cellWidthPx"
```

```bash
adb -s RFGL829RE0P exec-out screencap -p > side-dock-cover.png
```

Checklist:
1. Folded and unfolded, setting off: dump and screenshots match today (no regression).
2. Turn on "Dock position: Right". Cover: one-column dock on the right, top-to-bottom order matching
   the old left-to-right order, search pill at the bottom, folders open clear of the dock.
3. Unfold: two panes of 4×6, page pairs, same dock on the right edge, pill centered under both panes.
   Rotate the inner screen to portrait and back; rotate the cover to landscape.
4. 20 folds and unfolds with `logcat -b main,system,events`: one `startBinding` per fold, no
   `LoaderTask`, no `onIdpChanged`, no `updateSizeRanges changed=true`.
5. Drag an app into the dock, reorder, make a folder in it, remove one; `am force-stop` the
   launcher and confirm the order persists.
6. Set dock rows to 2 with a full dock, turn the side dock on, then off: no dock item lost.
7. A widget stack on page 0 and on the right pane: page through it, resize it, fold and unfold.
8. Gesture nav and three-button nav; back gesture from the right edge doesn't launch a dock app.
9. Taskbar on phones enabled: side options disabled with the explanation.
10. Unit tests: `CellPosMapperTest`, `DeviceProfileOverrides` rule, `DeviceProfileDumpTest`.

## Later (not in v1)

- A pinned **Today View** widget column on the inner screen's left (a new workspace container
  sized like one pane column, possibly holding widget stacks).
- Two dock columns, or dock pages, in side mode.
- The search pill as a page-dots replacement (dots inside the pill, as on iOS 17+).
