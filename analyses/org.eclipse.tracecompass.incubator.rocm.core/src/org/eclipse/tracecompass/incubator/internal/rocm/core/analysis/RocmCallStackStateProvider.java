/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.profiling.core.callstack.CallStackAnalysis;
import org.eclipse.tracecompass.incubator.callstack.core.base.EdgeStateValue;
import org.eclipse.tracecompass.incubator.internal.traceevent.core.event.ITraceEventConstants;
import org.eclipse.tracecompass.incubator.internal.traceevent.core.event.TraceEventAspects;
import org.eclipse.tracecompass.incubator.internal.traceevent.core.event.TraceEventPhases;
import org.eclipse.tracecompass.internal.provisional.jsontrace.core.trace.JsonTrace;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Rocm callstack state provider
 *
 * @author Arnaud Fiorini
 */
@SuppressWarnings("restriction")
public class RocmCallStackStateProvider extends AbstractTmfStateProvider {

    private static final @NonNull String ID = "org.eclipse.tracecompass.incubator.rocm.callstackstateprovider"; //$NON-NLS-1$
    private static final @NonNull String PROCESSES = "Processes"; //$NON-NLS-1$
    private static final @NonNull String BEGIN_TIME_FIELD = "args/BeginNs"; //$NON-NLS-1$
    private static final @NonNull String END_TIME_FIELD = "args/EndNs"; //$NON-NLS-1$
    private static final @NonNull String FROM_TIME_FIELD = "args/TimingNs"; //$NON-NLS-1$
    private static final @NonNull String GPU_ID_FIELD = "args/gpu-id"; //$NON-NLS-1$
    private static final String[] ARGS_TO_FILTER = new String[] { BEGIN_TIME_FIELD, END_TIME_FIELD,
            FROM_TIME_FIELD, GPU_ID_FIELD, "args/KernelName", "args/dev-id", "args/queue-id", "args/queue-index",   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
            "args/tid", "args/grd", "args/wgr", "args/lds", "args/scr", "args/vgpr", "args/sgpr",   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$//$NON-NLS-7$
            "args/fbar", "args/sig", "args/DispatchNs", "args/CompleteNs", "args/DurationNs",   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$
            "args/args", "args/pid", "args/Name" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final Set<String> ARGS_TO_FILTER_SET = new HashSet<>(Arrays.asList(ARGS_TO_FILTER));

    // Lanes name
    static final @NonNull String EDGES_LANE = "EDGES"; //$NON-NLS-1$
    private static final @NonNull String MEMORY_LANE = "Memory Transfers"; //$NON-NLS-1$
    private static final @NonNull String HSA_API_LANE = "HSA API"; //$NON-NLS-1$
    private static final @NonNull String HIP_API_LANE = "HIP API"; //$NON-NLS-1$
    private static final @NonNull String COUNTERS_LANE = "Counters"; //$NON-NLS-1$
    private static final @NonNull String GPU_INFO_LANE = "GPUInfo"; //$NON-NLS-1$

    private final ITmfEventAspect<?> fIdAspect;
    private int fEdgeId = 0;

    /**
     * Map of trace event scope ID string to their start times
     */
    private final Map<String, Long> fEdgeStartTimes = new HashMap<>();
    /**
     * Map of trace event scope ID string to the source {@link HostThread} of the edge.
     */
    private final Map<String, HostThread> fEdgeSrcHosts = new HashMap<>();
    private final Map<Integer, @NonNull List<Long>> fEventInFlightPerQuark = new HashMap<>();

    /**
     * Constructor
     *
     * @param trace the trace to analyze.
     */
    public RocmCallStackStateProvider(@NonNull ITmfTrace trace) {
        super(trace, ID);
        fIdAspect = TraceEventAspects.ID_ASPECT;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new RocmCallStackStateProvider(getTrace());
    }

    private static int getProcessId(@NonNull ITmfEvent event) {
        Object pidObject = event.getContent().getField(ITraceEventConstants.PID).getValue();
        int pid;
        if (pidObject instanceof Number) {
            pid = ((Number) pidObject).intValue();
        } else {
            pid = Integer.parseInt((String) pidObject);
        }
        return pid;
    }

    private static long getThreadId(@NonNull ITmfEvent event) {
        Object pidObject = event.getContent().getField(ITraceEventConstants.TID).getValue();
        long pid;
        if (pidObject instanceof Number) {
            pid = ((Number) pidObject).longValue();
        } else {
            pid = Long.parseLong((String) pidObject);
        }
        return pid;
    }

    @Override
    protected void eventHandle(ITmfEvent event) {
        String ph = event.getContent().getFieldValue(String.class, ITraceEventConstants.PHASE);
        if (ph == null) {
            return;
        }
        switch (ph) {
        case TraceEventPhases.DURATION:
            addCounterEvent(event);
            handleCompleteEvent(event);
            break;
        case TraceEventPhases.FLOW_START:
            updateStartFlowEvent(event);
            break;
        case TraceEventPhases.FLOW_END:
        case TraceEventPhases.FLOW_STEP:
            updateEndFlowEvent(event);
            break;
        case TraceEventPhases.METADATA:
            addMetadata(event);
            break;
        default:
            return;
        }
    }

    /**
     * Gets the value from one field of an event and interprets it as a timestamp
     *
     * @param event
     * @param field name of the field selected
     */
    private static long getTimeFromEventField(ITmfEvent event, String field) {
        if (event.getContent().getFieldNames().contains(field)) {
            return Long.parseLong((String) event.getContent().getField(field).getValue());
        }
        return 0l;
    }

    private void handleCompleteEvent(@NonNull ITmfEvent event) {
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }

        int callStackQuark = getCorrectQuark(ssb, event);

        List<Long> eventInFlight = fEventInFlightPerQuark.get(callStackQuark);
        if (eventInFlight == null) {
            eventInFlight = new ArrayList<>();
            fEventInFlightPerQuark.put(callStackQuark, eventInFlight);
        }
        long timeBegin = getTimeFromEventField(event, BEGIN_TIME_FIELD);
        long timeEnd = getTimeFromEventField(event, END_TIME_FIELD);
        int depthFree = 1; // depth at which the lane is available
        for (int i = 0; i < eventInFlight.size(); i++) {
            if (timeBegin > eventInFlight.get(i)) {
                eventInFlight.remove(i);
                eventInFlight.add(i, timeEnd);
                break;
            }
            depthFree += 1;
        }

        if (eventInFlight.size() < depthFree) {
            eventInFlight.add(timeEnd);
        }
        int laneQuark = ssb.getQuarkRelativeAndAdd(callStackQuark, Integer.toString(depthFree));

        ssb.modifyAttribute(timeBegin, event.getName(), laneQuark);
        ssb.removeAttribute(timeEnd, laneQuark);
    }

