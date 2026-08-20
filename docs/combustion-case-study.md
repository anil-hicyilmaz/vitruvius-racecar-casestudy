# Combustion Race Car — Case Study Documentation

Vitruvius V-SUM case study for the **internal combustion** branch of the F1 Race Car project.
Sister case study: `electricalracecar` (separate branch, same generic `racecar` metamodel).

This document doubles as onboarding material and as presentation notes.

---

## 1. What this project is

The project keeps **two separate models consistent** with each other, without merging them:

- `racecar` — the **generic** race car model. Vehicle, chassis, axles, wheels, a propulsion slot. Propulsion-agnostic.
- `combustionracecar` — the **combustion-specific** model. Engine, fuel tank, exhaust.

When somebody edits the generic model, Vitruvius automatically repairs the combustion model so the
two stay in sync. The two metamodels never reference each other; the link between them exists only
at runtime, in the **correspondence model**.

The construct that holds both models together is a **V-SUM** (Virtual Single Underlying Model):
several models that behave as if they were one, while staying separate on disk.

### The four modules

| Module | Contains | This case study's files |
|---|---|---|
| `model/` | Metamodels (`.ecore`) + code generation config (`.genmodel`) | `combustionracecar.ecore`, `combustionracecar.genmodel` |
| `consistency/` | Consistency rules in the Reactions DSL | `racecar2combustion.reactions` |
| `viewtype/` | View type definitions | (default identity view type is used) |
| `vsum/` | V-SUM setup + tests | `CombustionRaceCarVsumTest.java` |

---

## 2. Core concepts in one line each

| Term | Meaning |
|---|---|
| **EMF** | Framework that generates Java classes from a metamodel description. |
| **Ecore** | The language metamodels are written in (`.ecore`). |
| **genmodel** | Code generation settings for one `.ecore`. Must be reloaded after every ecore change. |
| **V-SUM** | Several separate models kept consistent, used as if they were one. |
| **View** | A filtered window on the V-SUM. The only legal way to read or write. |
| **Reaction** | *When* to repair: a change trigger. |
| **Routine** | *How* to repair: `match` → `create` → `update`. |
| **Correspondence** | A stored pair: "this object here is the same thing as that object there." |
| **Containment** | A reference that *owns* its target. Every object needs exactly one owner. |

---

## 3. The combustion metamodel

`model/src/main/ecore/combustionracecar.ecore`
nsURI: `https://kit.edu/vitruvius/case-study/combustionracecar/1.0`

```
CombustionRaceCar
  ├─ vehicleId : EString
  ├─ name      : EString
  └─ powertrain ◆──► CombustionPowertrain          [1..1]
                       ├─ powertrainId   : EString
                       ├─ mass           : EDouble
                       ├─ maxPower       : EDouble
                       ├─ mountInterface : EString
                       ├─ engine        ◆──► CombustionEngine   [1..1]
                       │                       ├─ maxPower      : EDouble
                       │                       ├─ mass          : EDouble
                       │                       ├─ displacement  : EDouble
                       │                       └─ cylinderCount : EInt
                       ├─ fuelTank      ◆──► FuelTank           [1..1]
                       │                       ├─ capacity : EDouble
                       │                       └─ mass     : EDouble
                       └─ exhaustSystem ◆──► ExhaustSystem      [1..1]
                                               ├─ mass         : EDouble
                                               └─ backPressure : EDouble
```

`◆` = containment. All four references are containment, so the whole model is one ownership tree
rooted at `CombustionRaceCar`.

`[1..1]` = lower bound 1, upper bound 1 — exactly one, mandatory. Upper bound also decides the
generated Java API: `setPowertrain(x)` for `[1..1]`, `getPowertrains().add(x)` for `[0..*]`.

**Deliberately absent:** there is no reference from `combustionracecar.ecore` to `racecar.ecore`.
That is the point of Vitruvius — the metamodels stay independent and can evolve separately. The
connection lives in the correspondence model at runtime.

### The generic side we react to

Relevant part of `racecar.ecore` (owned jointly with the electric case study):

```
RaceCar
  ├─ vehicleId      : EString  [1..1]  iD="true"
  ├─ name           : EString  [1..1]
  ├─ ratedPower     : EDouble  [1..1]
  ├─ totalMass      : EDouble
  ├─ propulsionKind : PropulsionKind [1..1]   -- UNSPECIFIED | COMBUSTION | ELECTRIC
  └─ powertrainSlot ◆──► PowertrainSlot [1..1]
                           ├─ requiredType   : PropulsionKind
                           └─ mountInterface : EString
```

