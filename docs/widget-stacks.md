# Widget stacks

A widget stack is one home-screen item that holds 2–10 app widgets in the same cells. Vertical
swipes page between them, like the Smart Stack on iOS or widget stacks on One UI. Because the
launcher is the `AppWidgetHost`, every page is a real, live, interactive widget, not a snapshot.

The feature is behind **Settings → ⋮ → Experimental features → Widget stacks (experimental)**,
off by default. Turning it off hides the ways to create stacks; existing stacks keep working.
**Unstack all** on the same screen separates every stack without deleting any widget.

## Using stacks

- **Create**: drag a widget onto the middle of another widget with the same span and hold for a
  moment. The target shrinks slightly; drop to stack. Other ways: the **Create stack** button
  (stack icon) on a widget's resize frame, or **Widget stack** at the top of the widget picker,
  which places an empty 2×2 stack and opens the editor.
- **Add**: drop a widget onto a stack it fits, or use **Add widget** in the editor. The editor
  lists only widgets that can render at the stack's current size and runs the normal bind and
  configure flow, then returns to the editor.
- **Page**: swipe up or down on the stack. Dots appear on the trailing edge while paging.
- **Edit**: long-press the stack → **Edit stack**, or the pencil on its resize frame. Reorder with a
  long-press drag, remove (with undo), move a widget to the home screen, open a widget's settings,
  and toggle Smart Rotate.
