package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.rocm.core.Activator;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmMetadataAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmMetadataStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.tmf.core.symbols.ISymbolProvider;
import org.eclipse.tracecompass.tmf.core.symbols.TmfResolvedSymbol;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class RocmTraceSymbolProvider implements ISymbolProvider {

    private @NonNull ITmfTrace fTrace;

    /**
     * Constructor
     *
     * @param trace
     *            The trace this provider is for
     */
    public RocmTraceSymbolProvider(@NonNull ITmfTrace trace) {
        fTrace = trace;
    }

    @Override
    public @NonNull ITmfTrace getTrace() {
        return fTrace;
    }

    @Override
    public void loadConfiguration(@Nullable IProgressMonitor monitor) {
        // No configuration
    }

    @Override
    public @Nullable TmfResolvedSymbol getSymbol(long address) {
        RocmMetadataAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(getTrace(),
                RocmMetadataAnalysis.class, RocmMetadataAnalysis.ID);
        if (module == null || address == -1) {
            /*
             * The analysis is not available for this trace, we won't be able to
             * find the information.
             */
            return new TmfResolvedSymbol(address, StringUtils.EMPTY);
        }
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return new TmfResolvedSymbol(address, StringUtils.EMPTY);
        }
        String functionName = StringUtils.EMPTY;
        try {
            RocmTrace trace = (RocmTrace) getTrace();
            int nApi = trace.getNApi();
            Integer apiId = (int) (address % nApi);
            int cid = (int) ((address - apiId) / nApi);
            int functionNameQuark = ss.getQuarkAbsolute(RocmMetadataStateProvider.FUNCTION_NAMES);
            int apiQuark = ss.getQuarkRelative(functionNameQuark, apiId.toString());
            functionName = ss.querySingleState(ss.getStartTime() + cid, apiQuark).getValueString();
        } catch (AttributeNotFoundException | StateSystemDisposedException e) {
            Activator.getInstance().logError(e.getMessage());
            return new TmfResolvedSymbol(address, StringUtils.EMPTY);
        }
        return new TmfResolvedSymbol(address, functionName);
    }

    @Override
    public @Nullable TmfResolvedSymbol getSymbol(int pid, long timestamp, long address) {
        return getSymbol(address);
    }

}
