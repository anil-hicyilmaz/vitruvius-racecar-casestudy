package kit.sdq.kastel.vitruvius.casestudy.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import kit.sdq.kastel.vitruvius.casestudy.model.racecar.PropulsionKind;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RaceCar;
import kit.sdq.kastel.vitruvius.casestudy.model.racecar.RacecarFactory;

class RaceCarVsumTest {

    @Test
    void createsElectricRaceCar() {
        RaceCar raceCar = createElectricRaceCar();

        assertEquals("RC-001", raceCar.getVehicleId());
        assertEquals(
            PropulsionKind.ELECTRIC,
            raceCar.getPropulsionKind()
        );
    }

    private RaceCar createElectricRaceCar() {
        RaceCar raceCar =
            RacecarFactory.eINSTANCE.createRaceCar();

        raceCar.setVehicleId("RC-001");
        raceCar.setName("Electric Race Car");
        raceCar.setPropulsionKind(PropulsionKind.ELECTRIC);
        raceCar.setRatedPower(250.0);
        raceCar.setTotalMass(1200.0);

        return raceCar;
    }
}