package kit.sdq.kastel.vitruvius.casestudy.vsum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import mir.reactions.racecar2electrical.Racecar2electricalChangePropagationSpecification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/** Creates and opens the VSUM used by the race-car case study. */
public final class RaceCarVsum {

  private RaceCarVsum() {}

  public static InternalVirtualModel create(Path storageFolder) throws IOException {
    Path absoluteStorageFolder = storageFolder.toAbsolutePath().normalize();
    Files.createDirectories(absoluteStorageFolder.resolve("models/electrical"));

    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("*", new XMIResourceFactoryImpl());

    // Required when an existing VSUM and its correspondence model are reloaded.
    tools.vitruv.dsls.reactions.runtime.correspondence.CorrespondencePackage.eINSTANCE.eClass();

    return new VirtualModelBuilder()
        .withStorageFolder(absoluteStorageFolder)
        .withUserInteractorForResultProvider(
            new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(
            new Racecar2electricalChangePropagationSpecification())
        .buildAndInitialize();
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
