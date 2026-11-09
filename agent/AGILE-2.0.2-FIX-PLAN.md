# AGILE 2.0.2 fix plan (P0 visibility)

## Findings
| Ticket | Evidence | Root cause | Fix | Test |
|---|---|---|---|---|
| #62 | renderer=Rive ready=false, DuoRiveView 0x0, slot 342x63, no Duo view ready/injected, stock remains | Attach marked ready on an unmeasured view; no box validation | Bounded `verifyAttachedBox`, Canvas fallback, stock restore | attach box validation |
| Panel handoff | mainCopyReady used isShown; one-frame guard | Guard treated window visibility as ink proof | `hasDrawableBox` drops isShown; 6-frame retry | PanelHandoff/copyDrawable rules |
| Stock hide | mobile_combo/status_bar_dot could remain | Hide ran without real box | Guard hide on box; reapplyHiding check | reapply hiding |
| Rive fallback | no bind leaves empty slot | Fallback not box-validated | ensureCanvasBox + stock restore | fallback box |

## Code changes
- DuoIconHost.kt: added `hasDrawableBox`, used in `copyDrawable` and `mainCopyReady`; attach post-layout validation; onElementReady/onElementFailed box gates; bounded Canvas validation; reveal/collapse/setMainBarVisible guards; reapplyHiding box check.

## Remaining unverified
- #60/#61 need ZIP/tombstone.
- DN2101 Rive asset fallback unverified.
- ROM SDK gating and DND priority deferred to later task.