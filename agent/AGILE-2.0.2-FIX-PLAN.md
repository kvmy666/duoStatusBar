# AGILE 2.0.2 fix plan (P0 visibility)

## Findings
| Ticket | Evidence | Root cause | Fix | Test |
|---|---|---|---|---|
| #62 | renderer=Rive ready=false, DuoRiveView 0x0, slot 342x63, no Duo view ready/injected, stock remains | Attach marked ready on an unmeasured view; no box validation | Bounded `verifyAttachedBox`, Canvas fallback, stock restore | attach box validation |
| Panel handoff | mainCopyReady used isShown; one-frame guard | Guard treated window visibility as ink proof | `hasDrawableBox` drops isShown; 6-frame retry | PanelHandoff/copyDrawable rules |
| Stock hide | mobile_combo/status_bar_dot could remain | Hide ran without real box | Guard hide on box; reapplyHiding check | reapply hiding |
| Rive fallback | no bind leaves empty slot | Fallback not box-validated | ensureCanvasBox + stock restore | fallback box |

## Code changes
- DuoIconHost.kt: readiness gates use `hasBox` (attached + visible + laid out); ink/handoff ordering uses `hasDrawableBox` (box + alpha > EPSILON). `hasBox` drives attach post-layout validation, onElementReady/onElementFailed gates, bounded Canvas validation, reveal/collapse/setMainBarVisible guards, and reapplyHiding; `hasDrawableBox` drives `copyDrawable` and `mainCopyReady` so alpha 0 is no longer treated as "no view".

## P1 (this change)
- SDK gating: OnePlus measured only on SDK 36, Xiaomi on SDK 37; data profiles rejected when apiMin/apiMax exclude the running SDK.
- Classic Canvas: classic design resolves to `duo_old` and scales 0.9286; fallback keeps the gap track.
- DND normal seat: with `middleSlot=percent` and DND active, the middle stays in its normal seat (middleY=0, scale=1); inversion applies otherwise.

## Verification status
- Unit tests: 480 green (`:app:testDebugUnitTest`).
- Build: `:app:assembleDebug` succeeds.
- No device test was run for this release.

## Remaining unverified
- #60/#61 need ZIP/tombstone artifacts. Nothing here is proven on a device.
- DN2101 Rive asset fallback unverified.