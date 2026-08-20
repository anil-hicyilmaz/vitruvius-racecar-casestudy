package kit.sdq.kastel.vitruvius.casestudy.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PowertrainSlot;

/**
 * Tests the racecar2combustion consistency preservation rules.
 *
 * <p>Each test builds a fresh V-SUM in a temporary folder, modifies it through a committable
 * view, and then checks a newly created view to verify that the reactions produced the
 * expected result in the combustion model.
 */
class CombustionRaceCarVsumTest {

  /** Registers the XMI factory so EMF can persist files with arbitrary extensions. */
  @BeforeAll
  static void setup() {
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());
  }

  /** A combustion RaceCar must cause the reaction to create a corresponding CombustionRaceCar. */
  @Test
  void combustionRaceCarIsCreatedByReaction(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);

    Assertions.assertEquals(
        1,
        getDefaultView(vsum, List.of(CombustionRaceCar.class)).getRootObjects().size());
  }

  /** The vehicleId must be propagated from the racecar model to the combustion model. */
  @Test
  void vehicleIdIsPropagated(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(RaceCar.class, CombustionRaceCar.class)),
            (View v) ->
                v.getRootObjects(RaceCar.class).iterator().next().getVehicleId()
                    .equals(
                        v.getRootObjects(CombustionRaceCar.class)
                            .iterator()
                            .next()
                            .getVehicleId())));
  }

  /**
   * An electric RaceCar must NOT trigger the combustion reaction. This verifies the
   * {@code with newValue.propulsionKind == PropulsionKind.COMBUSTION} guard.
   */
  @Test
  void electricRaceCarDoesNotTriggerCombustionReaction(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = RacecarFactory.eINSTANCE.createRaceCar();
          raceCar.setVehicleId("RC-ELEC");
          raceCar.setName("Electric Race Car");
          raceCar.setPropulsionKind(PropulsionKind.ELECTRIC);
          v.registerRoot(raceCar, URI.createFileURI(tempDir + "/electric.racecar"));
        });

    Assertions.assertEquals(
        0,
        getDefaultView(vsum, List.of(CombustionRaceCar.class)).getRootObjects().size());
  }

  /** Setting a PowertrainSlot must create a corresponding CombustionPowertrain. */
  @Test
  void powertrainIsCreatedForCombustionRaceCar(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = v.getRootObjects(RaceCar.class).iterator().next();
          PowertrainSlot slot = RacecarFactory.eINSTANCE.createPowertrainSlot();
          slot.setRequiredType(PropulsionKind.COMBUSTION);
          slot.setMountInterface("MI-V6");
          raceCar.setPowertrainSlot(slot);
        });

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) -> {
              CombustionRaceCar car =
                  v.getRootObjects(CombustionRaceCar.class).iterator().next();
              return car.getPowertrain() != null
                  && "MI-V6".equals(car.getPowertrain().getMountInterface());
            }));
  }

  /**
   * Documents a deliberate limitation.
   *
   * <p>{@code vehicleId} is declared with {@code iD="true"} in {@code racecar.ecore}. EMF uses an
   * ID attribute to compute the object's URI fragment, and the state-based change derivation of
   * {@code withChangeDerivingTrait()} matches objects by that fragment. Changing the ID therefore
   * does not produce an "attribute replaced" change, so the {@code RaceCarVehicleIdChanged}
   * reaction never fires and the combustion model keeps the original id.
   *
   * <p>This was verified experimentally: {@code name} is also a mandatory EString on RaceCar and
   * differs only in not being an ID — and {@link #nameChangeIsPropagated} passes with the exact
   * same reaction pattern.
   *
   * <p>Consequence for the case study: {@code vehicleId} is treated as an immutable identity,
   * {@code name} is the mutable label.
   */
  @Test
  void vehicleIdIsImmutableIdentityAndIsNotPropagatedOnChange(@TempDir Path tempDir)
      throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class).iterator().next().setVehicleId("RC-999"));

    Assertions.assertEquals(
        "RC-002",
        getDefaultView(vsum, List.of(CombustionRaceCar.class))
            .getRootObjects(CombustionRaceCar.class)
            .iterator()
            .next()
            .getVehicleId());
  }

  /** Removing the PowertrainSlot must also remove the corresponding CombustionPowertrain. */
  @Test
  void removingPowertrainSlotRemovesCombustionPowertrain(@TempDir Path tempDir)
      throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);
    addPowertrainSlot(vsum, "MI-V6");

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class).iterator().next().setPowertrainSlot(null));

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) ->
                v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain()
                    == null));
  }

  /** The mandatory children of CombustionPowertrain must be created together with it. */
  @Test
  void powertrainChildrenAreCreated(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);
    addPowertrainSlot(vsum, "MI-V6");

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) -> {
              var pt =
                  v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();
              return pt.getEngine() != null
                  && pt.getFuelTank() != null
                  && pt.getExhaustSystem() != null;
            }));
  }

  /** ratedPower must be propagated to the engine's maxPower, also when changed later. */
  @Test
  void ratedPowerChangeIsPropagatedToEngine(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);
    addPowertrainSlot(vsum, "MI-V6");

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class).iterator().next().setRatedPower(610.0));

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
                    == 610.0));
  }

  @Test
  void nameChangeIsPropagated(@TempDir Path tempDir) throws IOException {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCombustionRaceCar(vsum, tempDir);

    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) ->
            v.getRootObjects(RaceCar.class).iterator().next().setName("Renamed Car"));

    Assertions.assertTrue(
        assertView(
            getDefaultView(vsum, List.of(CombustionRaceCar.class)),
            (View v) ->
                "Renamed Car".equals(
                    v.getRootObjects(CombustionRaceCar.class).iterator().next().getName())));
  }

  // ---------------------------------------------------------------- helpers

  /** Registers a combustion RaceCar as a new root object and commits the change. */
  private void addCombustionRaceCar(VirtualModel vsum, Path projectPath) {
    modifyView(
        getDefaultView(vsum, List.of(RaceCar.class)).withChangeDerivingTrait(),
        (CommittableView v) -> {
          RaceCar raceCar = RacecarFactory.eINSTANCE.createRaceCar();
          raceCar.setVehicleId("RC-002");
          raceCar.setName("Combustion Race Car");
          raceCar.setPropulsionKind(PropulsionKind.COMBUSTION);
          raceCar.setRatedPower(560.0);
          raceCar.setTotalMass(798.0);
          v.registerRoot(raceCar, URI.createFileURI(projectPath + "/example.racecar"));
        });
  }

  private InternalVirtualModel createDefaultVirtualModel(Path projectPath) throws IOException {
    InternalVirtualModel model =
        new VirtualModelBuilder()
            .withStorageFolder(projectPath)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(new TestUserInteraction()))
            .withChangePropagationSpecifications(
                new Racecar2combustionChangePropagationSpecification())
            .buildAndInitialize();
    model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
    return model;
  }

  /** Creates a view restricted to the given root types. */
  private View getDefaultView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().stream()
        .filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
        .forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }

  /** Applies the modification to the view and commits it, triggering change propagation. */
  private void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
    modificationFunction.accept(view);
    view.commitChanges();
  }

  private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
    return viewAssertionFunction.apply(view);
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
}
