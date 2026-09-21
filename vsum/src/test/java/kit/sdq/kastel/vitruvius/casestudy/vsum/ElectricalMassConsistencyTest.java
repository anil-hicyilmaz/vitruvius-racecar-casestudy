package kit.sdq.kastel.vitruvius.casestudy.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import kit.sdq.kastel.vitruvius.casestudy.model.electricalracecar.ElectricalRaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Axle;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Position;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

class ElectricalMassConsistencyTest {

  @Test
  void enduranceProfileInitializesComponentsAndMass(
      @TempDir Path storageFolder) throws IOException {
    TestContext context = createVsum(storageFolder, "Endurance");

    try {
      registerRaceCar(context.vsum(), storageFolder);

      ElectricalRaceCar electricalRaceCar = electricalRaceCar(context.vsum());
      var powertrain = electricalRaceCar.getPowertrain();

      assertEquals(100.0, powertrain.getBattery().getCapacity());
      assertEquals(420.0, powertrain.getBattery().getMass());
      assertEquals(55.0, powertrain.getMotor().getMass());
      assertEquals(20.0, powertrain.getInverter().getMass());
      assertEquals(495.0, powertrain.getMass());
      assertEquals(1043.0, raceCar(context.vsum()).getTotalMass());
      context.interactions().assertAllInteractionsOccurred();
    } finally {
      context.vsum().dispose();
    }
  }

  @Test
  void changingBatteryMassRecalculatesPowertrainAndRaceCarMass(
      @TempDir Path storageFolder) throws IOException {
    TestContext context = createVsum(storageFolder, "Performance");

    try {
      registerRaceCar(context.vsum(), storageFolder);

      CommittableView electricalWriteView = RaceCarVsum
          .createIdentityView(context.vsum(), ElectricalRaceCar.class)
          .withChangeDerivingTrait();

      ElectricalRaceCar electricalRaceCar = electricalWriteView
          .getRootObjects(ElectricalRaceCar.class)
          .iterator()
          .next();

      electricalRaceCar.getPowertrain().getBattery().setMass(350.0);
      electricalWriteView.commitChanges();

      assertEquals(440.0, electricalRaceCar(context.vsum()).getPowertrain().getMass());
      assertEquals(988.0, raceCar(context.vsum()).getTotalMass());
      context.interactions().assertAllInteractionsOccurred();
    } finally {
      context.vsum().dispose();
    }
  }

  @Test
  void changingChassisAndWheelMassRecalculatesRaceCarMass(
      @TempDir Path storageFolder) throws IOException {
    TestContext context = createVsum(storageFolder, "Performance");

    try {
      registerRaceCar(context.vsum(), storageFolder);

      CommittableView raceCarWriteView = RaceCarVsum
          .createIdentityView(context.vsum(), RaceCar.class)
          .withChangeDerivingTrait();

      RaceCar raceCar = raceCarWriteView
          .getRootObjects(RaceCar.class)
          .iterator()
          .next();

      raceCar.getChassis().setMass(520.0);
      raceCar.getAxles().get(0).getWheels().get(0).setMass(15.0);
      raceCarWriteView.commitChanges();

      assertEquals(991.0, raceCar(context.vsum()).getTotalMass());
      context.interactions().assertAllInteractionsOccurred();
    } finally {
      context.vsum().dispose();
    }
  }

  private TestContext createVsum(Path storageFolder, String profile)
      throws IOException {
    TestUserInteraction interactions = new TestUserInteraction();
    interactions
        .onNextMultipleChoiceSingleSelection()
        .respondWith(profile);

    InternalVirtualModel vsum = RaceCarVsum.create(
        storageFolder,
        new TestUserInteraction.ResultProvider(interactions)
    );

    return new TestContext(vsum, interactions);
  }

  private void registerRaceCar(
      InternalVirtualModel vsum,
      Path storageFolder) {
    CommittableView view = RaceCarVsum.createCommittableIdentityView(vsum);
    view.registerRoot(
        createElectricRaceCar(),
        URI.createFileURI(
            storageFolder.resolve("RC-MASS.racecar")
                .toAbsolutePath()
                .toString()
        )
    );
    view.commitChanges();
  }

  private RaceCar createElectricRaceCar() {
    RacecarFactory factory = RacecarFactory.eINSTANCE;
    RaceCar raceCar = factory.createRaceCar();
    raceCar.setVehicleId("RC-MASS");
    raceCar.setName("Electrical mass test car");
    raceCar.setPropulsionKind(PropulsionKind.ELECTRIC);
    raceCar.setRatedPower(250.0);

    var chassis = factory.createChassis();
    chassis.setTypeId("CHASSIS-MASS");
    chassis.setMass(500.0);
    chassis.setMountInterface("ELECTRIC-MOUNT");
    raceCar.setChassis(chassis);

    var slot = factory.createPowertrainSlot();
    slot.setRequiredType(PropulsionKind.ELECTRIC);
    slot.setMountInterface("ELECTRIC-MOUNT");
    raceCar.setPowertrainSlot(slot);

    raceCar.getAxles().add(createAxle(factory, Position.FRONT, true));
    raceCar.getAxles().add(createAxle(factory, Position.REAR, false));
    return raceCar;
  }

  private Axle createAxle(
      RacecarFactory factory,
      Position position,
      boolean driven) {
    Axle axle = factory.createAxle();
    axle.setPosition(position);
    axle.setDriven(driven);

    var firstWheel = factory.createWheel();
    firstWheel.setMass(12.0);
    axle.getWheels().add(firstWheel);

    var secondWheel = factory.createWheel();
    secondWheel.setMass(12.0);
    axle.getWheels().add(secondWheel);
    return axle;
  }

  private ElectricalRaceCar electricalRaceCar(InternalVirtualModel vsum) {
    return RaceCarVsum
        .createIdentityView(vsum, ElectricalRaceCar.class)
        .getRootObjects(ElectricalRaceCar.class)
        .iterator()
        .next();
  }

  private RaceCar raceCar(InternalVirtualModel vsum) {
    return RaceCarVsum
        .createIdentityView(vsum, RaceCar.class)
        .getRootObjects(RaceCar.class)
        .iterator()
        .next();
  }

  private record TestContext(
      InternalVirtualModel vsum,
      TestUserInteraction interactions
  ) {}
}
