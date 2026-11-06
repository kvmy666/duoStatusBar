# Duo Status Bar — Agile 1.7 Test Plan

## Document control

| Field | Value |
|---|---|
| Phase | Agile 1.7 |
| Date | 2026-11-03 |
| Status | Draft; mirrors `AGILE-1.7-REQUIREMENTS.md` |
| Baseline | 1.6.0 code 76 on CPH2747; fatal paths fixed |
| Pass rule | PASS only with exact evidence; otherwise `unverified`/FAIL |

## 1. Gates

| Gate | Evidence |
|---|---|
| Requirements audit | Every requirement has a test here and a phase in `AGILE-1.7-PLAN.md` |
| Compile/unit | `:app:testDebugUnitTest :app:assembleDebug --offline` |
| Release | `:app:assembleRelease -x lintVitalRelease -x lintVitalAnalyzeRelease --offline` |
| Rive | `rive . --verify`, `inspect --summary`, ease/hash gates |
| Transition | 6 directions × 20; 60 fps recording; logcat |
| Fatal | 1.6 P0 suite remains green |
| All-UI | CPH2747 live; others `unverified` |

## 2. Cases

### BAT

| ID | Steps | Pass |
|---|---|---|
| T-BAT-01 | Read stock battery view hierarchy/lifecycle on CPH2747; pull logs during heads-up/QS/shade; record parent/visibility/geometry | Host and transition source documented |
| T-BAT-02 | Record 20 transitions in each direction (HUD↔QS, QS↔shade, shade→HUD) at 60 fps; inspect every frame for jump/blank/size flash | No jump >2 px, no blank, no size flash |
| T-BAT-03 | Toggle camera ring off, repeat transitions; check Duo remains with stock battery | Present on all frames where stock is present |
| T-BAT-04 | Run 1.6 P0 tests: QS rotation, shade stress, camera/lock, 100% charging | STB-01…08 pass, no fatal |

### RING2

| ID | Steps | Pass |
|---|---|---|
| T-RING2-01 | Home: switch Default/2SIM/Camera/Custom; kill/relaunch | All render, persist, no deleted art |
| T-RING2-02 | Set battery to 100/80/49/19 (or headless visuals) in new design | Battery bar follows level, not static 100 |
| T-RING2-03 | Trigger PHDP test/real download in old and 2SIM designs | Progress uses selected geometry |
| T-RING2-04 | Headless render new ring; inspect curvature | Continuous circular battery line, no straight segment |
| T-RING2-05 | Read cell logs at 0…4 bars; compare stock icon to dots; repeat on second SIM and second ROM if available | Exact mapping; no full→1 dot |
| T-RING2-06 | Camera toggle with current Default/2SIM/Custom; open QS/shade/keyguard | Current design scaled into camera cutout; Wi-Fi outside visible; normal on other surfaces |
| T-RING2-07 | Home simulation 4 full / 4+2 / custom; check preview | Preview matches, no module write |
| T-RING2-08 | 100% plugged, 99%, unplugged, saver, charging | Color/state mapping correct |

### HOME2 / size / position

| ID | Steps | Pass |
|---|---|---|
| T-HOME2-01 | Home four states; change each; live preview | Updates within 100 ms, persists |
| T-HOME2-02 | Set size 0/100; measure rendered ring; compare to physical 50/220; check old/new optical equality at the same logical size | Mapping matches `50+1.7×logical`; no zero pixels |
| T-HOME2-03 | Drag position 0→max→0 ten times; release mid-range; re-grab | No disappearance after 20 dp, no reset, cursor follows |
| T-HOME2-04 | Move centre icons to top; number to centre; shrink icons; live preview | Positions/scale correct |
| T-HOME2-05 | Preview charge slider, saver, 8-dot custom | All update, no continuous idle loop |
| T-HOME2-06 | Four event previews | Animated, no static placeholder |

### NAV2 / icons

| ID | Steps | Pass |
|---|---|---|
| T-NAV2-01 | Check "Custom" label and fourth Settings tab; screenshots | Short label, four tabs, Settings one tap, no duplicate customization |
| T-ICON2-01 | Inspect APK assets and screenshots against supplied icons | Supplied assets used, no redraw |
| T-ICON2-02 | Toggle each dynamic per-slot switch; All on/off | Only target icon changes; All toggles all; persists |
| T-ICON2-03 | Move Wi-Fi out; hide stock Wi-Fi | Duo Wi-Fi remains visible |
| T-ICON2-04 | Hide battery; check Duo battery and anchor | Duo battery remains, anchor survives, no black slot |
| T-ICON2-05 | Missing ROMs | Marked unverified |

### GLASS2

| ID | Steps | Pass |
|---|---|---|
| T-GLASS2-01 | Screenshot ColorDuo and Duo nav; sample alpha/blur/rim/reflection; compare | Values documented, close visual match |
| T-GLASS2-02 | Record press/drag/settle in both; compare | Same start/follow/settle/haptics, no label blur |
| T-GLASS2-03 | Inspect active blue/no wine | Pass |
| T-GLASS2-04 | Document ColorDuo REA evidence: package/version/classes | Evidence file complete |

### UX2 / QA2

| ID | Steps | Pass |
|---|---|---|
| T-UX2-01 | Report page screenshot; check title below blur | No overlap/clipping |
| T-UX2-02 | Live preview interactions | See HOME2 |
| T-UX2-03 | Event previews | Four animated |
| T-QA2-01 | All-UI matrix | CPH2747 measured; others unverified |
| T-QA2-02 | Fatal/transition matrix | No fatal, no safe mode |
| T-QA2-03 | Requirements audit | 1:1 traceability |
| T-QA2-04 | Log/report privacy | No PII; assets licensed by owner |

## 3. Evidence layout

- `docs/evidence/agile-1.7/transitions/` — videos/frames.
- `docs/evidence/agile-1.7/ring/` — headless/device.
- `docs/evidence/agile-1.7/home/` — four states.
- `docs/evidence/agile-1.7/icons/` — per-icon toggles.
- `docs/evidence/agile-1.7/glass/` — ColorDuo vs Duo.
- `docs/evidence/agile-1.7/report/`.
- Logs and dumps alongside.

## 4. Traceability

| Requirement | Cases |
|---|---|
| BAT-01…05 | T-BAT-01…04 |
| RING2-01…08 | T-RING2-01…08 |
| HOME2-01…06 | T-HOME2-01…06 |
| NAV2-01/02 | T-NAV2-01 |
| ICON2-01…04 | T-ICON2-01…05 |
| GLASS2-01…04 | T-GLASS2-01…04 |
| UX2-01…03 | T-UX2-01…03 |
| QA2-01…04 | T-QA2-01…04 |

## 5. Exit

- Requirements answers recorded.
- All P0/P1 tests pass on CPH2747; other UIs truthful.
- No fatal in transition/camera/rotation.
- Owner approves visual match and Home logic.