---

## 4. How the two models are linked

```
    racecar model                                    combustionracecar model
    example.racecar                                  example.combustionracecar
  ┌──────────────────────┐                        ┌───────────────────────────┐
  │ RaceCar              │  ◄── correspondence ──►│ CombustionRaceCar         │
  │  ├─ vehicleId        │                        │  ├─ vehicleId             │
  │  ├─ name             │                        │  ├─ name                  │
  │  ├─ ratedPower       │                        │  └─ powertrain            │
  │  └─ powertrainSlot   │  ◄── correspondence ──►│      CombustionPowertrain │
  │       ├─ requiredType│                        │       ├─ engine ◄── corr. │
  │       └─ mountIface  │                        │       ├─ fuelTank         │
  └──────────────────────┘                        │       └─ exhaustSystem    │
                                                  └───────────────────────────┘
                    ▲                                          ▲
                    │            REACTIONS                     │
                    └──── racecar2combustion.reactions ────────┘
                              (one-directional: racecar ──► combustion)
```

Correspondences are written with `addCorrespondenceBetween(a, b)` and read back with
`retrieve <Type> corresponding to <element>`. Because retrieval is **typed**, one source element
can carry several correspondences: `PowertrainSlot` corresponds to both a `CombustionPowertrain`
and a `CombustionEngine`.

---

## 5. The consistency rules

File: `consistency/src/main/reactions/kit/sdq/kastel/vitruvius/casestudy/consistency/racecar2combustion.reactions`

Header — declares direction and imports both metamodels by nsURI:

```java
reactions: racecar2combustion
in reaction to changes in racecar
execute actions in combustion
```

This generates `mir.reactions.racecar2combustion.Racecar2combustionChangePropagationSpecification`,
which the test registers on the V-SUM.

| # | Reaction | Trigger | Effect |
|---|---|---|---|
| 1 | `CreateCombustionRaceCar` | `RaceCar inserted as root`, guarded by `propulsionKind == COMBUSTION` | Creates `CombustionRaceCar`, copies `vehicleId` + `name`, persists it, registers correspondence |
| 2 | `PowertrainSlotSetOnRaceCar` | `PowertrainSlot replaced at RaceCar[powertrainSlot]`, guarded by `newValue !== null` | Creates `CombustionPowertrain` **plus** its three mandatory children, copies `mountInterface` and `ratedPower` |
| 3 | `RaceCarNameChanged` | `attribute replaced at RaceCar[name]` | Updates `CombustionRaceCar.name` |
| 4 | `RaceCarRatedPowerChanged` | `attribute replaced at RaceCar[ratedPower]` | Updates `CombustionEngine.maxPower` |
| 5 | `RaceCarVehicleIdChanged` | `attribute replaced at RaceCar[vehicleId]` | Written, but never fires — see §7 |
| 6 | `PowertrainSlotDeleted` | `PowertrainSlot deleted` | Removes `CombustionPowertrain` **and** its correspondence |

### Rule 1 explained line by line

```java
reaction CreateCombustionRaceCar {
    after element racecar::RaceCar inserted as root
    with newValue.propulsionKind == PropulsionKind.COMBUSTION
    call createCombustionRaceCar(newValue)
}

routine createCombustionRaceCar(racecar::RaceCar raceCar) {
    match {
        require absence of combustion::CombustionRaceCar corresponding to raceCar
    }
    create {
        val combustionRaceCar = new combustion::CombustionRaceCar
    }
    update {
        combustionRaceCar.vehicleId = raceCar.vehicleId
        combustionRaceCar.name = raceCar.name
        persistProjectRelative(
            raceCar,
            combustionRaceCar,
            "models/combustion/" + raceCar.vehicleId + ".combustionracecar"
        )
        addCorrespondenceBetween(combustionRaceCar, raceCar)
    }
}
```

| Line | Purpose |
|---|---|
| `inserted as root` | Fires when a `RaceCar` becomes a top-level object. |
| `with ... == COMBUSTION` | Guard. Electric cars are skipped — the electric case study handles those. |
| `require absence of` | Idempotence: don't create a second counterpart if one already exists. |
| `create { ... }` | New, still unattached object. |
| `persistProjectRelative(...)` | The **persistence rule** — gives the new root object a file. Without it the object exists in memory but never appears in a view. Detailed below. |
| `addCorrespondenceBetween(...)` | Records the link every later rule depends on. |

