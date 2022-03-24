package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;

/**
 * @author Arnaud Fiorini
 *
 * The base class for all GPU events handler.
 *
 * These handlers are used by the RocmCallStackStateProvider
 * but they are not responsible for dependency matching which
 * is done by the DependencyMaker classes.
 */
public abstract class GpuEventHandler {

    Set<Integer> fHostIdDefined = new HashSet<>();
    protected final RocmCallStackStateProvider fStateProvider;

    public GpuEventHandler(RocmCallStackStateProvider stateProvider) {
        fStateProvider = stateProvider;
    }

    /**
     * Handle a specific gpu event.
     *
     * @param ssb
     *            the state system to write to
     * @param event
     *            the event
     * @throws AttributeNotFoundException
     *             if the attribute is not yet create
     */
    public abstract void handleEvent(ITmfStateSystemBuilder ssb, ITmfEvent event) throws AttributeNotFoundException;

    /**
     * Add the host id (unique id per callstack to identify arrow source and target) to the state system
     *
     * @param ssb
     *            the state system to write to
     * @param trace
     *            the trace being read
     * @param hostThreadIdentifier
     *            the host thread identifier element that computes a unique id
     * @param quark
     *            the quark of the call stack
     */
    public void addHostIdToStateSystemIfNotDefined(ITmfStateSystemBuilder ssb, ITmfTrace trace, HostThreadIdentifier hostThreadIdentifier, int quark) {
        if (fHostIdDefined.contains(hostThreadIdentifier.hashCode())) {
            return;
        }
        int parentQuark = ssb.getParentAttributeQuark(quark);
        this.fHostIdDefined.add(hostThreadIdentifier.hashCode());
        ssb.modifyAttribute(trace.getStartTime().toNanos(), hostThreadIdentifier.hashCode(), parentQuark);
    }

    /**
     * @param ssb
     * @param stateProvider
     * @param callStackQuark
     * @param eventName
     * @param ts
     * @param endTs
     */
    public void pushParallelActivityOnCallStack(ITmfStateSystemBuilder ssb, int callStackQuark, String eventName, Long ts, Long endTs) {
        try {
            int depth = 1;
            int subQuark = ssb.getQuarkRelativeAndAdd(callStackQuark, String.valueOf(depth));
            if (ts < ssb.getStartTime()) {
                // do nothing
                return;
            }
            // While there is already activity on the quark
            while (ssb.querySingleState(ts, subQuark).getValue() != null) {
                depth += 1;
                subQuark = ssb.getQuarkRelativeAndAdd(callStackQuark, String.valueOf(depth));
            }
            // Register stack depth on call stack quark
            ssb.modifyAttribute(ts, depth, callStackQuark);
            fStateProvider.addFutureEvent(endTs, null, callStackQuark);
            // Register event name in the call stack
            ssb.modifyAttribute(ts, eventName, subQuark);
            fStateProvider.addFutureEvent(endTs, null, subQuark);
        } catch (StateSystemDisposedException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * @param event
     * @return
     */
    public static Long getEndTime(ITmfEvent event) {
        Long timestampEnd = event.getContent().getFieldValue(Long.class, "end");
        if (timestampEnd != null) {
            return ((CtfTmfTrace) event.getTrace()).timestampCyclesToNanos(timestampEnd);
        }
        return 0l;
    }

}
