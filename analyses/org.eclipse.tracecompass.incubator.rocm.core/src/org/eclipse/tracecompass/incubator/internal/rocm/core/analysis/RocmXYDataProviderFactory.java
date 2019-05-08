package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.tmf.core.model.xy.TmfTreeXYCompositeDataProvider;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * @author Arnaud Fiorini
 *
 */
public class RocmXYDataProviderFactory implements IDataProviderFactory {

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(@NonNull ITmfTrace trace) {
        Collection<ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
        if (traces.size() == 1) {
            List<ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel>> dataProviders = new ArrayList<>();
            for (RocmCallStackAnalysis module : TmfTraceUtils.getAnalysisModulesOfClass(trace, RocmCallStackAnalysis.class)) {
                ITmfTrace subTrace = module.getTrace();
                ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> provider = new RocmXYDataProvider(Objects.requireNonNull(subTrace), module);
                dataProviders.add(provider);
            }
            if (dataProviders.isEmpty()) {
                return null;
            } else if (dataProviders.size() == 1) {
                return dataProviders.get(0);
            }
            return new TmfTreeXYCompositeDataProvider<>(dataProviders, "Counters", RocmXYDataProvider.ID);
        }

        return TmfTreeXYCompositeDataProvider.create(traces, "Counters", RocmXYDataProvider.ID);
    }
}
