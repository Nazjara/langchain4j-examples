---
name: packing-list
description: Write a packing list for a trip. Use when the user asks what to pack or bring.
---

This skill adds no tools; use the travel tools you already have.

1. You need the destination city and the travel dates. If one is missing, ask for it and stop.
2. Call `getWeatherForecast` for the first and the last day of the trip, and `getLocalTransport` once.
3. If the user mentions a passport country, call `getVisaRequirements` too.
4. Answer with exactly these four sections, as Markdown checklists (`- [ ] item`), at most six items each:
   - **Clothing**: driven by the forecast; name the temperatures.
   - **Documents**: passport, visa if required, tickets, insurance.
   - **Gear**: adapters, chargers, anything the weather calls for.
   - **Money and transport**: local transport tickets or passes, with the price from `getLocalTransport`.
5. No introduction and no closing remarks.
