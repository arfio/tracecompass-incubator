package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.symbols.ISymbolProvider;
import org.eclipse.tracecompass.tmf.core.symbols.TmfResolvedSymbol;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

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
        return new TmfResolvedSymbol(address, ApiFunctionAspect.INSTANCE.getFunctionNameFromFunctionID((int) address));
    }

    @Override
    public @Nullable TmfResolvedSymbol getSymbol(int pid, long timestamp, long address) {
        return getSymbol(address);
    }

}
