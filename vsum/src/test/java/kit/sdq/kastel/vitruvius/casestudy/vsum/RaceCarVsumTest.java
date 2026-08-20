package kit.sdq.kastel.vitruvius.casestudy.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.emf.common.util.URI;

import kit.sdq.kastel.vitruvius.casestudy.model.electricalracecar.ElectricalRaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Axle;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Position;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PowertrainSlot;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;

import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

class RaceCarVsumTest {

    @Test
    void createsElectricalModelForElectricRaceCar(@TempDir Path storageFolder)
            throws IOException {
        var vsum = RaceCarVsum.create(storageFolder);

        try {
            RaceCar sourceRaceCar = createElectricRaceCar();
            var writeView = RaceCarVsum.createCommittableIdentityView(vsum);

            writeView.registerRoot(
                sourceRaceCar,
                URI.createFileURI(
                    storageFolder.resolve("RC-001.racecar").toString()
                )
            );
            writeView.commitChanges();

            var electricalView = RaceCarVsum.createIdentityView(
                vsum,
                ElectricalRaceCar.class
            );

            assertEquals(
                1,
                electricalView.getRootObjects(ElectricalRaceCar.class).size()
            );

            ElectricalRaceCar electricalRaceCar = electricalView
                .getRootObjects(ElectricalRaceCar.class)
                .iterator()
                .next();

            assertEquals("RC-001", electricalRaceCar.getVehicleId());
            assertNotNull(electricalRaceCar.getPowertrain());
            assertEquals(250.0, electricalRaceCar.getPowertrain().getMaxPower());
            assertEquals(
                "ELECTRIC-MOUNT",
                electricalRaceCar.getPowertrain().getMountInterface()
            );
            assertNotNull(electricalRaceCar.getPowertrain().getBattery());
            assertNotNull(electricalRaceCar.getPowertrain().getMotor());
            assertNotNull(electricalRaceCar.getPowertrain().getInverter());

            assertTrue(
                Files.exists(
                    storageFolder.resolve(
                        "models/electrical/RC-001.electricalracecar"
                    )
                )
            );
        } finally {
            vsum.dispose();
        }
    }

    private RaceCar createElectricRaceCar() {
        RacecarFactory factory = RacecarFactory.eINSTANCE;
        RaceCar raceCar =
            factory.createRaceCar();

        raceCar.setVehicleId("RC-001");
        raceCar.setName("Electric Race Car");
        raceCar.setPropulsionKind(PropulsionKind.ELECTRIC);
        raceCar.setRatedPower(250.0);
        raceCar.setTotalMass(1200.0);

        var chassis = factory.createChassis();
        chassis.setTypeId("CHASSIS-001");
        chassis.setMountInterface("ELECTRIC-MOUNT");
        raceCar.setChassis(chassis);

        var powertrainSlot = factory.createPowertrainSlot();
        powertrainSlot.setRequiredType(PropulsionKind.ELECTRIC);
        powertrainSlot.setMountInterface("ELECTRIC-MOUNT");
        raceCar.setPowertrainSlot(powertrainSlot);

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
        axle.getWheels().add(factory.createWheel());
        axle.getWheels().add(factory.createWheel());
        return axle;
    }

    @Test
    void deletingPowertrainSlotRemovesElectricPowertrain(
        @TempDir Path tempDir
    ) throws IOException {

        InternalVirtualModel vsum =
            RaceCarVsum.create(tempDir);

        try {
            // 1. Create RaceCar
            CommittableView createView =
                RaceCarVsum.createCommittableIdentityView(vsum);

            RaceCar raceCar =
                RacecarFactory.eINSTANCE.createRaceCar();

            raceCar.setVehicleId("RC-DELETE-01");
            raceCar.setName("Deletion Test Car");
            raceCar.setPropulsionKind(PropulsionKind.ELECTRIC);
            raceCar.setRatedPower(300.0);
            raceCar.setTotalMass(800.0);

            createView.registerRoot(
                raceCar,
                URI.createFileURI(
                    tempDir
                        .resolve("RC-DELETE-01.racecar")
                        .toAbsolutePath()
                        .toString()
                )
            );

            createView.commitChanges();


            // 2. Add PowertrainSlot
            CommittableView addSlotView =
                RaceCarVsum
                    .createIdentityView(vsum, RaceCar.class)
                    .withChangeDerivingTrait();

            RaceCar storedRaceCar =
                addSlotView
                    .getRootObjects(RaceCar.class)
                    .iterator()
                    .next();

            PowertrainSlot slot =
                RacecarFactory.eINSTANCE.createPowertrainSlot();

            slot.setRequiredType(
                PropulsionKind.ELECTRIC
            );

            slot.setMountInterface(
                "MI-DELETE-TEST"
            );

            storedRaceCar.setPowertrainSlot(slot);

            addSlotView.commitChanges();


            // 3. Verify that the ElectricPowertrain exists
            View electricalViewBeforeDeletion =
                RaceCarVsum.createIdentityView(
                    vsum,
                    ElectricalRaceCar.class
                );

            ElectricalRaceCar electricalRaceCarBeforeDeletion =
                electricalViewBeforeDeletion
                    .getRootObjects(ElectricalRaceCar.class)
                    .iterator()
                    .next();

            assertNotNull(
                electricalRaceCarBeforeDeletion.getPowertrain()
            );


            // 4. Delete PowertrainSlot
            CommittableView deleteSlotView =
                RaceCarVsum
                    .createIdentityView(vsum, RaceCar.class)
                    .withChangeDerivingTrait();

            RaceCar raceCarForDeletion =
                deleteSlotView
                    .getRootObjects(RaceCar.class)
                    .iterator()
                    .next();

            raceCarForDeletion.setPowertrainSlot(null);

            deleteSlotView.commitChanges();


            // 5. Verify propagation
            View electricalViewAfterDeletion =
                RaceCarVsum.createIdentityView(
                    vsum,
                    ElectricalRaceCar.class
                );

            ElectricalRaceCar electricalRaceCarAfterDeletion =
                electricalViewAfterDeletion
                    .getRootObjects(ElectricalRaceCar.class)
                    .iterator()
                    .next();

            assertNull(
                electricalRaceCarAfterDeletion.getPowertrain()
            );

        } finally {
            vsum.dispose();
        }
    }
}
