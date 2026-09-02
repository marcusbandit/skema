# Native Android front end

Decided 2026-09-02. The front end becomes a native Android app: Jetpack
Compose with Material 3, in `androidApp`. The desktop target and the shared
Compose Multiplatform UI go. iOS is not a target for now. The shared module
keeps what is not UI: the ICS parser, merging, terms, the repository, settings
and the session.

The drawing code is already Jetpack Compose code, since Compose Multiplatform
in `commonMain` compiles to it on Android. So the grids, the lanes, the day
graph, the palette and the squircle move over unchanged. What gets rebuilt is
the chrome, from stock Material 3 parts.

## Phase 1, structure

- Drop `:desktopApp` and its plugin use.
- `shared` loses the Compose plugins and dependencies and its `ui` package,
  in all three source sets, plus `shared/App.kt`. It keeps `jvm()` so the
  parser tests keep running fast.
- `androidApp` moves to the Compose BOM: `ui`, `foundation`, `material3`,
  `material3-window-size-class`, `activity-compose`,
  `lifecycle-viewmodel-compose`, plus the Lucide and Font Awesome packs.
- Every file from `shared/.../ui` moves to `androidApp/src/main/kotlin/.../ui`.
  The expect/actual pairs collapse to their Android side: `BackHandler` is
  `androidx.activity.compose.BackHandler`, `SystemBars` is the Android actual,
  `Feel` is the Android actual.
- The UI tests move to `androidApp/src/test`.
- Exit: the app builds, installs and looks exactly like 0.7.0.

## Phase 2, native chrome

- Theme: `MaterialTheme` with dynamic colour on Android 12+ and a DTU red
  scheme underneath, the shape tokens set to `SquircleShape` so every Material
  part wears the house corners, three type sizes on the system font. The
  `skema` colour facade maps onto `colorScheme` so the views keep compiling.
- Shell: `Scaffold` with a `TopAppBar`, menu as the navigation icon, today,
  tune and an overflow `DropdownMenu` as actions.
- Sidebar: `ModalNavigationDrawer` on a compact width, `NavigationRail` or a
  `PermanentNavigationDrawer` on an expanded one, `NavigationDrawerItem` rows.
- Sheets: `ModalBottomSheet` for the calendar settings and the month picker.
- The detail pane under the grid stays; it becomes a Material surface.
- Settings and Subjects: `ListItem`, `Switch`, `SingleChoiceSegmentedButtonRow`.
- Predictive back enabled in the manifest.
- Exit: every screen on the emulator, folded and unfolded, light and dark.

## Phase 3, tidy

- README, session notes and FEEDBACK say what the app is now.
- Version 0.8.0. No release without asking.
