# Reactions, Constraints & Model Creation — Study Guide

A tutorial-style companion to [`combustion-case-study.md`](combustion-case-study.md). That
document is a reference for *what* exists; this one is about *how the machinery works* and *why*
it is built that way, so you can explain the structure and logic yourself afterward — not just
point at a diff.

Everything here is scoped to the **combustion** side of the project
(`consistency/.../racecar2combustion.reactions`, `consistency/.../combustion2combustion.reactions`,
`vsum/.../Combustion*Test.java`). The electrical case study is not touched anywhere.

---

## 1. How the existing test cases work

Every Vitruvius test in this project — old or new — follows the same five-step shape, because it
mirrors how a *real* engineer would use the V-SUM:

```
1. build   a fresh V-SUM (in a throwaway @TempDir, so tests never interfere with each other)
2. check out  a View, restricted to the object types you care about
3. change   ordinary Java field setters on the objects in that view
4. commit   view.commitChanges() — this is the only moment anything actually happens
5. verify   check out a *fresh* View and assert on it
```

### Why a *view*, and not the model directly?

Vitruvius never lets you touch a model object without going through a `View`. A view is a
filtered, negotiated window onto the V-SUM — you declare which root types you want
(`getDefaultView(vsum, List.of(RaceCar.class))`), and only those become visible/editable.

A plain `View` is read-only. Calling `.withChangeDerivingTrait()` upgrades it to a
`CommittableView`: internally it snapshots the current state, lets you mutate freely with normal
setters, and on `commitChanges()` it *diffs* your edits against the snapshot to produce a list of
atomic changes (`ReplaceSingleValuedEAttribute`, `InsertRootEObject`, …). That diff is what
reactions actually react to — not your Java statements themselves.

This is why the test helper `modifyView` always looks the same:

```java
private void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
    modificationFunction.accept(view);   // step 3: plain setters
    view.commitChanges();                // step 4: diff + propagate
}
```

### Why a *fresh* view to assert on?

`assertView` always takes a brand-new `View`, never the one you just wrote through:

```java
Assertions.assertTrue(
    assertView(
        getDefaultView(vsum, List.of(CombustionRaceCar.class)),   // fresh
        (View v) -> ...));
```

The view you wrote through is a stale, already-diffed snapshot. Reactions ran *after* your
commit, inside the V-SUM, on the underlying resources — your old view object never saw that.
Checking out a new view is the only way to see the *post-reaction* state.

### The one-time setup

```java
@BeforeAll
static void setup() {
  Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
}
```

EMF needs to be told how to read/write files with the project's custom extensions
(`.racecar`, `.combustionracecar`). Skip this and every `persistProjectRelative` call fails
silently later.

### What's different in the new test class

[`CombustionInternalConsistencyVsumTest.java`](../vsum/src/test/java/kit/sdq/kastel/vitruvius/casestudy/vsum/CombustionInternalConsistencyVsumTest.java)
follows the exact same five steps, with one addition: it registers **two**
`ChangePropagationSpecification`s instead of one —

```java
.withChangePropagationSpecifications(
    new Racecar2combustionChangePropagationSpecification(),
    new Combustion2combustionChangePropagationSpecification())
```

— because two independent rule sets now need to run in the same V-SUM (see §2.3). It also shows
a new helper, `createVirtualModel(path, TestUserInteraction)`, for tests that need to script a
canned user answer (see §4).

---

## 2. How Vitruvius reactions work

### 2.1 The vocabulary

| Term | Role |
|---|---|
| **Reaction** | *When*: a trigger — "after some kind of change happens to some kind of object". |
| **Routine** | *How*: the repair logic — `match` → `create` → `update`. |
| **`match`** | Decide whether the routine should run at all, and fetch the objects it needs. Any failure here aborts the whole routine — nothing is created or changed. |
| **`create`** | Instantiate new, still-unattached objects. |
| **`update`** | Set fields, attach the created objects into the model, persist, register/remove correspondences. |
| **Correspondence** | A stored pair, "this object here is the same real thing as that object there", queried with `retrieve X corresponding to Y`. |
| **ChangePropagationSpecification** | The generated Java class (one per `reactions:` file) that the V-SUM is told to run. |

### 2.2 The engine underneath (what the DSL compiles to)

