package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackStateProvider;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class RoctxEventHandler extends GpuEventHandler {

    public RoctxEventHandler(RocmCallStackStateProvider stateProvider) {
        super(stateProvider);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ssb, ITmfEvent event) throws AttributeNotFoundException {
        // Select the correct quark
        int systemQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.SYSTEM);
        Long threadId = event.getContent().getFieldValue(Long.class, RocmStrings.TID);
        if (threadId == null) {
            threadId = 0l;
        }
        int threadQuark = ssb.getQuarkRelativeAndAdd(systemQuark, RocmStrings.THREAD + threadId.toString());
        int apiQuark = ssb.getQuarkRelativeAndAdd(threadQuark, "RocTX");
        int callStackQuark = ssb.getQuarkRelativeAndAdd(apiQuark, InstrumentedCallStackAnalysis.CALL_STACK);
        // Push message
        Long timestamp = event.getTimestamp().getValue();
        String message = event.getContent().getFieldValue(String.class, RocmStrings.MESSAGE);
        if (message == null || message.equals(RocmStrings.EMPTY_STRING)) {
            ssb.popAttribute(timestamp, callStackQuark);
            return;
        }
        ssb.pushAttribute(timestamp, message, callStackQuark);
    }

}