    /**
     * Select the correct quark in order to place the event at the right place
     *
     * @param ssb state system builder
     * @param event event to parse
     * @return quark of the correct callstack
     */
    private int getCorrectQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event) {
        Map<@NonNull String, @NonNull String> traceProperties = ((JsonTrace) getTrace()).getProperties();
        String processName = traceProperties.get("pid-" + getProcessId(event)); //$NON-NLS-1$

        // Is it a GPU event ?
        if (processName != null && processName.startsWith("GPU")) { //$NON-NLS-1$
            int gpuQuark = ssb.getQuarkAbsoluteAndAdd(PROCESSES, processName);
            // Is it a memory transfer event ?
            if (event.getName().startsWith("hcMemcpy")) { //$NON-NLS-1$
                int systemQuark = ssb.getQuarkAbsoluteAndAdd(PROCESSES, "System"); //$NON-NLS-1$
                int memCpyQuark = ssb.getQuarkRelativeAndAdd(systemQuark, MEMORY_LANE);
                int queueQuark = ssb.getQuarkRelativeAndAdd(memCpyQuark,
                        (String) event.getContent().getField("args/queue-id").getValue()); //$NON-NLS-1$
                return ssb.getQuarkRelativeAndAdd(queueQuark, CallStackAnalysis.CALL_STACK);
            }
            int computeQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, "Kernels"); //$NON-NLS-1$
            int queueQuark = ssb.getQuarkRelativeAndAdd(computeQuark, "queue1"); //$NON-NLS-1$
            return ssb.getQuarkRelativeAndAdd(queueQuark, CallStackAnalysis.CALL_STACK);
        }
        int systemQuark = ssb.getQuarkAbsoluteAndAdd(PROCESSES, "System"); //$NON-NLS-1$
        int runtimeQuark;
        // Is it an hsa event ?
        if (event.getName().startsWith("hsa")) { //$NON-NLS-1$
            runtimeQuark = ssb.getQuarkRelativeAndAdd(systemQuark, HSA_API_LANE);
        } else {
            runtimeQuark = ssb.getQuarkRelativeAndAdd(systemQuark, HIP_API_LANE);
        }
        int threadQuark = ssb.getQuarkRelativeAndAdd(runtimeQuark, Long.toString(getThreadId(event)));
        return ssb.getQuarkRelativeAndAdd(threadQuark, CallStackAnalysis.CALL_STACK);
    }

    private void updateStartFlowEvent(ITmfEvent event) {
        String id = event.getContent().getFieldValue(String.class, ITraceEventConstants.ID);
        long time = getTimeFromEventField(event, FROM_TIME_FIELD);
        fEdgeStartTimes.putIfAbsent(id, time);

        int tid = (int) getThreadId(event);
        HostThread currHostThread = new HostThread(event.getTrace().getHostId(), tid);
        fEdgeSrcHosts.putIfAbsent(id, currHostThread);
    }

    private long getEventNanoTime(@NonNull ITmfEvent event) {
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return 0;
        }
