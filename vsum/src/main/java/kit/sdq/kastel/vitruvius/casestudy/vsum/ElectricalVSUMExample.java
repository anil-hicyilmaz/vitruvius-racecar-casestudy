package kit.sdq.kastel.vitruvius.casestudy.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

import org.eclipse.emf.common.util.URI;

import kit.sdq.kastel.vitruvius.casestudy.model.electricalracecar.ElectricalRaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;

import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public final class ElectricalVSUMExample {

  private static final Path STORAGE_FOLDER =
      Path.of("target/vsum-electrical-example");

  private ElectricalVSUMExample() {}

  public static void main(String[] args) throws IOException {

    InternalVirtualModel vsum =
        RaceCarVsum.create(STORAGE_FOLDER);

    try {

      pause("DEMO 1 - Create RaceCar");
      createRaceCar(vsum);

      pause("DEMO 2 - Add PowertrainSlot");
      addPowertrainSlot(vsum);

      pause("DEMO 3 - Add electrical-specific data");
      addElectricalData(vsum);

      pause("DEMO 4 - Electrical -> RaceCar propagation");
      updateElectricalPower(vsum);

      pause("DEMO 5 - RaceCar -> Electrical propagation");
      updateRaceCarPower(vsum);

      pause("DEMO 6 - Update mountInterface");
      updateMountInterface(vsum);

      pause("DEMO 7 - Delete PowertrainSlot");
      removePowertrainSlot(vsum);

      System.out.println();
      System.out.println(
          "Storage folder: " +
              STORAGE_FOLDER.toAbsolutePath()
      );

    } finally {
      vsum.dispose();
    }
  }

  private static final Scanner SCANNER =
      new Scanner(System.in);

  private static void pause(String title) {

    System.out.println();
    System.out.println("========================================");
    System.out.println(title);
    System.out.println("Press ENTER to continue...");
    System.out.println("========================================");

    SCANNER.nextLine();
  }

  private static void createRaceCar(
      InternalVirtualModel vsum
  ) {

    CommittableView view =
        RaceCarVsum.createCommittableIdentityView(vsum);

    RaceCar raceCar =
        RacecarFactory.eINSTANCE.createRaceCar();

    raceCar.setVehicleId("RC-ELECTRIC-01");
    raceCar.setName("Demo Electric Race Car");
    raceCar.setPropulsionKind(PropulsionKind.ELECTRIC);
    raceCar.setRatedPower(300.0);
    raceCar.setTotalMass(800.0);

    view.registerRoot(
        raceCar,
        URI.createFileURI(
            STORAGE_FOLDER
                .resolve("RC-ELECTRIC-01.racecar")
                .toAbsolutePath()
                .toString()
        )
    );

    view.commitChanges();

    System.out.println(
        "Created RaceCar: " +
            raceCar.getVehicleId()
    );
  }

  private static void addPowertrainSlot(
      InternalVirtualModel vsum
  ) {

    CommittableView view =
        RaceCarVsum.createCommittableIdentityView(vsum);

    RaceCar raceCar =
        view.getRootObjects(RaceCar.class)
            .iterator()
            .next();

    var slot =
        RacecarFactory.eINSTANCE.createPowertrainSlot();

    slot.setRequiredType(
        PropulsionKind.ELECTRIC
    );

    slot.setMountInterface(
        "MI-ELECTRIC-01"
    );

    raceCar.setPowertrainSlot(slot);

    view.commitChanges();

    System.out.println(
        "Added PowertrainSlot: " +
            slot.getMountInterface()
    );
  }

  private static void addElectricalData(
      InternalVirtualModel vsum
  ) {

    CommittableView view =
        RaceCarVsum
            .createIdentityView(
                vsum,
                ElectricalRaceCar.class
            )
            .withChangeDerivingTrait();

    ElectricalRaceCar electricalRaceCar =
        view.getRootObjects(ElectricalRaceCar.class)
            .iterator()
            .next();

    var powertrain =
        electricalRaceCar.getPowertrain();

    powertrain.getBattery().setCapacity(80.0);
    powertrain.getBattery().setMass(350.0);

    powertrain.getMotor().setMass(45.0);
    powertrain.getMotor().setMaxPower(300.0);

    powertrain.getInverter().setMass(15.0);
    powertrain.getInverter().setMaxPower(320.0);

    view.commitChanges();

    System.out.println(
        "Authored electrical-specific data:"
            + " battery=" + powertrain.getBattery().getCapacity() + " kWh"
            + ", motor=" + powertrain.getMotor().getMaxPower()
            + ", inverter=" + powertrain.getInverter().getMaxPower()
    );
  }

  private static void updateElectricalPower(
      InternalVirtualModel vsum
  ) {

    CommittableView electricalView =
        RaceCarVsum
            .createIdentityView(
                vsum,
                ElectricalRaceCar.class
            )
            .withChangeDerivingTrait();

    ElectricalRaceCar electricalRaceCar =
        electricalView
            .getRootObjects(ElectricalRaceCar.class)
            .iterator()
            .next();

    var powertrain =
        electricalRaceCar.getPowertrain();

    System.out.println(
        "Electrical power BEFORE: "
            + powertrain.getMaxPower()
    );

    powertrain.setMaxPower(350.0);

    System.out.println(
        "Electrical power BEFORE COMMIT: "
            + powertrain.getMaxPower()
    );

    electricalView.commitChanges();

    View raceCarView =
        RaceCarVsum.createIdentityView(
            vsum,
            RaceCar.class
        );

    RaceCar raceCar =
        raceCarView
            .getRootObjects(RaceCar.class)
            .iterator()
            .next();

    System.out.println(
        "Electrical power AFTER COMMIT: "
            + powertrain.getMaxPower()
    );

    System.out.println(
        "RaceCar ratedPower AFTER propagation: "
            + raceCar.getRatedPower()
    );
  }

  private static void updateRaceCarPower(
      InternalVirtualModel vsum
  ) {

    CommittableView raceCarView =
        RaceCarVsum
            .createIdentityView(
                vsum,
                RaceCar.class
            )
            .withChangeDerivingTrait();

    RaceCar raceCar =
        raceCarView
            .getRootObjects(RaceCar.class)
            .iterator()
            .next();

    System.out.println(
        "RaceCar power BEFORE: "
            + raceCar.getRatedPower()
    );

    raceCar.setRatedPower(400.0);

    System.out.println(
        "RaceCar power BEFORE COMMIT: "
            + raceCar.getRatedPower()
    );

    raceCarView.commitChanges();

    View electricalView =
        RaceCarVsum.createIdentityView(
            vsum,
            ElectricalRaceCar.class
        );

    ElectricalRaceCar electricalRaceCar =
        electricalView
            .getRootObjects(ElectricalRaceCar.class)
            .iterator()
            .next();

    System.out.println(
        "RaceCar power AFTER COMMIT: "
            + raceCar.getRatedPower()
    );

    System.out.println(
        "Electrical maxPower AFTER propagation: "
            + electricalRaceCar
            .getPowertrain()
            .getMaxPower()
    );
  }

  private static void updateMountInterface(
      InternalVirtualModel vsum
  ) {

    CommittableView raceCarView =
        RaceCarVsum
            .createIdentityView(
                vsum,
                RaceCar.class
            )
            .withChangeDerivingTrait();

    RaceCar raceCar =
        raceCarView
            .getRootObjects(RaceCar.class)
            .iterator()
            .next();

    var slot = raceCar.getPowertrainSlot();

    System.out.println(
        "Mount interface BEFORE: "
            + slot.getMountInterface()
    );

    slot.setMountInterface(
        "MI-ELECTRIC-02"
    );

    System.out.println(
        "Mount interface BEFORE COMMIT: "
            + slot.getMountInterface()
    );

    raceCarView.commitChanges();

    View electricalView =
        RaceCarVsum.createIdentityView(
            vsum,
            ElectricalRaceCar.class
        );

    ElectricalRaceCar electricalRaceCar =
        electricalView
            .getRootObjects(ElectricalRaceCar.class)
            .iterator()
            .next();

    System.out.println(
        "RaceCar mount interface AFTER COMMIT: "
            + raceCar
            .getPowertrainSlot()
            .getMountInterface()
    );

    System.out.println(
        "Electrical mount interface AFTER propagation: "
            + electricalRaceCar
            .getPowertrain()
            .getMountInterface()
    );
  }

  private static void removePowertrainSlot(
      InternalVirtualModel vsum
  ) {

    CommittableView raceCarView =
        RaceCarVsum
            .createIdentityView(
                vsum,
                RaceCar.class
            )
            .withChangeDerivingTrait();

    RaceCar raceCar =
        raceCarView
            .getRootObjects(RaceCar.class)
            .iterator()
            .next();

    System.out.println(
        "PowertrainSlot BEFORE deletion: "
            + raceCar.getPowertrainSlot().getMountInterface()
    );

    raceCar.setPowertrainSlot(null);

    raceCarView.commitChanges();

    View electricalView =
        RaceCarVsum.createIdentityView(
            vsum,
            ElectricalRaceCar.class
        );

    ElectricalRaceCar electricalRaceCar =
        electricalView
            .getRootObjects(ElectricalRaceCar.class)
            .iterator()
            .next();

    System.out.println(
        "PowertrainSlot AFTER deletion: "
            + raceCar.getPowertrainSlot()
    );

    System.out.println(
        "Electrical powertrain AFTER propagation: "
            + electricalRaceCar.getPowertrain()
    );
  }
}