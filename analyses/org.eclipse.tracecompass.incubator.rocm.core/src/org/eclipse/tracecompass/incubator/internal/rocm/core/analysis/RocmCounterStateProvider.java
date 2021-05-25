package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.counters.core.CounterAnalysis;
import org.eclipse.tracecompass.analysis.counters.core.aspects.CounterAspect;
import org.eclipse.tracecompass.analysis.counters.core.aspects.ITmfCounterAspect;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils;
import org.eclipse.tracecompass.incubator.rocm.core.trace.RocmCtfTrace;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemBuilderUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.MultiAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class RocmCounterStateProvider extends AbstractTmfStateProvider {

    private static final Logger LOGGER = TraceCompassLog.getLogger(RocmCounterStateProvider.class);

    private static final String ID = "org.eclipse.tracecompass.incubator.rocm.ctf.counterstateprovider"; //$NON-NLS-1$
    private @NonNull ImmutableSet<ITmfEventAspect<?>> fCounterAspects;
    private @NonNull ImmutableMap<Class<? extends ITmfEventAspect<?>>, ITmfEventAspect<?>> fGroupingAspectImpls;

    @SuppressWarnings("javadoc")
    public static RocmCounterStateProvider create(RocmCtfTrace trace) {
        Map<Class<? extends ITmfEventAspect<?>>, ITmfEventAspect<?>> aspectImpls = new HashMap<>();
        Iterable<ITmfEventAspect<?>> counterAspects = TmfTraceUtils.getEventAspects(trace, ITmfCounterAspect.class);
        for (ITmfEventAspect<?> counter : counterAspects) {

            if (counter instanceof CounterAspect) {
                CounterAspect counterAspect = (CounterAspect) counter;
                for (Class<? extends ITmfEventAspect<?>> parentAspectClass : counterAspect.getGroups()) {

                    // Avoid creating the same aggregated aspect multiple times
                    if (parentAspectClass != null && !aspectImpls.containsKey(parentAspectClass)) {
                        /*
                         * Aggregated aspect if more than one are available for a given
                         * ITmfEventAspect<?> class.
                         */
                        ITmfEventAspect<?> goldenAspect = MultiAspect.create(TmfTraceUtils.getEventAspects(trace, parentAspectClass), parentAspectClass.getClass());
                        if (goldenAspect != null) {
                            aspectImpls.put(parentAspectClass, goldenAspect);
                        }
                    }
                }
            }
        }

        return new RocmCounterStateProvider(trace, counterAspects, aspectImpls);
    }

    private RocmCounterStateProvider(@NonNull ITmfTrace trace, Iterable<ITmfEventAspect<?>> counterAspects, Map<Class<? extends ITmfEventAspect<?>>, ITmfEventAspect<?>> aspectImpls) {
        super(trace, RocmCounterStateProvider.ID);
        fCounterAspects = ImmutableSet.copyOf(counterAspects);
        fGroupingAspectImpls = ImmutableMap.copyOf(aspectImpls);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new RocmCounterStateProvider(getTrace(), fCounterAspects, fGroupingAspectImpls);
    }

    @Override
    protected void eventHandle(@NonNull ITmfEvent event) {
        ITmfStateSystemBuilder ss = getStateSystemBuilder();
        if (ss == null) {
            return;
        }

        for (ITmfEventAspect<?> aspect : fCounterAspects) {
            if (aspect instanceof CounterAspect) {
                CounterAspect counterAspect = (CounterAspect) aspect;
                int rootQuark = ss.getQuarkAbsoluteAndAdd(CounterAnalysis.UNGROUPED_COUNTER_ASPECTS_ATTRIB);
                handleCounterAspect(event, ss, counterAspect, rootQuark);
            }
        }
    }

    private static void handleCounterAspect(ITmfEvent event, ITmfStateSystemBuilder ss, CounterAspect aspect, int rootQuark) {
        int quark = ss.getQuarkRelativeAndAdd(rootQuark, aspect.getName());
        Long eventContent = aspect.resolve(event);
        if (eventContent != null) {
            if (!aspect.isCumulative()) {
                try {
                    StateSystemBuilderUtils.incrementAttributeLong(ss, event.getTimestamp().toNanos(), quark, eventContent);
                } catch (StateValueTypeException e) {
                    TraceCompassLogUtils.traceInstant(LOGGER, Level.WARNING, "HandleCounterAspect:Exception", e); //$NON-NLS-1$
                }
            } else {
                ss.modifyAttribute(event.getTimestamp().toNanos(), eventContent, quark);
            }
        }
    }
}
