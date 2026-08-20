package kit.sdq.kastel.vitruvius.casestudy.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kit.sdq.kastel.vitruvius.casestudy.model.electricalracecar.ElectricalRaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Axle;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.emf.common.util.URI;

import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;

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
}
