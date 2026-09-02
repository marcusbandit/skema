# Outstanding feedback

Verbatim intent from the owner, kept here because it outlives any one session.
Tick items off only when they are verified on a device, not when they compile.

## Round three, 2026-09-02, not yet done

### Header
- The top line should say the DTU **semester week**, the thirteen week block
  everyone actually counts in ("Semester week 1", "Semester week 5").
- The line under it should read month, then the real ISO week, then the year:
  "September, week 36, 2026".
- Tapping it still opens the month and year picker. That picker has its own
  problems, which the owner says are tolerable for now.

### Sidebar
- Swiping right anywhere in **Agenda** opens the drawer. Same in **Day**.
- The menu button is too small, and must not sit in the edge furniture.
- The drawer must NOT float over the content. It is part of the side of the
  window: it pushes, it does not overlay.

### Settings
- Far too much text. Run the `unslop` skill over it and cut most of it.
- **Subjects and their colours become their own submenu.** Today they fill the
  whole screen and the owner hates it.

### Back
- The Android back gesture closes the app from Settings. Handle back
  **everywhere**: sheet before the screen under it, screen before the app.

### The detail sheet
- **Remove the fullscreen button and the close button.** Verbatim: "for the
  love of god remove those two buttons ... you want gestures, do not just add
  random buttons, you are adding so much jargon, stop doing that."
- Dragging up a little leaves it stuck part way. It needs snapping zones that
  animate smoothly between rest positions.
- It must never disappear without animating out through the bottom. No jank.

### Standing instruction
- Polish, with **animations and gestures**, never with new buttons.
- Every build handed over bumps `versionCode` and `versionName` first.

## Carried over, still open
- The typical week inside a semester still needs verifying on screen after the
  assignment-window filter and the depth cap.
- Unfolded calendar pane is about 415dp now that the rail takes 176dp. If it
  reads as cramped, the levers are the rail width or the detail pane's share.
- The month and year picker needs a pass.
