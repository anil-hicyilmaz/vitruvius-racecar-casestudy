package kit.sdq.kastel.vitruvius.casestudy.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import mir.reactions.racecar2combustion.Racecar2combustionChangePropagationSpecification;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import kit.sdq.kastel.vitruvius.casestudy.model.combustionracecar.CombustionRaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Axle;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Chassis;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Position;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PowertrainSlot;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Wheel;

/**
 * Interactive, runnable walkthrough of the combustion case study.
 *
 * <p>Unlike {@link CombustionRaceCarVsumTest}, which builds a V-SUM in a throwaway {@code
 * @TempDir} that JUnit deletes after every test, this class writes to a real, persistent folder
 * so the resulting {@code .racecar} and {@code .combustionracecar} XMI files can be inspected
 * afterwards with a text editor or the EMF tree editor in Eclipse.
 *
 * <p>It walks through the full lifecycle in one run, so every reaction in {@code
 * racecar2combustion.reactions} fires at least once:
 *
 * <ol>
 *   <li><b>Create</b> a fully populated {@link RaceCar} (chassis, two axles, four wheels) as a
 *       new root object.
 *   <li><b>Create</b> a {@link PowertrainSlot} &rarr; triggers creation of the combustion
 *       powertrain and its three mandatory children.
 *   <li><b>Update</b> the car's name and rated power in one commit &rarr; both propagate
 *       independently to the combustion model.
 *   <li><b>Delete</b> the powertrain slot &rarr; the combustion powertrain is removed and its
 *       correspondence is cleaned up.
 * </ol>
 *
 * <p>Run it either:
 *
 * <ul>
 *   <li>From Eclipse: right click this file &rarr; Run As &rarr; Java Application. No pom changes
 *       needed. Pass arguments via Run Configurations &rarr; Arguments if you want non-default
 *       values.
 *   <li>From Maven: {@code mvn -pl vsum -am compile exec:java
 *       -Dexec.mainClass=kit.sdq.kastel.vitruvius.casestudy.vsum.CombustionVSUMExample
 *       -Dexec.args="--reset"} (requires the {@code exec-maven-plugin}; add it to {@code
 *       vsum/pom.xml} if it's not there yet).
 * </ul>
 *
 * <p>Command-line arguments (all optional):
 *
 * <pre>
 *   --reset                  delete the storage folder before running, for a clean start
 *   --vehicle-id=RC-XYZ      default: RC-DEMO-01
 *   --name=My Car            default: Demo Combustion Car
 *   --rated-power=560.0      default: 560.0
 *   --total-mass=798.0       default: 798.0
 * </pre>
 *
 * <p>Without {@code --reset}, re-running against a non-empty storage folder registers a second
 * {@code RaceCar} root alongside the first one â€” the {@code require absence of} guards only
 * prevent duplicate reaction output for the same source object, they do not stop you from adding
 * a second, independent one by hand. Use {@code --reset} for a repeatable demo.
 */
public final class CombustionVSUMExample {

  /** Where the V-SUM persists its models. Relative to the module's working directory. */
  private static final Path STORAGE_FOLDER = Path.of("target/vsum-example-run");

  private CombustionVSUMExample() {}

  public static void main(String[] args) throws IOException {
    Arguments arguments = Arguments.parse(args);

    if (arguments.reset) {
      deleteRecursively(STORAGE_FOLDER);
      System.out.println("Storage folder reset.");
    }

    // EMF needs to be told how to read/write files with our custom extensions (.racecar,
    // .combustionracecar). Without this, persisting anything fails.
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());

    System.out.println("Storage folder: " + STORAGE_FOLDER.toAbsolutePath());
    System.out.println();

