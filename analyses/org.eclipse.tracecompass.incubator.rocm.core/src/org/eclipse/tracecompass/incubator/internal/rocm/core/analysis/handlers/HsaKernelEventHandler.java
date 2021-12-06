package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackStateProvider;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.HostThreadIdentifier.KERNEL_CATEGORY;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class HsaKernelEventHandler extends GpuEventHandler {

    public HsaKernelEventHandler(RocmCallStackStateProvider stateProvider) {
        super(stateProvider);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ssb, ITmfEvent event) throws AttributeNotFoundException {
        String kernelName = event.getContent().getFieldValue(String.class, RocmStrings.KERNEL_NAME);
        Long queueId = event.getContent().getFieldValue(Long.class, RocmStrings.QUEUE_ID);
        Long gpuId = event.getContent().getFieldValue(Long.class, RocmStrings.GPU_ID);
        if (queueId == null || gpuId == null) {
                return;
        }
        int gpuQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.GPU + gpuId.toString());
        int queuesQuark = ssb.getQuarkRelativeAndAdd(gpuQuark, RocmStrings.QUEUES);
        int queueQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, RocmStrings.QUEUE + Long.toString(queueId));
        int callStackQuark = ssb.getQuarkRelativeAndAdd(queueQuark, InstrumentedCallStackAnalysis.CALL_STACK);
        Long timestamp = event.getTimestamp().toNanos();

        //Long correlationId = content.getFieldValue(Long.class, RocmStrings.CORRELATION_ID);
        // Placeholder value in case we do not match any api event.
        //IDependencyMaker dependencyMaker = fStateProvider.getDependencyMaker();
        /*if (correlationId != null && dependencyMaker != null) {
            Map<Long, ITmfEvent> apiEventCorrelationMap = dependencyMaker.getApiEventCorrelationMap();
            ITmfEvent apiEvent = apiEventCorrelationMap.get(correlationId);
            if (apiEvent != null) {
                hipStreamId = Integer.parseInt(ApiEventHandler.getArg(apiEvent.getContent(), 4));
                gpuId = content.getFieldValue(Long.class, RocmStrings.DEVICE_ID);
                hipStreamCallStackQuark = getHipStreamCallStackQuark(ssb, apiEvent, gpuId);
            }
        }*/

        Long timestampEnd = GpuEventHandler.getEndTime(event);
        if (timestampEnd != null) {
            pushParallelActivityOnCallStack(ssb, callStackQuark, kernelName, timestamp, timestampEnd);
        }
        // Add Host Thread Identifier for dependency arrows
        HostThreadIdentifier queueHostThreadIdentifier = new HostThreadIdentifier(queueId.intValue(), KERNEL_CATEGORY.QUEUE, gpuId.intValue());
        addHostIdToStateSystemIfNotDefined(ssb, event.getTrace(), queueHostThreadIdentifier, callStackQuark);
    }

}
