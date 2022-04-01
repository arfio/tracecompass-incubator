package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.counters.core.CounterAnalysis;
import org.eclipse.tracecompass.analysis.counters.core.aspects.CounterAspect;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;

public class RocmCounterAspect extends CounterAspect {

    private Integer fCounterId;

    public RocmCounterAspect(String fieldName, String label, Class<GpuAspect> class1, Integer counterId) {
        super(fieldName, label, class1);
        fCounterId = counterId;
    }

    public RocmCounterAspect(String fieldName, String label, Integer counterId) {
        super(fieldName, label);
        fCounterId = counterId;
    }

    @Override
    public boolean isCumulative() {
        return false;
    }

    @Override
    public @Nullable Long resolve(@NonNull ITmfEvent event) {
        Integer counterId = event.getContent().getFieldValue(Integer.class, RocmStrings.ID);
        if (fCounterId.equals(counterId)) {
            Long counterValue = event.getContent().getFieldValue(Long.class, RocmStrings.VALUE);
            return counterValue;
        }
        if (event.getName().equals(RocmStrings.HIP_ACTIVITY)) {
            CounterAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(event.getTrace(),
                    CounterAnalysis.class, CounterAnalysis.ID);
            if (module == null) {
                return null;
            }
            ITmfStateSystem ss = module.getStateSystem();
            if (ss == null) {
                return null;
            }
            try {
                String gpuId = event.getContent().getFieldValue(String.class, RocmStrings.DEVICE_ID);
                int groupQuark = ss.getQuarkAbsolute(CounterAnalysis.GROUPED_COUNTER_ASPECTS_ATTRIB);
                int gpuGroupQuark = ss.getQuarkRelative(groupQuark, GpuAspect.INSTANCE.getName());
                int gpuQuark = ss.getQuarkRelative(gpuGroupQuark, gpuId);
                int counterQuark = ss.getQuarkRelative(gpuQuark, this.getName());
                Long timestampEnd = event.getContent().getFieldValue(Long.class, RocmStrings.END);
                if (timestampEnd != null) {
                    long valueBefore = ss.querySingleState(event.getTimestamp().getValue(), counterQuark).getValueLong();
                    long valueAfter = ss.querySingleState(((CtfTmfTrace) event.getTrace()).timestampCyclesToNanos(timestampEnd), counterQuark).getValueLong();
                    return valueAfter - valueBefore;
                }
            } catch (AttributeNotFoundException | StateSystemDisposedException e) {
                // Either the analysis is not available or the state system has not been written yet.
                return null;
            }
        }
        return null;
    }

}
