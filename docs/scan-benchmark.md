# Scan benchmark: hit-rate and time-to-lock

Repeatable procedure for the open risk in [spec section 10](spec/v1.md#10-open-risks) (ticket #32). The prototype's numbers were lost, so this is the v1 baseline. Re-run it after any change to the vote count, torch, tilt hint, auto-exposure or the analysed region, and compare with the previous run.

## Terms

- **Hit**: the scanner locks on the card's correct **Base Number** and the Catalog returns at least one **Printing** in the session's **Language**, with no manual search or type-in.
- **Miss**: no lock within 15 s, or a lock on the wrong Base Number. Record which.
- **Time-to-lock**: seconds from the card sitting steady in the outline to the confirmation sheet appearing. Time it from a screen recording (the frame where the card is steady to the frame where the sheet appears), or with a stopwatch to the nearest 0.5 s.
- **Hit-rate**: hits divided by cards tried, per group.

Per-add confirmation and adding are not part of the measurement; tap Skip or back out after each lock.

## Sample

Use your own cards, no more than 40 in total (a large benchmark is out of scope):

| Group | Cards |
| --- | --- |
| EN non-foil | 10 |
| EN foil (Alternate Art, secret rare, any glare-prone finish) | 10 |
| JP non-foil | 10 |
| JP foil (as above) | 10 |

Pick cards spread across sets, and keep the same list for later runs. Note the Base Number of each card in the results table.

## Setup

1. Install the debug build: `.\gradlew.bat :app:installDebug` (see [verification.md](agents/verification.md) for devices).
2. Use one device per run. Pixel 10 Pro is the primary target; the moto g play is the low-end check. Record which.
3. Make sure the Catalog has been downloaded in the app.
4. Same room, same lighting for the whole run. Note it (for example "desk lamp, daylight window behind phone"). Cards in penny sleeves or loose: note which, and use the same for every card.
5. Start a Scan Session with the group's **Language**, any Location, and Near Mint. Change Language by starting a new session.

## Procedure per card

1. Clear the frame (no card in view) for about 2 s.
2. Hold the card steady in the on-screen outline at normal scanning distance. Start the timer when it is steady.
3. Do **not** touch torch or tilt unless 5 s pass without a lock. If you do, note `torch` or `tilt` in the results; the first lock still counts as a hit, but report these separately so the tuning effect is visible.
4. Stop the timer when the confirmation sheet shows. Check the Base Number is the right one.
5. Skip the card (nothing is added). If nothing locks after 15 s, record a miss and the reason (glare, blur, no read, wrong number, no Catalog match).

## Results

Fill in one table per run. Copy the section for each re-run.

### Run YYYY-MM-DD

- Device:
- App build (commit):
- Vote count:
- Lighting and sleeves:

| Group | Cards tried | Hits | Hit-rate | Median time-to-lock (s) | Slowest (s) | Hits needing torch/tilt |
| --- | --- | --- | --- | --- | --- | --- |
| EN non-foil | | | | | | |
| EN foil | | | | | | |
| JP non-foil | | | | | | |
| JP foil | | | | | | |

Per card:

| Group | Base Number | Result (hit / miss) | Time-to-lock (s) | Torch / tilt used | Miss reason |
| --- | --- | --- | --- | --- | --- |
| | | | | | |

### Bulk Add (after #36 lands, parent #34)

Same cards, on the moto g play. Snap each card once in Bulk Add, then read the review list.

- **Snap hit-rate**: cards whose row resolved to the correct Printing or a correctly flagged "pick printing" row, divided by cards snapped. Failed or no-match rows are misses.
- **Time-to-read**: seconds from the shutter tap to the row appearing in the review list (screen recording).

| Group | Cards snapped | Resolved correctly | Snap hit-rate | Median time-to-read (s) | Slowest (s) |
| --- | --- | --- | --- | --- | --- |
| EN non-foil | | | | | |
| EN foil | | | | | |
| JP non-foil | | | | | |
| JP foil | | | | | |

## Findings and tuning decisions

Write the conclusions here, then copy anything lasting into [spec section 5](spec/v1.md#5-scan-session-flow) or an ADR.

- Hit-rate and time-to-lock versus expectations:
- Foil glare: does torch or tilt rescue it?
- Vote count (3 today): keep, raise or lower, and why:
- Torch, tilt hint, auto-exposure changes to make:
- Recognition approach still workable? (Only change it if the results show it is unworkable.)
