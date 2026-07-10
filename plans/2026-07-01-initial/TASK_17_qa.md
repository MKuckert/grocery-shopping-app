# Task 17: End-to-End Integration & Manual QA Checklist

> **Depends on:** All previous tasks
> **Status:** [ ]

---

## Description

Wire everything together and verify functional correctness across the full user journey.

### Core QA scenarios (1–12)

1. **Auth flow:** Sign up → JWT contains `household_id` in `app_metadata` → PowerSync connects → Inventory loads.
2. **Inventory:** Items appear grouped with sticky headers; swipe decrements stock; FAB opens scanner.
3. **Scanner (inventory):** Known barcode → beep + decrement + Snackbar. Unknown barcode → boop → Open Food Facts lookup → `CaptureRequired` overlay. Soft-deleted barcode → resurrect + Snackbar "Restored".
4. **Start Shopping:** button updates `households.current_state = 'SHOPPING'` → all devices switch to ShoppingScreen.
5. **Shopping sections:** Active / Struck-through / Impulse Buys reflect DB state in real time. Full-fulfillment tap moves item to struck-through immediately.
6. **Scanner (shopping):** Unknown barcode → spawns Unsorted impulse buy.
7. **SearchBar:** Expands, filters `product_kinds` live; force-add works.
8. **Finish Shopping:** → `UNLOADING` state.
9. **Unloading:** Rows show formula; checkbox locks/unlocks steppers; submit with unchecked items shows dialog; confirm submits transaction → `IDLE`.
10. **Detail Screen:** Edits persist; reachable from all three main screens; back works.
11. **Offline:** Writes queue locally; sync resumes on reconnect.
12. **Multi-device:** State change on one device updates all others via PowerSync replication.

### Offline-first scenarios (13–16)

13. **Airplane mode during shopping:** Turn off network → make stepper adjustments → verify writes queue in PowerSync → re-enable network → verify sync uploads and remote DB matches.
14. **Open Food Facts 503 during scan:** Use a proxy or mock to return 503 → verify single retry fires → verify fallback to `"Unknown Item"` + `CaptureRequired` overlay shown.
15. **Long offline period:** App stays on ShoppingScreen offline for 5+ minutes → reconnects → verify household state sync resolves correctly without duplicate writes.
16. **Multi-device state sync:** Device A taps "Start Shopping" → verify Device B automatically navigates to ShoppingScreen within sync latency window (typically < 5 s on LTE).

---

## Review Criteria

- All 16 QA scenarios pass.
- No uncaught exceptions in logcat.
- `quantity_to_buy` is always `MAX(0, minimum_stock - current_stock)` after any stock change.
- PowerSync upload queue drains to 0 after reconnect in offline scenario.
