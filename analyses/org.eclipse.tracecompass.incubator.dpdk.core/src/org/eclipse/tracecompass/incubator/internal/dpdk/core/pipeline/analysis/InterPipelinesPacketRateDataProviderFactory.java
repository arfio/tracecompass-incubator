package org.eclipse.tracecompass.incubator.internal.dpdk.core.pipeline.analysis;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor.ProviderType;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
import org.eclipse.tracecompass.tmf.core.model.DataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.TmfTreeXYCompositeDataProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * Inter-Pipeline packet rate data provider Factory
 *
 * @author Adel Belkhiri
 */
public class InterPipelinesPacketRateDataProviderFactory implements IDataProviderFactory {

    private static final IDataProviderDescriptor DESCRIPTOR = new DataProviderDescriptor.Builder()
            .setId(InterPipelinesPacketRateDataProvider.ID)
            .setName("Inter-Pipeline packet rate data provider") //$NON-NLS-1$
            .setDescription("Inter-Pipeline packet rate data provider") //$NON-NLS-1$
            .setProviderType(ProviderType.TREE_TIME_XY)
            .build();

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(@NonNull ITmfTrace trace) {
        Collection<ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
        if (traces.size() == 1) {
            return InterPipelinesPacketRateDataProvider.create(trace);
        }
        return TmfTreeXYCompositeDataProvider.create(traces, InterPipelinesPacketRateDataProvider.PROVIDER_TITLE, InterPipelinesPacketRateDataProvider.ID);
    }

    @Override
    public Collection<IDataProviderDescriptor> getDescriptors(@NonNull ITmfTrace trace) {
        DpdkPipelineAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, DpdkPipelineAnalysisModule.class, DpdkPipelineAnalysisModule.ID);
        return module != null ? Collections.singletonList(DESCRIPTOR) : Collections.emptyList();
    }
}