The `.reactions` files are not interpreted — they are compiled by an Xtext/Xtend generator into
plain Java, checked into `consistency/target/generated-sources/reactions/`. Reading that
generated code is the fastest way to see exactly what a rule does, with no DSL ambiguity left.
For example, `reaction RaceCarNameChanged` becomes a class
`RaceCarNameChangedReaction` whose `isCurrentChangeMatchingTrigger(EChange)` method does the
actual type/feature-name check, and whose `executeReaction(...)` extracts `oldValue`/`newValue`
and calls the matching `...Routine`.

Every routine compiles into three static inner classes — `Match`, `Create`, `Update` — mirroring
the three DSL blocks 1:1. Knowing this means you never have to guess what a DSL construct
"really" does: you can always look.

### 2.3 Combustion-specific reactions — two rule sets now, not one

Before this change, there was one direction of propagation:

```
racecar2combustion.reactions:  racecar  ──►  combustion     (cross-metamodel)
```

This project now adds a second, orthogonal rule set:

```
combustion2combustion.reactions:  combustion  ──►  combustion   (intra-model / self-consistency)
```

`combustion2combustion.reactions` reacts to changes **within** the combustion model itself. Its
header makes that explicit:

```java
reactions: combustion2combustion
in reaction to changes in combustion
execute actions in combustion
```

The three reactions in it (`EngineMassChanged`, `FuelTankMassChanged`, `ExhaustSystemMassChanged`)
all keep one derived value correct: `CombustionPowertrain.mass`, which has no counterpart in
`racecar.ecore` at all — it's computed purely from `engine.mass + fuelTank.mass +
exhaustSystem.mass`, three fields that already live on the combustion side.

```java
routine recalculatePowertrainMassFromEngine(combustion::CombustionEngine engine) {
    update {
        val cPowertrain = engine.eContainer as kit.sdq.kastel.vitruvius.casestudy.model.combustionracecar.CombustionPowertrain
        cPowertrain.mass = cPowertrain.engine.mass
            + cPowertrain.fuelTank.mass
            + cPowertrain.exhaustSystem.mass
    }
}
```

Two things are worth noticing:

1. **No correspondence lookup.** `retrieve X corresponding to Y` is for *cross-model* links.
   Here, engine and powertrain live in the *same* model and are already connected by a plain EMF
   containment reference, so the routine just walks up with `engine.eContainer`, cast to the
   concrete type. There's nothing "Vitruvius-specific" about that navigation — it's ordinary EMF.
2. **`combustion::Type` only works in DSL positions** (like `create { new combustion::X }` or a
   routine parameter type). Inside a plain Xtend expression such as an `as` cast, you need the
   fully-qualified generated Java class name — the DSL's short alias isn't visible there. This
   was found by trial: the first version of this file used `engine.eContainer as
   combustion::CombustionPowertrain` and the Xtend compiler rejected it with `combustion cannot be
   resolved to a type`.

Because three different trigger points (engine/fuelTank/exhaustSystem mass) all recompute the
*same* derived value by re-reading all three current fields — not just copying whichever one
changed — this is a genuinely more complex rule than the straight 1:1 attribute copies in
`racecar2combustion.reactions` (like `RaceCarNameChanged`). It's also why both a fresh
`engineMassChangePropagatesToPowertrainMass` test *and* a follow-up
`subsequentSingleMassEditRecomputesPowertrainMass` test exist — the second one exists specifically
to catch a rule that accidentally only re-added the *changed* value instead of recomputing the
full sum.

### 2.4 Running two rule sets in one V-SUM

Both specifications are registered on the same `VirtualModelBuilder`:

```java
.withChangePropagationSpecifications(
    new Racecar2combustionChangePropagationSpecification(),
    new Combustion2combustionChangePropagationSpecification())
