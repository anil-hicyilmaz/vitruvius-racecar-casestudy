package kit.sdq.kastel.vitruvius.casestudy.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import mir.reactions.combustion2combustion.Combustion2combustionChangePropagationSpecification;
import mir.reactions.racecar2combustion.Racecar2combustionChangePropagationSpecification;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import kit.sdq.kastel.vitruvius.casestudy.model.combustionracecar.CombustionRaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PowertrainSlot;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;

/**
 * Tests for the newer additions to the combustion case study:
 *
 * <ul>
 *   <li>{@code combustion2combustion.reactions} - self-consistency within the combustion model
 *       (the derived {@code CombustionPowertrain.mass}).
 *   <li>The {@code ratedPower > 0} constraints added to {@code racecar2combustion.reactions}.
 *   <li>The user-decided model creation path ({@code PropulsionKindChangedToCombustion}), as
 *       opposed to the fully automatic one exercised by {@link CombustionRaceCarVsumTest}.
 * </ul>
 *
 * <p>Unlike {@link CombustionRaceCarVsumTest}, the V-SUM built here registers <b>two</b> change
 * propagation specifications, because {@code combustion2combustion} needs to run alongside
 * {@code racecar2combustion} to see a fully populated powertrain.
 */
class CombustionInternalConsistencyVsumTest {

