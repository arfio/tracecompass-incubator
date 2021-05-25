package org.eclipse.tracecompass.incubator.internal.benchmark.sample.core.analysis;

import java.io.File;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;

import com.google.common.annotations.VisibleForTesting;

public class MonotoneAnalysis extends InstrumentedCallStackAnalysis {
    /**
     * Call stack analysis ID
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.benchmark.sample.core.analysis.monotone"; //$NON-NLS-1$

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        return new MonotoneStateProvider(Objects.requireNonNull(getTrace()));
    }

    @Override
    @VisibleForTesting
    public File getSsFile() {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return null;
        }
        String directory = TmfTraceManager.getSupplementaryFileDir(trace);
        File htFile = new File(directory + getSsFileName());
        return htFile;
    }

}