```

Vitruvius runs every registered specification against every change, regardless of which file
"caused" it. When a test sets `powertrain.getEngine().setMass(112.0)` on the combustion side, only
`combustion2combustion`'s reactions match (the change is on a `combustion::CombustionEngine`);
`racecar2combustion`'s reactions simply don't fire, because none of their triggers mention
`CombustionEngine.mass`. There is no manual routing needed — matching is purely by change type and
metaclass.

With `ChangePropagationMode.TRANSITIVE_CYCLIC`, this also composes automatically:
if a `racecar2combustion` reaction created/changed a combustion-side attribute, and that in turn
matches a `combustion2combustion` trigger, the second reaction fires *within the same commit*,
transitively. That's what makes `powertrainChildrenAreCreated`-style creation and mass
recalculation compose without any explicit "call the other spec" glue code.

---

## 3. How constraints work, and where they live

There is **no separate "constraint language"** in this project (no OCL, no EMF Validation
framework wired in — the original docs flag that as a "next step", still true). Two different
mechanisms currently play the role of "constraints", at two different points in time:

### 3.1 Structural constraints — in the `.ecore` files

`lowerBound="1"` / `upperBound="1"` on a reference (e.g. `CombustionPowertrain.engine`) is a
*structural* constraint: "every valid CombustionPowertrain has exactly one engine." Reactions
respect this passively — `createCombustionPowertrain` creates all three mandatory children
(`engine`, `fuelTank`, `exhaustSystem`) in the same routine specifically *because* leaving one out
would produce a structurally invalid model. This kind of constraint is enforced by convention in
the reaction author's code, not by a runtime check — EMF's generated setters don't reject `null`
on a containment reference by themselves.

### 3.2 Behavioral constraints — the `check` statement in a routine's `match` block

This is the mechanism used for the new constraints in this change. `check <boolean expression>`
sits inside `match`, alongside `require absence of` and `retrieve`:

```java
match {
    require absence of combustion::CombustionPowertrain corresponding to slot
    val cCar = retrieve combustion::CombustionRaceCar corresponding to raceCar
    check raceCar.ratedPower > 0
}
```

If the expression is `false`, the whole routine aborts — exactly like a failed `require absence
of` or a `retrieve` that finds nothing. Nothing is created, nothing is updated, no exception is
thrown. Looking at the generated code makes the mechanism concrete:

```java
public boolean checkMatcherPrecondition1(final RaceCar raceCar, final PowertrainSlot slot, final CombustionRaceCar cCar) {
  double _ratedPower = raceCar.getRatedPower();
  boolean _greaterThan = (_ratedPower > 0);
  return _greaterThan;
}
...
if (!checkMatcherPrecondition1(raceCar, slot, cCar)) {
    return null;   // match fails -> routine aborts
}
```

**Important nuance, worth understanding precisely:** this is a *repair-time* guard, not an
*edit-time* validator. By the time the reaction fires, the user's edit on the `racecar` side has
already happened — `raceCar.ratedPower` is already `0` (or negative) in the source model. `check`
doesn't undo that edit or block the commit; it only decides whether *this routine's own repair
actions* (creating a powertrain, updating the engine's `maxPower`) should proceed. Two concrete
consequences, both pinned by tests in
[`CombustionInternalConsistencyVsumTest.java`](../vsum/src/test/java/kit/sdq/kastel/vitruvius/casestudy/vsum/CombustionInternalConsistencyVsumTest.java):

- `nonPositiveRatedPowerBlocksPowertrainCreation` — setting the `PowertrainSlot` on a car with
  `ratedPower = 0` never produces a `CombustionPowertrain` at all (the *creation* routine's `check`
  fails).
- `laterNonPositiveRatedPowerIsNotPropagatedToEngine` — once a valid powertrain exists, later
  setting `ratedPower = -10` does *not* overwrite the engine's `maxPower`; it silently keeps the
  last valid value (the *update* routine's `check` fails, so `cEngine.maxPower = raceCar.ratedPower`
  never executes).

If you actually wanted to reject the edit itself (e.g. refuse to let `ratedPower` become negative
in the first place), you'd need EMF Validation / OCL constraints on `racecar.ecore` directly — a
different, complementary mechanism, still on the "next steps" list.

Where the two new constraints live: both are in
[`racecar2combustion.reactions`](../consistency/src/main/reactions/kit/sdq/kastel/vitruvius/casestudy/consistency/racecar2combustion.reactions),
inside `createCombustionPowertrain`'s and `updateEngineMaxPower`'s `match` blocks respectively —
right next to the `require absence of` / `retrieve` statements they logically belong with.

---

## 4. Model creation — automatic vs. user-decided

The case study now has **two** creation paths for a `CombustionRaceCar`, and the difference
between them is the clearest illustration of "automatic" vs. "user-decided" model creation.

### 4.1 Automatic — `CreateCombustionRaceCar` (unchanged, pre-existing)

```java
reaction CreateCombustionRaceCar {
    after element racecar::RaceCar inserted as root
    with newValue.propulsionKind == PropulsionKind.COMBUSTION
    call createCombustionRaceCar(newValue)
}
```

Trigger: a *new* `RaceCar` is inserted as a root object, already carrying `propulsionKind ==
COMBUSTION`. There is nothing to decide — the engineer's intent was already expressed by choosing
`COMBUSTION` at creation time, so Vitruvius just repairs the model immediately, with no dialog.
This is the right default whenever the reaction has enough information to act unambiguously and
the action is cheap/expected to reverse (or simply: whenever asking would just be noise).

### 4.2 User-decided — `PropulsionKindChangedToCombustion` (new)

There is a gap in the automatic path: it only fires on *insertion*. If a `RaceCar` already exists
— created with `propulsionKind = UNSPECIFIED`, maybe already has other data attached — and is
*later* switched to `COMBUSTION` by an attribute change, nothing reacted to that before this
change. That's a bigger, more consequential decision than initial creation, so instead of silently
repairing it, the new reaction asks first:

```java
reaction PropulsionKindChangedToCombustion {
    after attribute replaced at racecar::RaceCar[propulsionKind]
    with newValue == PropulsionKind.COMBUSTION && oldValue != PropulsionKind.COMBUSTION
    call createCombustionRaceCarWithUserConfirmation(affectedEObject)
}
```

The confirmation itself happens inside the routine's `update` block, via the `UserInteractor`
that every routine has implicit access to through its `ReactionExecutionState`:

```java
update {
    val userConfirmedCreation = userInteractor
        .getConfirmationDialogBuilder()
        .message("RaceCar " + raceCar.vehicleId + " was switched to COMBUSTION propulsion "
            + "after it was created. Create the corresponding CombustionRaceCar now?")
        .startInteraction()

    if (userConfirmedCreation) {
        combustionRaceCar.vehicleId = raceCar.vehicleId
        combustionRaceCar.name = raceCar.name
        persistProjectRelative(raceCar, combustionRaceCar, "models/combustion/" + raceCar.vehicleId + ".combustionracecar")
        addCorrespondenceBetween(combustionRaceCar, raceCar)
    }
}
```

Notice the object is still created unconditionally in the `create` block (Vitruvius routines
create-then-decide, not decide-then-create), but if the user says no, it is simply never
persisted and never gets a correspondence. That is exactly the same "no persistence → no effect"
mechanism the original docs describe for a *forgotten* `persistProjectRelative` call (§7.3 in
`combustion-case-study.md`) — here it's used deliberately: an unpersisted, uncorresponded object
has no container, so it's simply garbage-collected and never appears in any view.

### 4.3 How this is testable at all — `TestUserInteraction`

`UserInteractor` is an interface; in production it would pop a real dialog. In tests, the V-SUM is
built with a scripted answer instead:

```java
TestUserInteraction userInteraction = new TestUserInteraction();
userInteraction.addNextConfirmationInput(true);   // or false

