package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmEventLayout;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.old.ApiEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.old.HostThreadIdentifier;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.old.HostThreadIdentifier.KERNEL_CATEGORY;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.old.RocmStrings;
import org.eclipse.tracecompass.incubator.rocm.core.analysis.dependency.IDependencyMaker;
import org.eclipse.tracecompass.incubator.rocm.core.trace.GpuAspect;
import org.eclipse.tracecompass.internal.analysis.timing.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider.FutureEventType;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;

public class OperationEventHandler implements IRocmEventHandler {

    private Set<Integer> fHostIdDefined = new HashSet<>();

    @Override
    public void handleEvent(ITmfEvent event, ITmfStateSystemBuilder ssb, RocmEventLayout layout) {
        // Can be memory tranfer event or kernel execution event
        if (event.getName().ends

        ITmfEventField content = event.getContent();
        Long timestamp = event.getTimestamp().toNanos();
        String eventName = content.getFieldValue(String.class, RocmStrings.NAME);

        if (eventName != null && eventName.equals(RocmStrings.KERNEL_EXECUTION)) {
            int hipStreamCallStackQuark = -1;
            Long correlationId = content.getFieldValue(Long.class, RocmStrings.CORRELATION_ID);
            int gpuId = getGpuId(event);
            int hipStreamId = -1;
            // Placeholder value in case we do not match any api event.
            String kernelName = RocmStrings.KERNEL_EXECUTION;
            // TODO: Match dependency
            Integer queueId = content.getFieldValue(Integer.class, RocmStrings.QUEUE_ID);
            if (queueId == null || correlationId == null) {
                return;
            }
            Long timestampEnd = content.getFieldValue(Long.class, RocmStrings.END);
            if (timestampEnd != null) {
                pushParallelActivityOnCallStack(ssb, callStackQuark, kernelName, timestamp,
                        ((CtfTmfTrace) event.getTrace()).timestampCyclesToNanos(timestampEnd));
                if (hipStreamCallStackQuark > 0) {
                    pushParallelActivityOnCallStack(ssb, hipStreamCallStackQuark, kernelName,
                            timestamp, ((CtfTmfTrace) event.getTrace()).timestampCyclesToNanos(timestampEnd));
                }
            }

            // Add Host Thread Identifier for dependency arrows
            HostThreadIdentifier queueHostThreadIdentifier = new HostThreadIdentifier(queueId.intValue(), KERNEL_CATEGORY.QUEUE, gpuId);
            addHostIdToStateSystemIfNotDefined(ssb, event.getTrace(), queueHostThreadIdentifier, callStackQuark);
            if (hipStreamCallStackQuark > 0) {
                HostThreadIdentifier streamHostThreadIdentifier = new HostThreadIdentifier(hipStreamId, KERNEL_CATEGORY.STREAM, gpuId);
                addHostIdToStateSystemIfNotDefined(ssb, event.getTrace(), streamHostThreadIdentifier, hipStreamCallStackQuark);
            }
        } else {
            if (eventName == null) {
                ssb.modifyAttribute(timestamp, null, callStackQuark);
                return;
            }
            Long timestampEnd = content.getFieldValue(Long.class, RocmStrings.END);
            ssb.pushAttribute(timestamp, eventName, callStackQuark);
            if (timestampEnd != null) {
                fStateProvider.addFutureEvent(((CtfTmfTrace) event.getTrace()).timestampCyclesToNanos(timestampEnd),
                        timestampEnd, callStackQuark, FutureEventType.POP);
            }
            // Add CallStack Identifier (tid equivalent) for the memory quark
            HostThreadIdentifier hostThreadIdentifier = new HostThreadIdentifier();
            addHostIdToStateSystemIfNotDefined(ssb, event.getTrace(), hostThreadIdentifier, callStackQuark);
        }
    }

    private static int getCallStackQuark(ITmfStateSystemBuilder ssb, ITmfEvent event) {
        String eventName = event.getContent().getFieldValue(String.class, RocmStrings.NAME);
        if (eventName == null) {
            return -1;
        }
        if (eventName.equals(RocmStrings.KERNEL_EXECUTION)) {
            Long queueId = event.getContent().getFieldValue(Long.class, RocmStrings.QUEUE_ID);
            Long gpuId = event.getContent().getFieldValue(Long.class, RocmStrings.DEVICE_ID);
            if (queueId == null || gpuId == null) {
                return -1;
            }
            int gpuQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU + gpuId.toString());
            int queuesQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, RocmStrings.QUEUES);
            int queueQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, RocmStrings.QUEUE + Long.toString(queueId));
            return ssb.getQuarkRelativeAndAdd(queueQuark, InstrumentedCallStackAnalysis.CALL_STACK);
        }
        int copyQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.MEMORY);
        int tempQuark1 = ssb.getQuarkRelativeAndAdd(copyQuark, StringUtils.EMPTY);
        int tempQuark2 = ssb.getQuarkRelativeAndAdd(tempQuark1, RocmStrings.MEMORY_TRANSFERS);
        return ssb.getQuarkRelativeAndAdd(tempQuark2, InstrumentedCallStackAnalysis.CALL_STACK);
    }

    private static int getHipStreamCallStackQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event, Integer gpuId) {
        int gpuQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU + gpuId.toString());
        int hipStreamsQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, RocmStrings.STREAMS);
        int hipStreamId = Integer.parseInt(ApiEventHandler.getArg(event.getContent(), 4));
        int hipStreamQuark = ssb.getQuarkRelativeAndAdd(hipStreamsQuark, RocmStrings.STREAM + Integer.toString(hipStreamId));
        return ssb.getQuarkRelativeAndAdd(hipStreamQuark, InstrumentedCallStackAnalysis.CALL_STACK);
    }

    private static int getGpuId(ITmfEvent event) {
        Integer gpuId = (Integer) TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), GpuAspect.class, event);
        if (gpuId != null) {
            return gpuId;
        }
        return -1;
    }

    /**
     * Add the host id (unique id per callstack to identify arrow source and
     * target) to the state system
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
    private void addHostIdToStateSystemIfNotDefined(ITmfStateSystemBuilder ssb, ITmfTrace trace, HostThreadIdentifier hostThreadIdentifier, int quark) {
        if (fHostIdDefined.contains(hostThreadIdentifier.hashCode())) {
            return;
        }
        int parentQuark = ssb.getParentAttributeQuark(quark);
        this.fHostIdDefined.add(hostThreadIdentifier.hashCode());
        ssb.modifyAttribute(trace.getStartTime().toNanos(), hostThreadIdentifier.hashCode(), parentQuark);
    }

    /**
     * @param ssb
     *            The state system builder used
     * @param callStackQuark
     *            the quark to push to
     * @param eventName
     *            the gpu activity name (usually the compute kernel name)
     * @param ts
     *            the begin timestamp
     * @param endTs
     *            the end timestamp
     */
    private void pushParallelActivityOnCallStack(ITmfStateSystemBuilder ssb, int callStackQuark, String eventName, Long ts, Long endTs) {
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
            Activator.getInstance().logError(e.getMessage());
        }
    }
}