  @BeforeAll
  static void setup() {
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());
  }

  // ------------------------------------------------------------- self-consistency (mass) tests

  /** Editing engine.mass alone must recompute the powertrain's total mass. */
  @Test
  void engineMassChangePropagatesToPowertrainMass(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir, 560.0);
    addPowertrainSlot(vsum, "MI-V6");

    modifyView(
        getDefaultView(vsum, List.of(CombustionRaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          var powertrain =
              v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();
          powertrain.getEngine().setMass(112.0);
          powertrain.getFuelTank().setMass(4.5);
          powertrain.getExhaustSystem().setMass(18.0);
        });

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) -> {
              var powertrain =
                  v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();
              return powertrain.getMass() == 112.0 + 4.5 + 18.0;
            }));
  }

  /** A second, independent edit to just one child mass must still recompute the correct sum. */
  @Test
  void subsequentSingleMassEditRecomputesPowertrainMass(@TempDir Path tempDir)
      throws IOException {
    InternalVirtualModel vsum = createVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir, 560.0);
    addPowertrainSlot(vsum, "MI-V6");

    modifyView(
        getDefaultView(vsum, List.of(CombustionRaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          var powertrain =
              v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();
          powertrain.getEngine().setMass(112.0);
          powertrain.getFuelTank().setMass(4.5);
          powertrain.getExhaustSystem().setMass(18.0);
        });

    // Only the exhaust system gets heavier later (e.g. a bigger muffler) - engine and fuel tank
    // mass are unchanged. The reaction must re-read all three current values, not just the one
    // that changed.
    modifyView(
        getDefaultView(vsum, List.of(CombustionRaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          var powertrain =
              v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();
          powertrain.getExhaustSystem().setMass(22.0);
        });

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) -> {
              var powertrain =
                  v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();
              return powertrain.getMass() == 112.0 + 4.5 + 22.0;
            }));
  }

  // --------------------------------------------------------------------------- constraint tests

  /**
   * Constraint: a combustion powertrain must not be created for a car with non-positive rated
   * power. Setting the slot must not blow up, but it also must not produce a powertrain.
   */
  @Test
  void nonPositiveRatedPowerBlocksPowertrainCreation(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir, 0.0);
    addPowertrainSlot(vsum, "MI-V6");

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) ->
                v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain()
                    == null));
  }

  /**
   * Constraint: once a valid powertrain exists, a later non-positive ratedPower update must not
   * be propagated - the engine keeps its last valid maxPower instead of an invalid one.
   */
  @Test
  void laterNonPositiveRatedPowerIsNotPropagatedToEngine(@TempDir Path tempDir)
      throws IOException {
    InternalVirtualModel vsum = createVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir, 560.0);
    addPowertrainSlot(vsum, "MI-V6");

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class).iterator().next().setRatedPower(-10.0));

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) ->
                v.getRootObjects(CombustionRaceCar.class)
                        .iterator()
                        .next()
                        .getPowertrain()
                        .getEngine()
                        .getMaxPower()
                    == 560.0));
  }

  // -------------------------------------------------------- user-decided model creation tests

  /** propulsionKind changing to COMBUSTION after creation, confirmed by the user, must create it. */
  @Test
  void userConfirmedLateConversionCreatesCombustionRaceCar(@TempDir Path tempDir)
      throws IOException {
    TestUserInteraction userInteraction = new TestUserInteraction();
    userInteraction.addNextConfirmationInput(true);
    InternalVirtualModel vsum = createVirtualModel(tempDir, userInteraction);

    registerUnspecifiedRaceCar(vsum, tempDir);
    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class)
                .iterator()
                .next()
                .setPropulsionKind(PropulsionKind.COMBUSTION));

    Assertions.assertEquals(
        1, getDefaultView(vsum, List.of(CombustionRaceCar.class)).getRootObjects().size());
  }

  /** Same scenario, but the user declines - no CombustionRaceCar must be created. */
  @Test
  void userDeclinedLateConversionCreatesNothing(@TempDir Path tempDir) throws IOException {
    TestUserInteraction userInteraction = new TestUserInteraction();
    userInteraction.addNextConfirmationInput(false);
    InternalVirtualModel vsum = createVirtualModel(tempDir, userInteraction);

    registerUnspecifiedRaceCar(vsum, tempDir);
    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class)
                .iterator()
                .next()
                .setPropulsionKind(PropulsionKind.COMBUSTION));

    Assertions.assertEquals(
        0, getDefaultView(vsum, List.of(CombustionRaceCar.class)).getRootObjects().size());
  }

  // ------------------------------------------------------- combined cross-model propagation test

  /** Two independent attribute changes committed together must both propagate correctly. */
  @Test
  void nameAndRatedPowerChangeInSameCommitBothPropagate(@TempDir Path tempDir)
      throws IOException {
    InternalVirtualModel vsum = createVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir, 560.0);
    addPowertrainSlot(vsum, "MI-V6");

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = v.getRootObjects(RaceCar.class).iterator().next();
          raceCar.setName("Renamed In Same Commit");
          raceCar.setRatedPower(610.0);
        });

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) -> {
              CombustionRaceCar car = v.getRootObjects(CombustionRaceCar.class).iterator().next();
              return "Renamed In Same Commit".equals(car.getName())
                  && car.getPowertrain().getEngine().getMaxPower() == 610.0;
            }));
  }

  // ---------------------------------------------------------------------------------- helpers

  private InternalVirtualModel createVirtualModel(Path projectPath) throws IOException {
    return createVirtualModel(
        projectPath,
        new TestUserInteraction());
  }

  private InternalVirtualModel createVirtualModel(
      Path projectPath, TestUserInteraction userInteraction) throws IOException {
    InternalVirtualModel model =
        new VirtualModelBuilder()
            .withStorageFolder(projectPath)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(userInteraction))
            .withChangePropagationSpecifications(
                new Racecar2combustionChangePropagationSpecification(),
                new Combustion2combustionChangePropagationSpecification())
            .buildAndInitialize();
    model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
    return model;
  }

  private void addCombustionRaceCar(VirtualModel vsum, Path projectPath, double ratedPower) {
    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = RacecarFactory.eINSTANCE.createRaceCar();
          raceCar.setVehicleId("RC-010");
          raceCar.setName("Combustion Race Car");
          raceCar.setPropulsionKind(PropulsionKind.COMBUSTION);
          raceCar.setRatedPower(ratedPower);
          raceCar.setTotalMass(798.0);
          v.registerRoot(raceCar, URI.createFileURI(projectPath + "/example.racecar"));
        });
  }

  /** Registers a RaceCar with UNSPECIFIED propulsion, for the late-conversion tests. */
  private void registerUnspecifiedRaceCar(VirtualModel vsum, Path projectPath) {
    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = RacecarFactory.eINSTANCE.createRaceCar();
          raceCar.setVehicleId("RC-020");
          raceCar.setName("Undecided Race Car");
          raceCar.setPropulsionKind(PropulsionKind.UNSPECIFIED);
          raceCar.setRatedPower(560.0);
          raceCar.setTotalMass(798.0);
          v.registerRoot(raceCar, URI.createFileURI(projectPath + "/example.racecar"));
        });
  }

  private void addPowertrainSlot(VirtualModel vsum, String mountInterface) {
    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = v.getRootObjects(RaceCar.class).iterator().next();
          PowertrainSlot slot = RacecarFactory.eINSTANCE.createPowertrainSlot();
          slot.setRequiredType(PropulsionKind.COMBUSTION);
          slot.setMountInterface(mountInterface);
          raceCar.setPowertrainSlot(slot);
        });
  }

  private View getDefaultView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().stream()
        .filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
        .forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }

  private void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
    modificationFunction.accept(view);
    view.commitChanges();
  }

  private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
    return viewAssertionFunction.apply(view);
  }
}
