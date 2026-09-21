package kit.sdq.kastel.vitruvius.casestudy.vsum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import mir.reactions.electrical2racecar.Electrical2racecarChangePropagationSpecification;
import mir.reactions.racecar2electrical.Racecar2electricalChangePropagationSpecification;
import mir.reactions.electricalInternal.ElectricalInternalChangePropagationSpecification;
import mir.reactions.racecarInternal.RacecarInternalChangePropagationSpecification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import tools.vitruv.change.interaction.CliInteractionResultProviderImpl;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.runtime.correspondence.CorrespondencePackage;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/** Creates and opens the VSUM used by the race-car case study. */
public final class RaceCarVsum {

  private RaceCarVsum() {}

  public static InternalVirtualModel create(Path storageFolder)
      throws IOException {

    return create(
        storageFolder,
        new CliInteractionResultProviderImpl()
    );
  }

  public static InternalVirtualModel create(
      Path storageFolder,
      InteractionResultProvider interactionResultProvider
  ) throws IOException {

    Path absoluteStorageFolder =
        storageFolder.toAbsolutePath().normalize();

    Files.createDirectories(
        absoluteStorageFolder.resolve("models/electrical")
    );

    Files.createDirectories(
        absoluteStorageFolder.resolve("models/combustion")
    );

    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());

    CorrespondencePackage.eINSTANCE.eClass();

    InternalVirtualModel vsum =
        new VirtualModelBuilder()
            .withStorageFolder(absoluteStorageFolder)
            .withUserInteractorForResultProvider(interactionResultProvider)
            .withChangePropagationSpecifications(
                new Racecar2electricalChangePropagationSpecification(),
                new ElectricalInternalChangePropagationSpecification(),
                new Electrical2racecarChangePropagationSpecification(),
                new RacecarInternalChangePropagationSpecification()
            )
            .buildAndInitialize();

    vsum.setChangePropagationMode(
        ChangePropagationMode.TRANSITIVE_CYCLIC
    );

    return vsum;
  }

  public static CommittableView createCommittableIdentityView(VirtualModel vsum) {
    return createIdentityView(vsum).withChangeDerivingTrait();
  }

  public static View createIdentityView(VirtualModel vsum, Class<?>... rootTypes) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().stream()
        .filter(
            element ->
                rootTypes.length == 0
                    || Arrays.stream(rootTypes).anyMatch(type -> type.isInstance(element)))
        .forEach(element -> selector.setSelected(element, true));
    return selector.createView();
  }


}
