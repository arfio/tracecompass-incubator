package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class HipApiHipActivityDependencyMaker implements IDependencyMaker {

    private final Map<Long, ITmfEvent> fApiKernelDispatch = new HashMap<>();
    /*private final Map<Long, ITmfEvent> fApiMemCopy = new HashMap<>();
    private final Map<Long, ITmfEvent> fSynchronizeActivity = new HashMap<>();

    private final List<Long> fKernelDispatchActivity = new LinkedList<>();
    private final List<Long> fMemCopyActivity = new LinkedList<>();
    private final List<Long> fApiDeviceSynchronize = new LinkedList<>();*/


    @Override
    public void processEvent(ITmfEvent event, ITmfStateSystemBuilder ssb) {
        switch(event.getName()) {
        case "hipLaunchKernel_enter":
            addKernelDispatch(event);
            return;

        case "hcc_ops":
            String name = event.getContent().getFieldValue(String.class, "name");
            if (name != null && name != "<barrier packet>") {
                addKernelDependency(event, ssb);
            }
            return;
        default:
            return;
        }
        // Correlate kernel dispatch to kernel
        if (fApiKernelDispatch.size() == 0) {
            return;
        }
        ITmfEvent hipEvent = fApiKernelDispatch.get(Collections.min(fApiKernelDispatch.keySet()));
        if (hipEvent == null) {
            return;
        }
        // Add Kernel Launch Dependency
        Long tid = hipEvent.getContent().getFieldValue(Long.class, RocmStrings.TID);
        Long corrId = hipEvent.getContent().getFieldValue(Long.class, RocmStrings.CORR_ID);
        int threadToDeviceQuark = ssb.getQuarkAbsolute("ThreadsToDevice", tid.toString());
        //Long gpuId = ssb.querySingleState(timestamp, )
        if (tid != null && corrId != null) {

            /*Integer srcHostId = fHostIdArrowsSystemTable.get(tid, fApiId.getOrDefault(hipEvent.getName(), 4));
            Integer destQueueHostId = hostIdTable.get(GPU_CATEGORIES.QUEUE.ordinal(), queueId);
            Integer destStreamHostId = hostIdTable.get(GPU_CATEGORIES.STREAM.ordinal(), hipStreamId.longValue());
            HostThread src = new HostThread(event.getTrace().getHostId(), srcHostId);
            HostThread destQueue = new HostThread(event.getTrace().getHostId(), destQueueHostId);
            HostThread destStream = new HostThread(event.getTrace().getHostId(), destStreamHostId);
            addArrow(ssb, hipEvent.getTimestamp().getValue(), timestamp, Math.toIntExact(eventDispatchId), src, destQueue);
            addArrow(ssb, hipEvent.getTimestamp().getValue(), timestamp, Math.toIntExact(eventDispatchId), src, destStream);*/
        }
    }

    private void addKernelDispatch(ITmfEvent event) {
        Long corrId = event.getContent().getFieldValue(Long.class, RocmStrings.CORR_ID);
        if (corrId != null) {
            fApiKernelDispatch.put(corrId, event);
        }
    }

    private void addKernelDependency(ITmfEvent event, ITmfStateSystemBuilder ssb) {
        Long corrId = event.getContent().getFieldValue(Long.class, RocmStrings.CORR_ID);
        Integer gpuId = event.getContent().getFieldValue(Integer.class, RocmStrings.GPU_ID);
        if (corrId != null) {
            HostThreadIdentifier hostTid = new HostThreadIdentifier()
            ITmfEvent hipEvent = fApiKernelDispatch.get(corrId);
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
