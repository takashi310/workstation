package org.janelia.jacs2.asyncservice.sampleprocessing;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.CollectionUtils;
import org.janelia.jacs2.cdi.qualifier.PropertyValue;
import org.janelia.jacs2.model.jacsservice.JacsServiceData;
import org.janelia.jacs2.model.jacsservice.JacsServiceDataBuilder;
import org.janelia.jacs2.dataservice.persistence.JacsServiceDataPersistence;
import org.janelia.jacs2.asyncservice.common.AbstractServiceProcessor;
import org.janelia.jacs2.asyncservice.common.ComputationException;
import org.janelia.jacs2.asyncservice.common.JacsServiceDispatcher;
import org.janelia.jacs2.asyncservice.common.ServiceComputation;
import org.janelia.jacs2.asyncservice.common.ServiceComputationFactory;
import org.janelia.jacs2.asyncservice.common.ServiceDataUtils;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GetSampleLsmsMetadataProcessor extends AbstractServiceProcessor<List<File>> {

    @Inject
    GetSampleLsmsMetadataProcessor(JacsServiceDispatcher jacsServiceDispatcher,
                                   ServiceComputationFactory computationFactory,
                                   JacsServiceDataPersistence jacsServiceDataPersistence,
                                   @PropertyValue(name = "service.DefaultWorkingDir") String defaultWorkingDir,
                                   Logger logger) {
        super(jacsServiceDispatcher, computationFactory, jacsServiceDataPersistence, defaultWorkingDir, logger);
    }

    @Override
    public List<File> getResult(JacsServiceData jacsServiceData) {
        return ServiceDataUtils.stringToFileList(jacsServiceData.getStringifiedResult());
    }

    @Override
    public void setResult(List<File> result, JacsServiceData jacsServiceData) {
        jacsServiceData.setStringifiedResult(ServiceDataUtils.fileListToString(result));
    }

    @Override
    protected ServiceComputation<List<SampleImageFile>> preProcessData(JacsServiceData jacsServiceData) {
        JacsServiceData sampleLSMsServiceData = SampleServicesUtils.createChildSampleServiceData("getSampleImageFiles", getArgs(jacsServiceData), jacsServiceData);
        return submitServiceDependency(jacsServiceData, sampleLSMsServiceData)
                .thenCompose(sd -> this.waitForCompletion(sd))
                .thenApply(r -> ServiceDataUtils.stringToAny(sampleLSMsServiceData.getStringifiedResult(), new TypeReference<List<SampleImageFile>>() {}));
    }

    @Override
    protected ServiceComputation<List<File>> localProcessData(Object preProcessingResult, JacsServiceData jacsServiceData) {
        SampleServiceArgs args = getArgs(jacsServiceData);
        List<SampleImageFile> sampleLSMs = (List<SampleImageFile>) preProcessingResult;
        if (CollectionUtils.isEmpty(sampleLSMs)) {
            return computationFactory.newFailedComputation(new ComputationException(jacsServiceData, "No sample image file was found"));
        }
        List<ServiceComputation<?>> lsmMetadataComputations = submitAllLSMMetadataServices(sampleLSMs, args.sampleDataDir, jacsServiceData);
        return computationFactory.newCompletedComputation(jacsServiceData)
                .thenCombineAll(lsmMetadataComputations, (sd, sampleLSMMetadataResults) -> sampleLSMMetadataResults.stream().map(r -> (File) r).collect(Collectors.toList()))
                .thenApply(results -> this.applyResult(results, jacsServiceData));
    }

    @Override
    protected boolean isResultAvailable(Object preProcessingResult, JacsServiceData jacsServiceData) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<File> retrieveResult(Object preProcessingResult, JacsServiceData jacsServiceData) {
        throw new UnsupportedOperationException();
    }

    private List<ServiceComputation<?>> submitAllLSMMetadataServices(List<SampleImageFile> lsmFiles, String outputDir, JacsServiceData jacsServiceData) {
        List<ServiceComputation<?>> lsmMetadataComputations = new ArrayList<>();
        lsmFiles.forEach(f -> {
            File lsmMetadataFile = getOutputFileName(outputDir, new File(f.getWorkingFilePath()));
            JacsServiceData extractLsmMetadataService =
                    new JacsServiceDataBuilder(jacsServiceData)
                            .setName("lsmFileMetadata")
                            .addArg("-inputLSM", f.getWorkingFilePath())
                            .addArg("-outputLSMMetadata", lsmMetadataFile.getAbsolutePath())
                            .build();
            lsmMetadataComputations.add(
                    this.submitServiceDependency(jacsServiceData, extractLsmMetadataService)
                            .thenCompose(sd -> this.waitForCompletion(sd))
            );
        });
        return lsmMetadataComputations;
    }

    private SampleServiceArgs getArgs(JacsServiceData jacsServiceData) {
        return SampleServiceArgs.parse(jacsServiceData.getArgsArray(), new SampleServiceArgs());
    }

    private File getOutputFileName(String outputDir, File inputFile) {
        return new File(outputDir, inputFile.getName().replaceAll("\\s+", "_") + ".json");
    }
}