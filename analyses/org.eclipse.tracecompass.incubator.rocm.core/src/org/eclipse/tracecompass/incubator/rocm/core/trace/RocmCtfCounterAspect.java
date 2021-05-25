package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.tracecompass.analysis.counters.core.aspects.CounterAspect;

public class RocmCtfCounterAspect extends CounterAspect {

    public RocmCtfCounterAspect(String fieldName, String label, Class<GpuAspect> class1) {
        super(fieldName, label, class1);
    }

    public RocmCtfCounterAspect(String fieldName, String label) {
        super(fieldName, label);
    }

    @Override
    public boolean isCumulative() {
        return false;
    }

}