//        int processQuark = ssb.getQuarkAbsoluteAndAdd(PROCESSES, getProcessName(event));
//        int threadQuark = ssb.getQuarkRelativeAndAdd(processQuark, Long.toString(getThreadId(event)));
//        int callStackQuark = ssb.getQuarkRelativeAndAdd(threadQuark, CallStackAnalysis.CALL_STACK);

        long uSecondTime = event.getTimestamp().toNanos();
        try {
            List<ITmfStateInterval> correspondingEvents = ssb.queryFullState(uSecondTime+1000);
            for (ITmfStateInterval correspondingEvent: correspondingEvents) {
                if (correspondingEvent.getStartTime() == uSecondTime) {
                    return correspondingEvent.getEndTime() + 1;
                }
            }
        } catch (StateSystemDisposedException e) {
            e.printStackTrace();
        }
        return uSecondTime;
    }

    private void updateEndFlowEvent(ITmfEvent event) {
        String sId = event.getContent().getFieldValue(String.class, ITraceEventConstants.ID);
        if (sId == null) {
            Object resolve = fIdAspect.resolve(event);
            if (resolve == null) {
                resolve = Integer.valueOf(0);
            }
            sId = String.valueOf(resolve);
        }
        int tid = (int) getThreadId(event);
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }

        long ts = getEventNanoTime(event);
        long startTime = fEdgeStartTimes.getOrDefault(sId, ts);
        fEdgeStartTimes.remove(sId);

        HostThread srcHostThread = fEdgeSrcHosts.remove(sId);
        HostThread currHostThread = new HostThread(event.getTrace().getHostId(), tid);
        if (srcHostThread != null) {
            int edgeQuark = getAvailableEdgeQuark(ssb, startTime);

            Object edgeStateValue = new EdgeStateValue(fEdgeId++, srcHostThread, currHostThread);

            ssb.modifyAttribute(startTime, edgeStateValue, edgeQuark);
            ssb.modifyAttribute(ts, (Object) null, edgeQuark);
        }
    }

    private static int getAvailableEdgeQuark(ITmfStateSystemBuilder ssb, long startTime) {
        int edgeRoot = ssb.getQuarkAbsoluteAndAdd(EDGES_LANE);
        List<@NonNull Integer> subQuarks = ssb.getSubAttributes(edgeRoot, false);

        for (int quark : subQuarks) {
            long start = ssb.getOngoingStartTime(quark);
            Object value = ssb.queryOngoing(quark);
            if (value == null && start < startTime) {
                return quark;
            }
        }

        return ssb.getQuarkRelativeAndAdd(edgeRoot, Integer.toString(subQuarks.size()));
    }

    private void addCounterEvent(ITmfEvent event) {
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }
        long time = getTimeFromEventField(event, END_TIME_FIELD);
        // Add every args value that are not in the args to filter set.
        // We assume that it is a counter if it is not in this set.
        for (String fieldName: event.getContent().getFieldNames()) {
            if (fieldName.startsWith("args/") == false || ARGS_TO_FILTER_SET.contains(fieldName)) { //$NON-NLS-1$
                continue;
            }
            ITmfEventField field = event.getContent().getField(fieldName);
            if (field == null) {
                return;
            }
            // This quark is set in the loop to make sure that we have a kernel event with counters
            int gpuCountersQuark = ssb.getQuarkAbsoluteAndAdd(COUNTERS_LANE, event.getContent().getFieldValue(String.class, GPU_ID_FIELD));
            int counterQuark = ssb.getQuarkRelativeAndAdd(gpuCountersQuark, fieldName);
            ssb.modifyAttribute(
                time,
                field.getValue(),
                counterQuark
            );
        }
    }

    /**
     * Adds bounds and other informations about each gpu into the state system
     *
     * @param event metadata event given in the event handler
     */
    private void addMetadata(ITmfEvent event) {
        if (event.getName().equals("gpu_info")) { //$NON-NLS-1$
            ITmfStateSystemBuilder ssb = getStateSystemBuilder();
            if (ssb == null) {
                return;
            }
            int gpuQuark = ssb.getQuarkAbsoluteAndAdd(
                GPU_INFO_LANE,
                (String) event.getContent().getField(GPU_ID_FIELD).getValue()
            );
            for (ITmfEventField field : event.getContent().getFields()) {
                if (field == null) {
                    continue;
                }
                if (field.getName().startsWith("args/")) { //$NON-NLS-1$
                    int fieldQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, field.getName());
                    ssb.modifyAttribute(0, field.getValue(), fieldQuark);
                }
            }
        }
    }
}
