/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    static final String EDGES = "EDGES"; //$NON-NLS-1$
    private static final int VERSION = 1;
    private static final String BEGIN_TIME_FIELD = "args/BeginNs"; //$NON-NLS-1$
    private static final String END_TIME_FIELD = "args/EndNs"; //$NON-NLS-1$
    private static final String FROM_TIME_FIELD = "args/TimingNs"; //$NON-NLS-1$
    private static final String MEMORY_LANE = "Memory Transfers"; //$NON-NLS-1$
    private static final String GPU_LANE = "GPU"; //$NON-NLS-1$
    private static final String RUNTIME_API_LANE = "Runtime API"; //$NON-NLS-1$
    private static final String UNKNOWN_LANE = "Unknown"; //$NON-NLS-1$
    private static final String COUNTERS_LANE = "Counters"; //$NON-NLS-1$
    private final ITmfEventAspect<?> fIdAspect;

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
    public RocmCallStackStateProvider(ITmfTrace trace) {
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
        default:
            return;
        }
    }

    private static long getTimeFromEventField(ITmfEvent event, String field) {
        return Long.parseLong((String) event.getContent().getField(field).getValue());
    }

    private void handleCompleteEvent(ITmfEvent event) {
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

    private long getEventNanoTime(ITmfEvent event) {
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

            Object edgeStateValue = new EdgeStateValue(Integer.parseInt(sId), srcHostThread, currHostThread);

            ssb.modifyAttribute(startTime, edgeStateValue, edgeQuark);
            ssb.modifyAttribute(ts, (Object) null, edgeQuark);
        }
    }

    private static int getAvailableEdgeQuark(ITmfStateSystemBuilder ssb, long startTime) {
        int edgeRoot = ssb.getQuarkAbsoluteAndAdd(EDGES);
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
        int countersQuark = ssb.getQuarkAbsoluteAndAdd(COUNTERS_LANE, "VALUInsts"); //$NON-NLS-1$
        long time = getTimeFromEventField(event, END_TIME_FIELD);
        ITmfEventField eventContent = event.getContent().getField("args/VALUInsts"); //$NON-NLS-1$
        if (eventContent == null) {
            return;
        }
        int counterValue = Integer.parseInt((String) eventContent.getValue());
        ssb.modifyAttribute(
            time,
            counterValue,
            countersQuark
        );
    }
}