    InternalVirtualModel vsum =
        new VirtualModelBuilder()
            .withStorageFolder(STORAGE_FOLDER)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(new TestUserInteraction()))
            .withChangePropagationSpecifications(
                new Racecar2combustionChangePropagationSpecification())
            .buildAndInitialize();
    vsum.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);

    // --- step 1: CREATE a fully populated RaceCar as a new root object -------------------------
    section("1. create RaceCar (+ chassis, 2 axles, 4 wheels)");
    modifyView(
        checkOutView(vsum, List.of(RaceCar.class)),
        (CommittableView v) -> {
          RaceCar raceCar = RacecarFactory.eINSTANCE.createRaceCar();
          raceCar.setVehicleId(arguments.vehicleId);
          raceCar.setName(arguments.name);
          raceCar.setPropulsionKind(PropulsionKind.COMBUSTION);
          raceCar.setRatedPower(arguments.ratedPower);
          raceCar.setTotalMass(arguments.totalMass);

          raceCar.setChassis(buildChassis());
          raceCar.getAxles().add(buildAxle(Position.FRONT, false, "front", 660.0, 305.0, 9.5));
          raceCar.getAxles().add(buildAxle(Position.REAR, true, "rear", 660.0, 380.0, 10.2));

          v.registerRoot(
              raceCar,
              URI.createFileURI(STORAGE_FOLDER.toAbsolutePath() + "/example.racecar"));

          System.out.println("created RaceCar " + raceCar.getVehicleId()
              + " (" + raceCar.getAxles().size() + " axles, "
              + raceCar.getAxles().stream().mapToInt(a -> a.getWheels().size()).sum()
              + " wheels)");
        });

    // --- step 2: CREATE the powertrain slot -> triggers powertrain + children ------------------
    section("2. set PowertrainSlot -> triggers CombustionPowertrain creation");
    modifyView(
        checkOutView(vsum, List.of(RaceCar.class)),
        (CommittableView v) -> {
          RaceCar raceCar = v.getRootObjects(RaceCar.class).iterator().next();

          PowertrainSlot slot = RacecarFactory.eINSTANCE.createPowertrainSlot();
          slot.setRequiredType(PropulsionKind.COMBUSTION);
          slot.setMountInterface("MI-V6");
          raceCar.setPowertrainSlot(slot);

          System.out.println("set PowertrainSlot with mountInterface=" + slot.getMountInterface());
        });
    printCombustionState(vsum, "after step 2");

    // --- step 2b: author combustion-INTRINSIC data directly, on the combustion side ------------
    //
    // cylinderCount, displacement, fuel capacity, exhaust backPressure etc. have no equivalent
    // anywhere in racecar.ecore -- there is nothing for a reaction to react to, and there
    // shouldn't be: racecar.ecore is the metamodel shared with the electric case study, so
    // combustion-only concepts must not leak into it. This data is therefore not reaction-derived;
    // it is authored directly on the combustion model, through a view of the combustion side,
    // exactly like an engineer filling in engine specs that racecar simply has no concept of.
    section("2b. author combustion-intrinsic data (no racecar equivalent exists)");
    modifyView(
        checkOutView(vsum, List.of(CombustionRaceCar.class)),
        (CommittableView v) -> {
          var powertrain = v.getRootObjects(CombustionRaceCar.class).iterator().next().getPowertrain();

          powertrain.setPowertrainId("PT-" + arguments.vehicleId);
          powertrain.setMass(78.0);

          powertrain.getEngine().setMass(112.0);
          powertrain.getEngine().setDisplacement(3.0);
          powertrain.getEngine().setCylinderCount(6);

          powertrain.getFuelTank().setCapacity(110.0);
          powertrain.getFuelTank().setMass(4.5);

          powertrain.getExhaustSystem().setMass(18.0);
          powertrain.getExhaustSystem().setBackPressure(1.35);

          System.out.println("authored: cylinderCount=" + powertrain.getEngine().getCylinderCount()
              + ", displacement=" + powertrain.getEngine().getDisplacement()
              + "L, fuelTank.capacity=" + powertrain.getFuelTank().getCapacity() + "L");
        });
    printCombustionState(vsum, "after step 2b");

    // --- step 3: UPDATE name + ratedPower together in one commit -------------------------------
    section("3. update: rename + change ratedPower");
    modifyView(
        checkOutView(vsum, List.of(RaceCar.class)),
        (CommittableView v) -> {
          RaceCar raceCar = v.getRootObjects(RaceCar.class).iterator().next();
          System.out.println("before: name=" + raceCar.getName()
              + ", ratedPower=" + raceCar.getRatedPower());

          raceCar.setName(raceCar.getName() + " (Updated)");
          raceCar.setRatedPower(raceCar.getRatedPower() + 50.0);

          System.out.println("after:  name=" + raceCar.getName()
              + ", ratedPower=" + raceCar.getRatedPower());
        });
    printCombustionState(vsum, "after step 3");

    // --- step 4: DELETE the powertrain slot -> combustion powertrain must disappear ------------
    section("4. delete PowertrainSlot -> CombustionPowertrain must be removed");
    modifyView(
        checkOutView(vsum, List.of(RaceCar.class)),
        (CommittableView v) -> {
          v.getRootObjects(RaceCar.class).iterator().next().setPowertrainSlot(null);
          System.out.println("removed PowertrainSlot");
        });
    printCombustionState(vsum, "after step 4");

    System.out.println();
    System.out.println("Inspect the persisted files under " + STORAGE_FOLDER.toAbsolutePath());

    vsum.dispose();
  }

  // ---------------------------------------------------------------- model construction helpers

  private static Chassis buildChassis() {
    Chassis chassis = RacecarFactory.eINSTANCE.createChassis();
    chassis.setTypeId("CH-MONOCOQUE-01");
    chassis.setMountInterface("STD-CHASSIS-MOUNT-A");
    chassis.getMass().add(102.5); // Chassis.mass is list-valued (upperBound="-1" in racecar.ecore)
    return chassis;
  }

  private static Axle buildAxle(
      Position position, boolean driven, String label, double diameter, double width, double wheelMass) {
    Axle axle = RacecarFactory.eINSTANCE.createAxle();
    axle.setPosition(position);
    axle.setDriven(driven);
    axle.getWheels().add(buildWheel(label + "-left", diameter, width, wheelMass));
    axle.getWheels().add(buildWheel(label + "-right", diameter, width, wheelMass));
    return axle;
  }

  private static Wheel buildWheel(String typeId, double diameter, double width, double mass) {
    Wheel wheel = RacecarFactory.eINSTANCE.createWheel();
    wheel.setTypeId("W-" + typeId);
    // Wheel.diameter / width / mass are all list-valued (upperBound="-1"), no setters exist.
    wheel.getDiameter().add(diameter);
    wheel.getWidth().add(width);
    wheel.getMass().add(mass);
    return wheel;
  }

  // ---------------------------------------------------------------------------- V-SUM plumbing

  private static View checkOutView(InternalVirtualModel vsum, List<Class<?>> rootTypes) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().stream()
        .filter(element -> rootTypes.stream().anyMatch(type -> type.isInstance(element)))
        .forEach(element -> selector.setSelected(element, true));
    return selector.createView();
  }

  private static void modifyView(View view, Consumer<CommittableView> change) {
    CommittableView committableView = view.withChangeDerivingTrait();
    change.accept(committableView);
    committableView.commitChanges();
  }

  /** Checks out a fresh view and prints the current state of the combustion model, if any. */
  private static void printCombustionState(InternalVirtualModel vsum, String label) {
    View view = checkOutView(vsum, List.of(RaceCar.class, CombustionRaceCar.class));
    RaceCar raceCar = view.getRootObjects(RaceCar.class).iterator().next();

    var combustionCars = view.getRootObjects(CombustionRaceCar.class);
    if (combustionCars.isEmpty()) {
      System.out.println(label + ": no CombustionRaceCar found");
      return;
    }

    CombustionRaceCar combustionRaceCar = combustionCars.iterator().next();
    System.out.println(label + ": racecar[" + raceCar.getVehicleId() + " / " + raceCar.getName()
        + "] <-> combustion[" + combustionRaceCar.getVehicleId()
        + " / " + combustionRaceCar.getName() + "]");

    if (combustionRaceCar.getPowertrain() == null) {
      System.out.println("  powertrain: (none)");
      return;
    }

    var powertrain = combustionRaceCar.getPowertrain();
    System.out.println("  [reaction-derived, traceable back to racecar]");
    System.out.println("    powertrainId:              " + powertrain.getPowertrainId());
    System.out.println("    mountInterface:             " + powertrain.getMountInterface());
    System.out.println("    engine.maxPower:            " + powertrain.getEngine().getMaxPower());

    System.out.println("  [combustion-intrinsic, authored directly, no racecar equivalent]");
    System.out.println("    engine.displacement:        " + powertrain.getEngine().getDisplacement());
    System.out.println("    engine.cylinderCount:       " + powertrain.getEngine().getCylinderCount());
    System.out.println("    fuelTank.capacity:          " + powertrain.getFuelTank().getCapacity());
    System.out.println("    exhaustSystem.backPressure: " + powertrain.getExhaustSystem().getBackPressure());
  }

  private static void section(String title) {
    System.out.println();
    System.out.println("--- " + title + " ---");
  }

  // -------------------------------------------------------------------------------- arguments

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  private static final class Arguments {
    boolean reset = false;
    String vehicleId = "RC-DEMO-01";
    String name = "Demo Combustion Car";
    double ratedPower = 560.0;
    double totalMass = 798.0;

    static Arguments parse(String[] args) {
      Arguments result = new Arguments();
      for (String arg : args) {
        if (arg.equals("--reset")) {
          result.reset = true;
        } else if (arg.startsWith("--vehicle-id=")) {
          result.vehicleId = arg.substring("--vehicle-id=".length());
        } else if (arg.startsWith("--name=")) {
          result.name = arg.substring("--name=".length());
        } else if (arg.startsWith("--rated-power=")) {
          result.ratedPower = Double.parseDouble(arg.substring("--rated-power=".length()));
        } else if (arg.startsWith("--total-mass=")) {
          result.totalMass = Double.parseDouble(arg.substring("--total-mass=".length()));
        } else {
          System.out.println("Unknown argument, ignoring: " + arg);
        }
      }
      return result;
    }
  }
}
