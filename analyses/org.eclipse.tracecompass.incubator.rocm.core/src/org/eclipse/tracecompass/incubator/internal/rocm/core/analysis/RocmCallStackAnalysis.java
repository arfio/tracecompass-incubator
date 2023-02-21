package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.analysis.callstack.core.instrumented.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;

@SuppressWarnings("restriction")
public class RocmCallStackAnalysis extends InstrumentedCallStackAnalysis {

    private static String EDGES = "EDGES";

    @Override
    protected @NonNull Collection<@NonNull Integer> getEdgeQuarks() {
        ITmfStateSystem ss = getStateSystem();
        if (ss == null) {
            return Collections.emptyList();
        }
        int edgeQuark = ss.optQuarkAbsolute(EDGES);
        if (edgeQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return Collections.emptyList();
        }
        return ss.getSubAttributes(edgeQuark, false);
    }

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        return new RocmCallStackStateProvider(Objects.requireNonNull(getTrace()), new RocmEventLayout());
    }
}
