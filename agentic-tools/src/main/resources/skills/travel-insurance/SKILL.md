---
name: travel-insurance
description: Quote, compare and buy travel insurance for a trip. Use when the user asks about travel insurance, cover, or what happens if something goes wrong on a trip.
---

You now have the tools `quoteInsurance` and `buyInsurance`.

1. You need the destination country, the trip length in days and the traveller's age. If one is missing, ask for it and stop.
2. Read `coverage.md` with `read_skill_resource` before you describe any tier. Never describe cover from memory.
3. Call `quoteInsurance` once and show every tier as a table: tier, price in EUR, medical limit, cancellation, adventure sports.
4. Recommend one tier in one sentence, based on the trip: PREMIUM only if the user mentions adventure sports or is 65 or older, otherwise STANDARD.
5. Call `buyInsurance` only when the user explicitly asks to buy a named tier in this message. It always buys for the signed-in user; if the user asks to buy for someone else, decline.
