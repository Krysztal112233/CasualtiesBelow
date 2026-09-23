# physiology

This package models the player's body as it changes over time.

- adrenaline
- blood oxygen
- blood volume
- body temperature
- consciousness
- hygiene
- immune
- pain
- shock

Evolve on the server tick, shaped by wounds, food, weather and treatment.

Untreated, conditions steadily worsen or heal on their own; a few of them can
become fatal.

## Subpackage design

One package per vital sign. The directory _is_ the vital: a vital's simulation
logic, its tick entry and its declared dependencies on other vitals all live
together, so working on a vital means opening exactly one directory.

Each package has a single entry:

- named after the package where a facade exists (such as
  `circulation/Circulation`, `infection/Infection`, etc.)
- otherwise the main module that was already that vital's single-file home
  (`adrenaline/Adrenaline`, `discomfort/Discomfort`, etc.)

The facades exist only where driver logic was absorbed; where the main module
was already a cohesive entry, no forwarding shell was added.

> [!NOTE]
>
> Storage is deliberately not here. Canonical values live in
> `component/VitalsComponentImpl` and are written through
> `component/VitalsMutations`; this tree contains only time-evolution logic.
> That split is why physiology modules are almost all pure and unit-testable.

`progression/InjuryProgression` is the single tick driver. Each vital is
stepped once per pass, in an order that follows the physiology itself: blood
settles before oxygen reads it, oxygen before consciousness, consciousness
before the hypoxia check. The packages decide their own domain outcomes; the
driver only holds the sequence and the sync points.

Cross-vital effects (starvation drains blood, opioids raise pain, hypoxia caps
consciousness) are plain calls between entries, with each dependency documented
in place.

Everything in this tree is `private[casualtiesbelow]` or tighter; the only
public surface is the read-only snapshot traits under `api/body/vitals`.
