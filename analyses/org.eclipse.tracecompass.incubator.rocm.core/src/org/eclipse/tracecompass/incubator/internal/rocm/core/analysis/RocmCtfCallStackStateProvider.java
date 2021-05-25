/*******************************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.incubator.callstack.core.base.EdgeStateValue;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventType;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceWithPreDefinedEvents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Main state provider that defines the API states and GPU states,
 * as well as dependencies and performance counters (not yet implemented)
 *
 * @author Arnaud Fiorini
 */
public class RocmCtfCallStackStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.rocm.ctf.callstackstateprovider"; //$NON-NLS-1$
    static final @NonNull String EDGES_LANE = "EDGES"; //$NON-NLS-1$

    IDependencyMaker fDependencyMaker;
    final List<Long> fCurrentKernelDispatched = new LinkedList<>();
    Map<Long, ITmfEvent> fHipKernelDispatchs = new HashMap<>();
    Long fHipDispatchCounter = 0L;
    Table<Long, Integer, Integer> fHostIdArrowsSystemTable = HashBasedTable.create();
    Map<Long, Table<Integer, Long, Integer>> fHostIdArrowsGpuTables = new HashMap<>();
    int fHostIdCounter = 0;

    /* Used to calculate unique identifier for each thread and each API */
    private static final Map<String, Integer> fApiId;
    static {
        fApiId = new HashMap<>();
        fApiId.put(RocmStrings.HIP_API, 1);
        fApiId.put(RocmStrings.HSA_API, 2);
        fApiId.put(RocmStrings.KFD_API, 3);
    }
    private enum GPU_CATEGORIES {
        QUEUE, STREAM
    }


    /**
     * @param trace Trace to follow
     */
    public RocmCtfCallStackStateProvider(@NonNull ITmfTrace trace) {
        super(trace, ID);
        for (ITmfEventType eventType : ((ITmfTraceWithPreDefinedEvents) trace).getContainedEventTypes()) {
            if (eventType.getName() == RocmStrings.HSA_API) {
                fDependencyMaker = new HsaApiHsaActivityDependencyMaker();
                break;
            }
            if (eventType.getName() == RocmStrings.HCC_OPS) {
                fDependencyMaker = new HipApiHipActivityDependencyMaker();
                break;
            }
            if (eventType.getName() == RocmStrings.HIP_API) {
                fDependencyMaker = new HipApiHsaActivityDependencyMaker();
                // If we have HIP_API events, we can have HIP activity events. We shouldn't break in this case
            }
        }
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new RocmCtfCallStackStateProvider(getTrace());
    }

    @Override
    protected void eventHandle(ITmfEvent event) {
        ITmfEventField content = event.getContent();
        if (content == null) {
            return;
        }
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }
        int quark = getCorrectQuark(ssb, event);
        if (quark == -1) {
            return;
        }
        if (event.getName().equals(RocmStrings.GPU_KERNEL)) {
            processGpuEvent(event, ssb, quark);
        } else if (event.getName().equals(RocmStrings.ASYNC_COPY)) {
            processMemoryCopies(event, ssb, quark);
        } else {
            processApiEvent(event, ssb, quark);
        }

    }

    private static void processMemoryCopies(ITmfEvent event, ITmfStateSystemBuilder ssb, int quark) {
        ITmfEventField content = event.getContent();
        long timestamp = event.getTimestamp().toNanos();
        String eventName = content.getFieldValue(String.class, RocmStrings.NAME);
        if (eventName == null) {
            ssb.modifyAttribute(timestamp, null, quark);
            return;
        }
        if (eventName.endsWith("_exit")) { //$NON-NLS-1$
            ssb.popAttribute(timestamp, quark);
        } else {
            ssb.pushAttribute(timestamp, eventName.substring(0, eventName.length()-6), quark);
        }
    }

    private void processGpuEvent(ITmfEvent event, ITmfStateSystemBuilder ssb, int callStackQuark) {
        ITmfEventField content = event.getContent();
        long timestamp = event.getTimestamp().toNanos();
        String eventName = (String) content.getField(RocmStrings.KERNEL_NAME).getValue();
        Long eventDispatchId = content.getFieldValue(Long.class, RocmStrings.KERNEL_DISPATCH_ID);
        if (eventDispatchId == null) {
            return;
        }
        Long gpuId = event.getContent().getFieldValue(Long.class, RocmStrings.GPU_ID);
        Long queueId = event.getContent().getFieldValue(Long.class, RocmStrings.QUEUE_ID);
        Table<Integer, Long, Integer> hostIdTable = fHostIdArrowsGpuTables.get(gpuId);
        if (hostIdTable == null) {
            hostIdTable = HashBasedTable.create();
            fHostIdArrowsGpuTables.put(gpuId, hostIdTable);
        }
        updateGpuGapState(ssb, timestamp, gpuId, eventDispatchId);
        if (fCurrentKernelDispatched.remove(eventDispatchId)) {
            ssb.popAttribute(timestamp, callStackQuark);
            // correlate kernel dispatch to kernel
            if (fHipKernelDispatchs.size() == 0) {
                return;
            }
            ITmfEvent hipEvent = fHipKernelDispatchs.remove(Collections.min(fHipKernelDispatchs.keySet()));
            if (hipEvent == null) {
                return;
            }
            int hipStreamCallStackQuark = getHipStreamCallStackQuark(ssb, hipEvent, gpuId);
            ssb.popAttribute(timestamp, hipStreamCallStackQuark);

        } else {
            fCurrentKernelDispatched.add(eventDispatchId);
            ssb.pushAttribute(timestamp, eventName, callStackQuark);
            // Add CallStack Identifier (tid equivalent) for the queue quark
            if (gpuId != null && !hostIdTable.contains(GPU_CATEGORIES.QUEUE.ordinal(), queueId)) {
                int parentQuark = ssb.getParentAttributeQuark(callStackQuark);
                hostIdTable.put(GPU_CATEGORIES.QUEUE.ordinal(), queueId, fHostIdCounter++);
                ssb.modifyAttribute(getTrace().getStartTime().toNanos(), hostIdTable.get(GPU_CATEGORIES.QUEUE.ordinal(), queueId), parentQuark);
            }
            // Correlate kernel dispatch to kernel
            if (fHipKernelDispatchs.size() == 0) {
                return;
            }
            ITmfEvent hipEvent = fHipKernelDispatchs.get(Collections.min(fHipKernelDispatchs.keySet()));
            if (hipEvent == null) {
                return;
            }
            int hipStreamCallStackQuark = getHipStreamCallStackQuark(ssb, hipEvent, gpuId);
            ssb.pushAttribute(timestamp, eventName, hipStreamCallStackQuark);
            // Add CallStack Identifier (tid equivalent) for the stream quark
            Integer hipStreamId = getHipStreamId(hipEvent);
            if (gpuId != null && !hostIdTable.contains(GPU_CATEGORIES.STREAM.ordinal(), hipStreamId.longValue())) {
                int parentQuark = ssb.getParentAttributeQuark(hipStreamCallStackQuark);
                hostIdTable.put(GPU_CATEGORIES.STREAM.ordinal(), hipStreamId.longValue(), fHostIdCounter++);
                ssb.modifyAttribute(getTrace().getStartTime().toNanos(), hostIdTable.get(GPU_CATEGORIES.STREAM.ordinal(), hipStreamId.longValue()), parentQuark);
            }
            // Add Kernel Launch Dependency
            Long tid = hipEvent.getContent().getFieldValue(Long.class, RocmStrings.TID);
            if (tid != null && gpuId != null) {
                Integer srcHostId = fHostIdArrowsSystemTable.get(tid, fApiId.getOrDefault(hipEvent.getName(), 4));
                Integer destQueueHostId = hostIdTable.get(GPU_CATEGORIES.QUEUE.ordinal(), queueId);
                Integer destStreamHostId = hostIdTable.get(GPU_CATEGORIES.STREAM.ordinal(), hipStreamId.longValue());
                HostThread src = new HostThread(event.getTrace().getHostId(), srcHostId);
                HostThread destQueue = new HostThread(event.getTrace().getHostId(), destQueueHostId);
                HostThread destStream = new HostThread(event.getTrace().getHostId(), destStreamHostId);
                addArrow(ssb, hipEvent.getTimestamp().getValue(), timestamp, Math.toIntExact(eventDispatchId), src, destQueue);
                addArrow(ssb, hipEvent.getTimestamp().getValue(), timestamp, Math.toIntExact(eventDispatchId), src, destStream);
            }
        }
    }

    private void updateGpuGapState(ITmfStateSystemBuilder ssb, Long timestamp, Long gpuId, Long dispatchId) {
        int gpuQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU + gpuId.toString());
        boolean isNewGap = ssb.optQuarkRelative(gpuQuark, RocmStrings.EMPTY_STRING) == ITmfStateSystem.INVALID_ATTRIBUTE;
        int interQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, RocmStrings.EMPTY_STRING);
        int gapQuark = ssb.getQuarkRelativeAndAdd(interQuark, RocmStrings.GAP_ANALYSIS);
        int callStackQuark = ssb.getQuarkRelativeAndAdd(gapQuark, InstrumentedCallStackAnalysis.CALL_STACK);
        if (isNewGap) {
            ssb.pushAttribute(getTrace().getStartTime().getValue(), RocmStrings.IDLE, callStackQuark);
        }
        if (fCurrentKernelDispatched.contains(dispatchId)) {
            ssb.pushAttribute(timestamp, RocmStrings.IDLE, callStackQuark);
        } else {
            ssb.popAttribute(timestamp, callStackQuark);
        }
    }

    private static void addArrow(ITmfStateSystemBuilder ssb, Long startTime, Long endTime, int id,
            @NonNull HostThread src, @NonNull HostThread dest) {
        int edgeQuark = getAvailableEdgeQuark(ssb, startTime);

        Object edgeStateValue = new EdgeStateValue(id, src, dest);
        ssb.modifyAttribute(startTime, edgeStateValue, edgeQuark);
        ssb.modifyAttribute(endTime, (Object) null, edgeQuark);
    }

    private static int getAvailableEdgeQuark(ITmfStateSystemBuilder ssb, long startTime) {
        int edgeRoot = ssb.getQuarkAbsoluteAndAdd(RocmStrings.EDGES);
        List<@NonNull Integer> subQuarks = ssb.getSubAttributes(edgeRoot, false);

        for (int quark : subQuarks) {
            long start = ssb.getOngoingStartTime(quark);
            Object value = ssb.queryOngoing(quark);
            if (value == null && start <= startTime) {
                return quark;
            }
        }
        return ssb.getQuarkRelativeAndAdd(edgeRoot, Integer.toString(subQuarks.size()));
    }

    private void processApiEvent(ITmfEvent event, ITmfStateSystemBuilder ssb, int callStackQuark) {
        ITmfEventField content = event.getContent();
        long timestamp = event.getTimestamp().toNanos();
        String eventName = content.getFieldValue(String.class, RocmStrings.NAME);
        if (eventName == null) {
            ssb.popAttribute(timestamp, callStackQuark);
            return;
        }
        // Add CallStack Identifier (tid multiplied by API type)
        Long tid = content.getFieldValue(Long.class, RocmStrings.TID);
        if (tid != null && !fHostIdArrowsSystemTable.contains(tid, fApiId.getOrDefault(event.getName(), 4))) {
            int parentQuark = ssb.getParentAttributeQuark(callStackQuark);
            fHostIdArrowsSystemTable.put(tid, fApiId.getOrDefault(event.getName(), 4), fHostIdCounter++);
            int hostId = fHostIdArrowsSystemTable.get(tid, fApiId.getOrDefault(event.getName(), 4));
            ssb.modifyAttribute(getTrace().getStartTime().toNanos(), hostId, parentQuark);
        }
        // Add kernel launch request to the map to correlate kernel launches to streams
        /*if (eventName.equals("hipLaunchKernel_enter")) { //$NON-NLS-1$
            fHipKernelDispatchs.put(fHipDispatchCounter, event);
            fHipDispatchCounter += 1;
        }*/
        // Add event to API lane
        if (eventName.endsWith("_exit")) { //$NON-NLS-1$
            ssb.popAttribute(timestamp, callStackQuark);
        } else {
            ssb.pushAttribute(timestamp, eventName.substring(0, eventName.length()-6), callStackQuark);
        }
        // Add Thread to device association from hipSetDevice and hipGetDevice calls
        if (eventName.endsWith("_exit")) {
            eventName = eventName.substring(0, eventName.length() - 5);
        } else {
            eventName = eventName.substring(0, eventName.length() - 6);
        }
        if ((eventName.equals(RocmStrings.HIP_SET_DEVICE) || eventName.equals(RocmStrings.HIP_GET_DEVICE)) && tid != null) {
            String args = content.getFieldValue(String.class, "args");
            Pattern p = Pattern.compile("deviceId\\((\\d*)\\)"); //$NON-NLS-1$
            Matcher m = p.matcher(args);
            int hipGpuId = 0; // Default stream
            if (m.find()) {
                hipGpuId = Integer.parseInt(m.group(1));
            }
            int threadToDeviceQuark = ssb.getQuarkAbsoluteAndAdd("ThreadsToDevice", tid.toString());
            ssb.modifyAttribute(timestamp, hipGpuId, threadToDeviceQuark);
        }
    }

    private static int getCorrectQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event) {
        switch (event.getName()) {
        case RocmStrings.HSA_API:
        case RocmStrings.HIP_API:
        case RocmStrings.KFD_API:
            return getApiCallStackQuark(ssb, event);
        case RocmStrings.ASYNC_COPY:
        case RocmStrings.GPU_KERNEL:
        case RocmStrings.HCC_OPS:
        case RocmStrings.ROCTX:
            return getGpuActivityCallStackQuark(ssb, event);
        default:
            return -1;
        }
    }

    private static int getGpuActivityCallStackQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event) {
        if (event.getName().equals(RocmStrings.GPU_KERNEL)) {
            Long queueId = event.getContent().getFieldValue(Long.class, RocmStrings.QUEUE_ID);
            Long gpuId = event.getContent().getFieldValue(Long.class, RocmStrings.GPU_ID);
            if (queueId == null || gpuId == null) {
                return -1;
            }
            int gpuQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU + gpuId.toString());
            int queuesQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, RocmStrings.QUEUES);
            int queueQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, RocmStrings.QUEUE + Long.toString(queueId));
            int callStackQuark = ssb.getQuarkRelativeAndAdd(queueQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            return callStackQuark;
        } else if (event.getName().equals(RocmStrings.HCC_OPS)) {
            int gpuActivity = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU_ACTIVITY);
            int gpuQuark = ssb.getQuarkRelativeAndAdd(gpuActivity, RocmStrings.GPU_KERNELS);
            int callStackQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            return callStackQuark;
        }
        int copyQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.MEMORY);
        int tempQuark1 = ssb.getQuarkRelativeAndAdd(copyQuark, RocmStrings.EMPTY_STRING);
        int tempQuark2 = ssb.getQuarkRelativeAndAdd(tempQuark1, RocmStrings.MEMORY_TRANSFERS);
        int callStackQuark = ssb.getQuarkRelativeAndAdd(tempQuark2, InstrumentedCallStackAnalysis.CALL_STACK);
        return callStackQuark;
    }

    private static int getApiCallStackQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event) {
        int systemQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.SYSTEM);

        Long threadId = event.getContent().getFieldValue(Long.class, RocmStrings.TID);
        if (threadId == null) {
            threadId = 0l;
        }
        int threadQuark = ssb.getQuarkRelativeAndAdd(systemQuark, RocmStrings.THREAD + threadId.toString());
        int apiQuark = ssb.getQuarkRelativeAndAdd(threadQuark, event.getName().toUpperCase());
        int callStackQuark = ssb.getQuarkRelativeAndAdd(apiQuark, InstrumentedCallStackAnalysis.CALL_STACK);
        return callStackQuark;
    }

    private static Integer getHipStreamId(@NonNull ITmfEvent event) {
        String args = event.getContent().getFieldValue(String.class, RocmStrings.ARGS);
        Pattern p = Pattern.compile("stream\\((\\d*)\\)"); //$NON-NLS-1$
        Matcher m = p.matcher(args);
        int hipStreamId = 1; // Default stream
        if (m.find()) {
            hipStreamId = Integer.parseInt(m.group(1));
        }
        return hipStreamId;
    }

    private static int getHipStreamCallStackQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event, Long gpuId) {
        int gpuQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU + gpuId.toString());
        int hipStreamsQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, RocmStrings.STREAMS);
        int hipStreamId = getHipStreamId(event);
        int hipStreamQuark = ssb.getQuarkRelativeAndAdd(hipStreamsQuark, RocmStrings.STREAM + Integer.toString(hipStreamId));
        int callStackQuark = ssb.getQuarkRelativeAndAdd(hipStreamQuark, InstrumentedCallStackAnalysis.CALL_STACK);
        return callStackQuark;
    }

}
