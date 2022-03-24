package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.tracecompass.analysis.counters.core.aspects.CounterAspect;

public class RocmCounterAspect extends CounterAspect {

    public RocmCounterAspect(String fieldName, String label, Class<GpuAspect> class1) {
        super(fieldName, label, class1);
    }

    public RocmCounterAspect(String fieldName, String label) {
        super(fieldName, label);
    }

    @Override
    public boolean isCumulative() {
        return false;
    }

}