### Rule 2 — why it creates four objects

```java
create {
    val cPowertrain = new combustion::CombustionPowertrain
    val cEngine     = new combustion::CombustionEngine
    val cFuelTank   = new combustion::FuelTank
    val cExhaust    = new combustion::ExhaustSystem
}
update {
    cPowertrain.mountInterface = slot.mountInterface
    cPowertrain.maxPower = raceCar.ratedPower
    cEngine.maxPower = raceCar.ratedPower

    cPowertrain.engine = cEngine
    cPowertrain.fuelTank = cFuelTank
    cPowertrain.exhaustSystem = cExhaust
    cCar.powertrain = cPowertrain

    addCorrespondenceBetween(cPowertrain, slot)
    addCorrespondenceBetween(cEngine, slot)
}
```

`engine`, `fuelTank` and `exhaustSystem` are all `lowerBound=1` in the metamodel, so creating a
powertrain without them would produce a structurally invalid model. They are created together.

**No `persistProjectRelative` here** — unlike rule 1. These objects are attached to `cCar` through
the containment chain and inherit its resource. Only root objects need explicit persistence.

**No `with` guard on the propulsion kind is needed either.** The `match` block does the filtering:
`retrieve combustion::CombustionRaceCar corresponding to raceCar` fails for an electric car,
because no such counterpart was ever created, and a failed retrieve aborts the routine silently.

### Rule 6 — deletion always comes in pairs

```java
update {
    removeObject(cPowertrain)
    removeCorrespondenceBetween(cPowertrain, slot)
}
```

Removing the object without removing the correspondence leaves a dangling link pointing at a
deleted object, which will break a later `retrieve`.

### Persistence rules — where a created object gets stored

A **persistence rule** decides which file a newly created object is written to. In the Reactions
DSL it is a single call:

```java
persistProjectRelative(
    raceCar,                                                        // anchor: path is relative to this
    combustionRaceCar,                                              // the new root object to store
    "models/combustion/" + raceCar.vehicleId + ".combustionracecar" // path and file name
)
```

**Why it is needed at all.** In EMF an object can exist in memory without belonging to any file.
Such an object is never written to disk, disappears when the V-SUM is reloaded, and — most
confusingly — is invisible to views: `getRootObjects(...)` returns an empty list. The reaction
looks like it did nothing, even though the routine ran without error. This is a hard failure mode
to diagnose, because nothing throws.

**Root objects need one, contained objects don't.**

| Object | Persistence |
|---|---|
| `CombustionRaceCar` (rule 1) | explicit `persistProjectRelative` — it is a root |
| `CombustionPowertrain`, `CombustionEngine`, `FuelTank`, `ExhaustSystem` (rule 2) | none needed — attached via `cCar.powertrain = cPowertrain`, they inherit the container's resource |

**The file name must be unique per object.** The rule originally used a fixed name,
`"example.combustionracecar"`. With more than one combustion car in the same V-SUM the second one
would overwrite the first one's file. Including `vehicleId` in the path makes each car land in its
own file:

```
models/combustion/RC-DEMO-01.combustionracecar
models/combustion/RC-DEMO-02.combustionracecar
```

The single-car tests never exposed this, because each test runs in its own `@TempDir` with exactly
one vehicle. It was found by comparing against the electric case study, which had already adopted
the `vehicleId`-based naming.

---

## 6. How a change flows through the system

The working cycle mirrors git: **check out → change → commit**.

```
1. check out   getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait()
                   └─► an editable, self-observing view

2. change      raceCar.setName("Renamed Car")
                   └─► ordinary Java; nothing has happened to the V-SUM yet

3. commit      view.commitChanges()
                   └─► the view derives the atomic change list and hands it to the V-SUM

4. propagate   Vitruvius runs every matching reaction
                   └─► RaceCarNameChanged fires
                        └─► retrieve CombustionRaceCar corresponding to raceCar
                             └─► cCar.name = raceCar.name

5. verify      getDefaultView(vsum, List.of(CombustionRaceCar.class))   ← a FRESH view
                   └─► the old view is a stale snapshot; never assert on it
```

`.withChangeDerivingTrait()` is what turns a read-only `View` into a `CommittableView`: it compares
state before and after your edits and derives the change list from the difference.

---

## 7. Findings — limitations discovered and documented

These were found experimentally during development and are covered by tests.

### 7.1 `iD="true"` blocks rename propagation

