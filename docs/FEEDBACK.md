# Outstanding feedback

Verbatim intent from the owner, kept here because it outlives any one session.
Tick items off only when they are verified on a device, not when they compile.

## Round four, 2026-09-02, built and looked at on the emulator

Two School Planner screenshots came with this round: its drawer, and its
calendar with the hamburger, the day disc and the three toolbar icons circled.

### Sidebar
- Make it look like that drawer: the app's name at the top in the accent, a
  rule under it, big glyphs, a soft pill behind the current view, a rule
  between the groups. Done. It still pushes rather than overlays.
- The drawer is 300dp, about two thirds of the folded phone, which is the
  reference's share. The rail keeps its 176dp so the unfolded pane loses nothing.

### Top bar
- The icons come from the circled elements: the hamburger at the left, and
  today, tune and the three dots at the right, all Material's own glyphs.
  Done. The edge handle is gone; the edge drag still works.
- Today stays in the row and dims when today is on screen, instead of vanishing.
- Tune opens a sheet of the calendar's own settings, after School Planner's:
  a layout row of four sketched screens, one per view, then theme and
  weekends. Not the Settings screen. Done. The three dots hold Sync now and
  Semesters, which is still a guess.
- The today marker in the day strip is a true circle, like the circled "21".

### Month
- The month view should be like School Planner's: the grid on top, and under
  it the selected day as a list drawn like a git graph. Overlapping things
  run as lines side by side; overlapping things of the same course are a
  branch, forked off and merged back. Done. Tap a day to list it, tap it
  again to open its week.

### Theme
- The status bar's clock and icons went white on the light app, because Android
  coloured them from the phone's dark mode rather than the app's. They now
  follow the theme the app resolved to. Done, both mismatches checked.

### Time grid
- Show 07:00 to 18:00 by default. Anything on the schema outside that is
  shown, and the grid for that day or week grows to hold it. Done. The week
  grows once the swipe lands, not under a moving finger.
- Not agreed with the owner: the feed's only entries outside those hours
  are zero length "Due" and "Available" stamps, 41 of them, and letting
  those grow the grid would open most spring weeks at midnight. So a stamp
  never grows the grid; one the grid does not reach sits in the strip above
  it, beside the whole day entries. If a stamp should grow the grid after
  all, that is one filter in DayWindow.around.

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
