/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.profiling.core.callstack.CallStackAnalysis;
import org.eclipse.tracecompass.analysis.profiling.core.callstack.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.base.EdgeStateValue;
import org.eclipse.tracecompass.incubator.internal.traceevent.core.event.ITraceEventConstants;
import org.eclipse.tracecompass.incubator.internal.traceevent.core.event.TraceEventAspects;
import org.eclipse.tracecompass.incubator.internal.traceevent.core.event.TraceEventPhases;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Rocm callstack state provider
 *
 * @author Arnaud Fiorini
 */
@SuppressWarnings("restriction")
public class RocmCallStackStateProvider extends CallStackStateProvider {


    private static final int VERSION = 1;
    private static final @NonNull String BEGIN_TIME_FIELD = "args/BeginNs"; //$NON-NLS-1$
    private static final @NonNull String END_TIME_FIELD = "args/EndNs"; //$NON-NLS-1$
    private static final @NonNull String FROM_TIME_FIELD = "args/TimingNs"; //$NON-NLS-1$
    private static final @NonNull String GPU_ID_FIELD = "args/gpu-id"; //$NON-NLS-1$
    private static final String[] ARGS_TO_FILTER = new String[] { BEGIN_TIME_FIELD, END_TIME_FIELD,
            FROM_TIME_FIELD, GPU_ID_FIELD, "args/KernelName", "args/queue-id", "args/queue-index",   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
            "args/tid", "args/grd", "args/wgr", "args/lds", "args/scr", "args/vgpr", "args/sgpr",   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$//$NON-NLS-7$
            "args/fbar", "args/sig", "args/DispatchNs", "args/CompleteNs", "args/DurationNs",   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$
            "args/args", "args/pid", "args/Name" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final Set<String> ARGS_TO_FILTER_SET = new HashSet<>(Arrays.asList(ARGS_TO_FILTER));

    // Lanes name
    static final @NonNull String EDGES_LANE = "EDGES"; //$NON-NLS-1$
    private static final @NonNull String MEMORY_LANE = "Memory Transfers"; //$NON-NLS-1$
    private static final @NonNull String GPU_LANE = "GPU"; //$NON-NLS-1$
    private static final @NonNull String RUNTIME_API_LANE = "Runtime API"; //$NON-NLS-1$
    private static final @NonNull String UNKNOWN_LANE = "Unknown"; //$NON-NLS-1$
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

    /**
     * Constructor
     *
     * @param trace the trace to analyze.
     */
    public RocmCallStackStateProvider(@NonNull ITmfTrace trace) {
        super(trace);
        fIdAspect = TraceEventAspects.ID_ASPECT;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public @NonNull CallStackStateProvider getNewInstance() {
        return new RocmCallStackStateProvider(getTrace());
    }

    @Override
    protected boolean considerEvent(@NonNull ITmfEvent event) {
        return true;
    }

    @Override
    protected @Nullable ITmfStateValue functionEntry(@NonNull ITmfEvent event) {
        return null;
    }

    @Override
    protected @Nullable ITmfStateValue functionExit(@NonNull ITmfEvent event) {
        return null;
    }

    @Override
    protected int getProcessId(@NonNull ITmfEvent event) {
        Object pidObject = event.getContent().getField(ITraceEventConstants.PID).getValue();
        int pid;
        if (pidObject instanceof Number) {
            pid = ((Number) pidObject).intValue();
        } else {
            pid = Integer.parseInt((String) pidObject);
        }
        return pid;
    }

    @Override
    protected long getThreadId(@NonNull ITmfEvent event) {
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
    protected @Nullable String getProcessName(ITmfEvent event) {
        if (event.getContent().getFieldNames().contains(ITraceEventConstants.PID) == false) {
            return UNKNOWN_LANE;
        }
        int pid = getProcessId(event);
        switch(pid) {
        case 0:
            return MEMORY_LANE;
        case 2:
            return RUNTIME_API_LANE;
        case 4:
            return GPU_LANE;
        default:
            return UNKNOWN_LANE;
        }
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
        int processQuark = ssb.getQuarkAbsoluteAndAdd(PROCESSES, getProcessName(event));
        int threadQuark = ssb.getQuarkRelativeAndAdd(processQuark, Long.toString(getThreadId(event)));
        int callStackQuark = ssb.getQuarkRelativeAndAdd(threadQuark, CallStackAnalysis.CALL_STACK);

        ssb.modifyAttribute(event.getTimestamp().toNanos(), Long.toString(getThreadId(event)), threadQuark);
        ssb.modifyAttribute(event.getTimestamp().toNanos(), Integer.toString(getProcessId(event)), processQuark);

        long timeBegin = getTimeFromEventField(event, BEGIN_TIME_FIELD);
        long timeEnd = getTimeFromEventField(event, END_TIME_FIELD);

        ssb.pushAttribute(timeBegin, event.getName(), callStackQuark);
        ssb.popAttribute(timeEnd, callStackQuark);
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
        int processQuark = ssb.getQuarkAbsoluteAndAdd(PROCESSES, getProcessName(event));
        int threadQuark = ssb.getQuarkRelativeAndAdd(processQuark, Long.toString(getThreadId(event)));
        int callStackQuark = ssb.getQuarkRelativeAndAdd(threadQuark, CallStackAnalysis.CALL_STACK);

        long uSecondTime = event.getTimestamp().toNanos();
        try {
            ITmfStateInterval correspondingEvent = ssb.querySingleState(uSecondTime, callStackQuark);
            return correspondingEvent.getEndTime() + 1;
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