InternalVirtualModel vsum = new VirtualModelBuilder()
    .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(userInteraction))
    ...
```

`addNextConfirmationInput(boolean)` queues exactly one canned answer for the *next* confirmation
dialog the code under test triggers. This is what makes
`userConfirmedLateConversionCreatesCombustionRaceCar` (answer = `true`) and
`userDeclinedLateConversionCreatesNothing` (answer = `false`) deterministic, fast unit tests
instead of something requiring a human to click a button.

### 4.4 Summary table

| | Automatic | User-decided |
|---|---|---|
| Reaction | `CreateCombustionRaceCar` | `PropulsionKindChangedToCombustion` |
| Trigger | `RaceCar inserted as root` | `attribute replaced at RaceCar[propulsionKind]` |
| When it fires | Car created already as COMBUSTION | Car *converted* to COMBUSTION after the fact |
| Decision point | None — intent already explicit | `userInteractor.getConfirmationDialogBuilder()...startInteraction()` |
| On "no" | n/a | Object created in memory, never persisted/corresponded → invisible, GC'd |
| Test lever | n/a | `TestUserInteraction.addNextConfirmationInput(true/false)` |

---

## 5. Where to go from here

- [`combustion-case-study.md`](combustion-case-study.md) — the original reference: full rule
  table, persistence-rule mechanics, and the findings log (§7) that this tutorial builds on.
- `consistency/target/generated-sources/reactions/` — after building once, this is the ground
  truth for what any `.reactions` construct compiles to. Reading it beats guessing.
- Next steps still open (from the original docs, still valid): real OCL/EMF-Validation
  constraints as edit-time validators, and deciding whether propagation should ever run
  combustion → racecar.