- **Resize**: long-press → **Resize** opens the resize frame (lifting the finger under the menu
  doesn't, since the menu holds the drag back). The item is hidden when the stack can't change size
  or the home screen is locked. Dragging a stack and dropping it also opens the frame.
- **Delete**: long-press → **Delete stack** asks first and removes every widget in it. Dragging a
  stack to **Remove** deletes it with the usual undo.
- A stack left with one widget turns back into that widget.
- Stacks are workspace-only: never in the hotseat or a folder.

## Architecture

Nearly all code lives in `android/lawnchair/src/app/lawnchair/widgetstack/`. Launcher3 files get
small hooks marked `// LawnchairZ: widget stacks`, or covered by the file's `LawnchairZ
modification:` notice right above them, so rebases stay manageable.

| File | Role |
| --- | --- |
| `WidgetStackInfo` | Model: an `ItemInfo` holding the children in rank order, the visible rank and Smart Rotate. Deliberately not a `CollectionInfo`, whose contract is a collection of icons. |
| `WidgetStackOptions` / `WidgetStackOrdering` | Packing of the `options` column; pure ordering rules that keep the visible child stable across adds, removals and moves. |
| `WidgetStackLoader` / `WidgetStackLoaderRepair` | Loading stack rows, then attaching children and repairing leftovers after each load. |
| `WidgetStackWriter` | Model writes on top of `ModelWriter` (create, add, remove, reorder, move between stacks, resize, collapse, delete), sharing its threading and undo. |
| `WidgetStackMigration` | Grid-size migration support. |
| `WidgetStackView` | The view: an `NavigableAppWidgetHostView` holding the children's host views. |
| `WidgetStackPhysics` / `WidgetStackSizing` / `WidgetStackResize` | Pure paging math, span-range intersection and presets, and the resize rules used by `AppWidgetResizeFrame`. |
| `WidgetStackTouch` | Exempts touches on a pageable stack from the launcher's vertical swipe controllers. |
| `WidgetStackDropHelper` | Hover-to-stack during workspace drags. |
| `WidgetStackController` | Keeps views and model in step when launcher code acts on a widget that lives in a stack (restore, re-inflate, removal, adds that need configuration). |
| `WidgetStackPopup` / `WidgetStackEditor` / `WidgetStackPickerEntry` / `WidgetStackUnstacker` | Long-press menu, Compose editor sheet, picker entry, "Unstack all". |

### Why the view extends `NavigableAppWidgetHostView`

`ShortcutAndWidgetContainer` measures, scales, pads and lays out anything that is a
`NavigableAppWidgetHostView` exactly like a widget, and the reorder, drag-preview and keyboard
navigation code already handles that type. Only five call sites check for that type or for
`AppWidgetHostView`; the 28 that check `LauncherAppWidgetHostView` (a real widget id) do not
match a stack, which is what we want.

### Children

Children are the real host views from `LauncherWidgetHolder`, added once and never re-parented or
recycled, so updates keep arriving. The visible child and its neighbours stay laid out
(neighbours `INVISIBLE` while settled); the rest are `GONE` but attached.

The workspace binder inflates items off the main thread and then re-attaches widget views to the
host (`attachViewToHostAndGetAttachedView`). Stacks do the same for their children
(`WidgetStackView.attachPagesToHost`); without it, stacked widgets stay on "Loading…" after a
cold start.

`Workspace.mapOverCellLayouts` descends into stacks, so lookups such as
`getWidgetForAppWidgetId`, restore completion and pending-widget updates reach stacked widgets.
`Launcher.removeItem` and `Launcher.bindAppWidget` route stacked widgets to
`WidgetStackController` instead of placing them in cells.

## Database schema change

Schema version **32 → 33**. No columns change.

| Row | `itemType` | `container` | `rank` | `options` | Cells and span |
| --- | --- | --- | --- | --- | --- |
| Stack | `100` (`ITEM_TYPE_WIDGET_STACK`) | `-100` (desktop) | 0 | bit 0: Smart Rotate; bits 8–15: visible rank | The stack's cells |
| Child | `4` / `5` (widget) | stack `_id` | 0…n-1 | the widget's own | span = the stack's |

Item type 100 is far from upstream's sequence (0–11) so a future AOSP type cannot collide. If
upstream ships its own schema 33, the next rebase must renumber ours.

- **Upgrade** 32 → 33 needs no migration.
- **Downgrade** 33 → 32 (`res/raw/downgrade_schema.json`, which the newer build writes for older
  builds to run): each stack's visible child (nearest rank wins if the stored rank is stale) is
  promoted to a plain widget in the stack's cells; the other children and the stack rows are
  deleted. Their widget ids are reclaimed by `removeGhostWidgets` on the next load. Tested against
  real SQLite in `WidgetStackSchemaTest`.
- Without the downgrade script (an older build that never saw it), the loader skips the stack row
  and deletes its children; nothing is corrupted.
- **Load repair** (`WidgetStackLoaderRepair`), after each load: widget rows whose container is not a
  loaded stack are deleted; empty stacks are deleted; a one-child stack becomes that widget;
  ranks are renumbered; the visible rank is clamped; children take the stack's span.
- **Grid-size migration**: both migrators read a stack with its children, use the largest minimum
  span among them, give it a migration id from its widgets, and copy children with the stack.
- **Backup and restore**: the Lawnchair backup is the raw database. On restore, `RestoreDbTask`
  marks every widget row, children included, for id re-allocation, and the loader accepts pending
  widgets inside stacks, so children appear as placeholders until their providers are ready.

## Touch arbitration

| Gesture starting on a stack | Who handles it |
| --- | --- |
| Mostly vertical drag (past touch slop, \|dy\| > \|dx\|) | The stack pages, unless the view under the finger can scroll in that direction (e.g. a list widget), in which case the widget owns the whole gesture. The decision is made once per gesture; there is no mid-gesture handover. |
| Horizontal drag | The workspace pages (or opens the feed). |
| Tap | The child widget. |
| Long-press | A child's long-press is forwarded to the stack, which is picked up and shows Edit / Resize / Delete. Only a still finger counts: once the finger moves past touch slop (paging, or scrolling a widget that disallowed intercepts), or while the stack is settling, the forwarded long-press is cancelled. `WidgetStackView.dispatchTouchEvent` tracks this because it sees every event even after a scrolling child stops `onInterceptTouchEvent` from running. |
| Touch during a paging animation | Catches the page mid-flight. |

The app-drawer swipe (`PortraitStatesTouchController`), notification swipe
(`StatusBarTouchController`) and Lawnchair's custom vertical gestures
(`VerticalSwipeTouchController`) skip touches that start on a stack that can page (two or more
widgets, launcher in the normal state).

Paging physics: `VelocityTracker` plus a `SpringAnimation`. A fling (≥ 500 dp/s) turns one page in
its direction; otherwise the nearest page wins, so dragging past halfway turns the page. One page
per gesture. The ends rubber-band (at most 0.18 page) and never wrap. Moving pages scale down to
94% and fade to 50%. The spring stiffness follows the animator duration scale, and paging jumps
without animation when animations are off. A tick plays when a new page settles.

## Sizing

Every child receives the stack's size options (`WidgetSizes.updateWidgetSizeRanges`) whenever the
stack's span changes, so they all render at the stack's exact size.

The resize frame limits a stack to the span range every child supports: the largest of the
children's minimum spans and the smallest of their maximum spans, per axis, and only the resize
directions every child allows. If the children share no size on an axis, that axis is locked to
the current span. A stack containing a non-resizable widget therefore cannot be resized, unless
the launcher's widget options widen it the same way they widen a single widget
(`WidgetStackSizing.applyResizeOptions`): **Enforce widget resizing** allows both directions, and
**Remove size constraints** allows 1×1 up to the whole grid. Preset mode snaps within that wider
range too.

Two modes, switchable under the experimental toggle (**Preset stack sizes**):

- **Free resize** (default): like any widget, within the shared range.
- **Preset sizes**: snaps to 2×2, 4×2 and 4×4, narrowed on grids with fewer than four columns or
  rows. The frame's normal step-by-step resizing would bounce between presets after a snap jumps
  more than one cell, so preset mode computes the size from the whole drag so far; ties go in
  the direction of the drag.

### Sizing-mode comparison

Both modes are implemented and unit-tested; free resize was exercised end to end on an Android
37.1 emulator (2×2 → 2×3, children re-laid out at the new size and persisted). The emulator uses
software rendering and injected touch events, which is not a fair test of feel, so **the
real-device comparison has not been done yet**. Notes so far:

- Free resize matches how every other widget behaves and never surprises with a jump; its cost is
  that stacks can end up at sizes some children render poorly.
- Preset sizes keep stacks at shapes widgets are designed for, but a one-cell drag can jump two
  cells, and on this 4-column grid the presets leave no 3-wide option.

Initial recommendation: keep **free resize** as the default and offer presets as an option, pending
a check on a real device.

## Smart Rotate

Per stack, off by default. When a child widget updates while the launcher is not visible, the stack
switches to that widget before the user next sees it. It never rotates while the stack is visible
or being touched, and ignores the burst of updates right after binding. Because the switch happens
while hidden, there is no visible animation.

## Accessibility

- TalkBack reads a stack as "Widget stack, *widget name*" with the state "*n* of *m*"; only the
  visible widget is exposed.
- Scroll forward / backward actions labelled **Next widget** and **Previous widget**.
- Launcher actions on a stack: **Move**, **Remove**, **Edit stack**. On a stacked widget: **Move to
  home screen** (instead of drag-moving it out of the stack).
- The editor's list items have **Move up** / **Move down** actions.

## Known limitations

- Dragging a widget out of a stack by hand is not supported; use **Move to home screen** in the
  editor.
- **Add widget** uses a filtered list inside the editor rather than the full widget picker.
- While hovering to create a stack, the shrink cue on the target is mostly hidden under the
  same-size dragged widget.
- The stack is briefly hidden while the Delete confirmation dialog is showing.
- Smart Rotate has no animation (it only switches while hidden).
- The drop-to-stack path only accepts widgets already on the home screen, not widgets dragged in
  from the picker.
- Accessibility resize actions are not offered for stacks.
- Instrumented or screenshot tests for the view are not set up; `WidgetStackView` needs a launcher
  `ActivityContext`, which the existing Robolectric setup does not provide.

## Tests

`android/tests/lawnchairzUnit/app/lawnchair/widgetstack/` (run with
`./gradlew testLawnWithQuickstepLawnchairzDebugUnitTest --tests "app.lawnchair.widgetstack.*"`):

- `WidgetStackInfoTest`: options packing, ordering, visible-child stability, span sync.
- `WidgetStackLoaderRepairTest`: attaching children, orphans, empty and single-child stacks.
- `WidgetStackSchemaTest`: schema version, the downgrade script on real SQLite, grid-migration
  reading.
- `WidgetStackPhysicsTest`: settle targets, fling, halfway rule, rubber band.
- `WidgetStackSizingTest`: range intersection, presets, snapping, drag-based snapping without
  flicker.
- `WidgetStackUnstackerTest`: "Unstack all" placement.

## Device test checklist

Status from the Android 37.1 emulator run (✓ verified, — not yet verified on a device).

| Check | Status |
| --- | --- |
| Turn the experimental switch on | ✓ |
| Create from the widget picker (empty stack, editor opens) | ✓ |
| Create by dropping a widget onto a same-span widget | ✓ |
| Create from the resize frame's Create stack button | ✓ |
| Add a widget from the editor, with and without a configure screen | ✓ |
| Add by dropping a widget onto a stack | ✓ |
| Swipe up/down; halfway rule; rubber band at the ends; no wrap | ✓ |
| Horizontal swipe on a stack pages the workspace / opens the feed | ✓ |
| Swipe up elsewhere still opens the app drawer; swipe on a stack does not | ✓ |
| Taps reach the widget (opens its app) | ✓ |
| Scroll inside a list widget in a stack | — |
| Long-press menu; Edit stack | ✓ |
| Reorder in the editor | ✓ |
| Remove with undo; remove committed after the undo window | ✓ |
| Move to home screen; stack collapses to one widget | ✓ |
| Resize (free mode), children re-laid out and persisted | ✓ |
| Resize (preset mode) | — (stack in the test contained a non-resizable widget) |
| Delete stack with confirmation; widget ids released | ✓ |
| Drag a stack to Remove; undo | — |
| Cold start: order and live widgets restored | ✓ |
| Smart Rotate | — |
| TalkBack announcements and actions | — |
| Backup and restore | — |
| Reboot | — |
| Rotation, foldables, multi-window, dark/light, locale change | — |
| Uninstall or update a stacked widget's app | — |
| 60/120 fps paging on a mid-range device | — |