`RaceCar.vehicleId` is declared `iD="true"`. EMF uses an ID attribute to compute an object's URI
fragment, and state-based change derivation matches objects by that fragment. Changing the ID does
not produce an "attribute replaced" change, so `RaceCarVehicleIdChanged` never fires.

**How it was isolated:** `name` and `vehicleId` are both mandatory `EString` on `RaceCar` and differ
*only* in the ID flag. With the identical reaction pattern, `nameChangeIsPropagated` passes and
`vehicleId` propagation does not. Controlled comparison — the ID flag is the cause.

**Decision:** `vehicleId` is treated as an immutable identity; `name` is the mutable label. The
behaviour is pinned by the test `vehicleIdIsImmutableIdentityAndIsNotPropagatedOnChange`.

**Open question for the supervisors:** should `iD="true"` be removed from the shared
`racecar.ecore`? It affects the electric case study too.

### 7.2 `replaced at` also fires for `null`

Setting a single-valued reference to `null` (`raceCar.setPowertrainSlot(null)`) triggers the
`replaced at` reaction with `newValue = null`, which made the *create* routine run on a null slot
and throw a `NullPointerException`.

**Fix:** guard the create rule with `with newValue !== null`. Deletion is then handled solely by
`PowertrainSlotDeleted`.

### 7.3 `persistProjectRelative` is required for root objects

A created root object with no `persistProjectRelative` call is attached to no resource. It exists
in memory, but `getRootObjects(...)` returns an empty list and the reaction appears to do nothing.
Contained objects do not need it — they inherit the container's resource. See the persistence rule
section in §5 for the full explanation.

### 7.4 Persistence paths must be unique per object

A fixed file name in the persistence rule means the second object of the same kind overwrites the
first one's file. Single-object tests cannot catch this, because each test uses its own `@TempDir`
with exactly one vehicle. Fixed by including `vehicleId` in the path.

### 7.5 Cross-review against the electric case study

Reviewing `racecar2electrical.reactions` against this one surfaced issues on both sides — worth
recording, since the two case studies share the `racecar` metamodel and should converge on the same
patterns.

| Aspect | `racecar2electrical` | `racecar2combustion` |
|---|---|---|
| `persistProjectRelative` present | yes | yes |
| Unique file name per vehicle | yes | was missing, now fixed (§7.4) |
| Reacts to a `powertrainSlot` set *after* the car was created | no — the slot is only read inside the root-creation routine, so a two-step workflow is silently ignored | yes — separate `PowertrainSlotSetOnRaceCar` reaction |
| Correspondences for the powertrain and its children | not registered — only the root pair is | registered for `CombustionPowertrain` and `CombustionEngine` |
| `name` propagation | not possible — `electricalracecar.ecore` has no `name` attribute | supported |

The missing child correspondences on the electric side are latent rather than visible: everything
is created in one routine today, so nothing fails yet. They become blocking as soon as an update or
delete rule needs `retrieve ... corresponding to ...` for a motor, battery or inverter.

---

## 8. Tests

`vsum/src/test/java/kit/sdq/kastel/vitruvius/casestudy/vsum/CombustionRaceCarVsumTest.java`

Every test builds a **fresh V-SUM in a JUnit `@TempDir`**, so tests are independent.

| Test | Verifies |
|---|---|
| `combustionRaceCarIsCreatedByReaction` | Rule 1 fires and produces exactly one counterpart |
| `vehicleIdIsPropagated` | `vehicleId` is copied at creation time |
| `nameChangeIsPropagated` | Rule 3 — update path works |
| `powertrainIsCreatedForCombustionRaceCar` | Rule 2 creates the powertrain, `mountInterface` copied |
| `powertrainChildrenAreCreated` | Rule 2 satisfies the three `lowerBound=1` children |
| `ratedPowerChangeIsPropagatedToEngine` | Rule 4 — navigation-expression retrieve works |
| `removingPowertrainSlotRemovesCombustionPowertrain` | Rule 6 — delete path works |
| `electricRaceCarDoesNotTriggerCombustionReaction` | **Negative test**: the `with` guard actually filters |
| `vehicleIdIsImmutableIdentityAndIsNotPropagatedOnChange` | **Negative test**: documents finding 7.1 |

Coverage: create · update · delete · two negative cases.

### Test infrastructure

Four helpers carry the whole pattern:

```java
createDefaultVirtualModel(tempDir)   // builds the V-SUM, registers the reactions
getDefaultView(vsum, rootTypes)      // checks out a filtered view
modifyView(view, lambda)             // applies changes and commits
assertView(view, predicate)          // asserts on a freshly checked-out view
```

