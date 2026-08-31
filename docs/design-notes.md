# Design notes

Decisions that shaped Ardoise and are not obvious from the code. The README
covers what the app does and how to set it up; this file covers *why*, and only
where the answer is not already there.

> Replaces an earlier pre-implementation spec. That document described screens
> that were never built (`SettingsScreen`, `OnboardingScreen`), a theme setting
> that does not exist, and a strict three-layer architecture the code does not
> follow. It is in the git history if the reasoning is ever wanted.

## The surfaces

Android's lock screen offers three places to put text, and only two are usable:

| Surface | Verdict |
|---|---|
| Lock screen widget | Capped in size, throttled, and placed where the system decides. Rejected. |
| Ongoing notification | Six to eight lines, action buttons, survives reboot. **The primary surface.** |
| Lock screen wallpaper | A bitmap the app owns outright, so full typographic control — but it replaces whatever the user had. **Off by default.** |

## Layering

Six packages, and the dependency graph is *not* strictly downward — worth
stating plainly rather than claiming an architecture the code does not have:

```
ui ──────────────► domain ──────────────► data
                      │                     │
                      └──────► render ◄─────┘
```

- `domain/TaskRepository` drives `render/SurfaceRenderer`: orchestration has to
  know that something must be redrawn.
- `data/SnapshotStore` serialises `domain/RenderSnapshot`: the cached document
  *is* the domain model, and inventing a separate storage type to avoid the
  import would be ceremony.

Both renderers know nothing of the network or of authentication.
`WallpaperRenderer` is the one exception to knowing nothing of storage: it keeps
a single content key there, to avoid repainting a bitmap that has not changed.

## Data

**No database.** The cache is one JSON document holding at most a hundred short
strings, always read whole. Room would add a compiler plugin, a schema and a
migration story to store fifty lines of text.

**Due dates are calendar days, not instants.** Google Tasks pins `due` to UTC
midnight with no time component, so it is compared as a `LocalDate` against the
user's local today. Comparing instants would mark today's tasks overdue for
every time zone west of UTC.

**Polling, because there is no alternative.** The Google Tasks API offers
neither webhooks nor push. `WorkManager` polls at 15, 30 or 60 minutes. A second
worker fires at local midnight to repaint from cache alone, so "today" and
"overdue" stay true even with no network.

## Notification channel

`IMPORTANCE_DEFAULT` with the sound muted at channel level, not
`IMPORTANCE_LOW`. See the README: `LOW` is filed as "silent" and collapsed to a
bare icon on the lock screen, which removes every line of task text — the whole
feature.

The channel id is versioned (`ardoise_tasks_visible`) because a channel's
importance is immutable once created; the previous id is deleted on first run.

## Privacy

- No access token is ever stored. Google Identity Services returns a fresh one
  per call, so there is nothing to persist and no client secret to hide.
- The DataStore file is **excluded** from cloud backup and device transfer. It
  holds task titles, and the app tells the user their tasks stay on the device.
- The only network destination is `tasks.googleapis.com`.

## Testing

The interesting logic is deliberately kept in plain Kotlin objects that need no
Android runtime: date arithmetic, API mapping, notification wording, the sync
stamp, the midnight delay, the fingerprint format. Localised strings reach them
as a `Wording` value resolved at the call site rather than through a `Context`,
which is what keeps them testable.

What is *not* unit-tested, and why: the authentication path (needs Google Play
services and a real account), the actual `setBitmap` call (needs the system),
and the Compose screen (its logic lives in the ViewModel and the pure renderers).
These were exercised by hand on a physical Pixel 9a.