Plus a mandatory one-time setup, without which persistence fails:

```java
@BeforeAll
static void setup() {
  Resource.Factory.Registry.INSTANCE
      .getExtensionToFactoryMap()
      .put("*", new XMIResourceFactoryImpl());
}
```

---

## 9. Building

```bash
chmod +x mvnw     # once; mvnw was committed without the executable bit
sh mvnw install
```

**Do not run `clean`.** EMF code generation is *not* wired into the Maven build — the
`exec-maven-plugin` that would run `model/workflow/generate.mwe2` is commented out in
`model/pom.xml`. Generated EMF sources live in `model/target/generated-sources/ecore`, which
`clean` deletes and Maven does not recreate. Reactions code *is* generated by Maven
(`xtext-maven-plugin`), so only the EMF part is affected.

Regenerating EMF code after an ecore change, in Eclipse:

1. Edit the `.ecore`
2. `.genmodel` → right click → **Reload...**
3. `.genmodel` → right click → **Generate Model Code**
4. Project → **Maven → Update Project** (Alt+F5)

---

## 10. Known issues in the shared repository

Not caused by this case study, but they affect it.

| Issue | Impact | Suggested fix |
|---|---|---|
| `mvnw` committed with mode `100644` | `./mvnw` fails with *permission denied* | `chmod +x mvnw` and commit the mode change |
| EMF codegen not part of the Maven build | `mvnw clean install` fails on a fresh clone | Re-enable `exec-maven-plugin` in `model/pom.xml` |
| On `main`, the reactions file is named `" racecar2electrical.reactions"` (leading space) | Duplicate file after merge | Rename/remove on `main` |
| Electric side registers no correspondences for powertrain / motor / battery / inverter | Update and delete rules on the electric side cannot `retrieve` those objects later | Register correspondences at creation time, as rule 2 here does |
| Electric side ignores a `powertrainSlot` set after car creation | Two-step workflows silently produce no `mountInterface` | Add a `replaced at RaceCar[powertrainSlot]` reaction |
| `Chassis.mass`, `Wheel.diameter`, `Wheel.width`, `Wheel.mass` still `upperBound="-1"` | List-valued instead of single-valued; `getMass().add(x)` instead of `setMass(x)` | Same fix already applied to `RaceCar.totalMass` / `ratedPower` on `main` |

---

## 11. Next steps

- [ ] Extend the metamodel element by element, adding a rule + test for each addition
- [ ] Propagate `Chassis` / `Axle` data if the domain requires it
- [ ] Consider VitruviusOCL constraints as a declarative complement to the reactions
- [ ] Decide with the supervisors whether propagation should also run combustion → racecar (currently one-directional)
- [ ] Resolve the `iD="true"` question on the shared `racecar.ecore`

---

## Appendix — presentation cheat sheet

**One-sentence pitch:** two independent metamodels, kept consistent automatically by change-driven
rules, linked at runtime through a correspondence model rather than by metamodel references.

**Three things worth showing:**

1. The `with` guard and the negative test proving it filters — consistency rules are selective, not blind.
2. The four-object creation in rule 2 — how `lowerBound=1` constraints shape the repair logic.
3. Finding 7.1 — a limitation isolated by controlled experiment, then pinned by a test rather than left as a failing build.

**On persistence rules, if asked.** `persistProjectRelative` is the rule that decides which file a
newly created root object lands in. Forgetting it is the nastiest failure mode in this framework:
the routine completes without error, but the object belongs to no resource, so it is never written
to disk and never appears in a view — the reaction looks like it silently did nothing. Contained
objects are exempt; they inherit the container's file. The path must be unique per object, which is
why `vehicleId` is part of it.

**Also worth mentioning.** Not everything in the combustion model is reaction-derived.
`cylinderCount`, `displacement`, `fuelTank.capacity` and `exhaustSystem.backPressure` have no
counterpart anywhere in `racecar.ecore` — and must not, since that metamodel is shared with the
electric case study. There is no source change for a reaction to react to, so this data is authored
directly on the combustion side. Correspondence means "these two objects describe the same real
thing", not "every field has a twin". `CombustionVSUMExample` demonstrates both kinds side by side
and labels them in its output.

**If asked "why not just one model?"** — the two metamodels evolve under separate pressure. The
generic `racecar` model is shared with the electric case study; a combustion-specific field must not
leak into it. Vitruvius combines them non-invasively so each can change independently.
