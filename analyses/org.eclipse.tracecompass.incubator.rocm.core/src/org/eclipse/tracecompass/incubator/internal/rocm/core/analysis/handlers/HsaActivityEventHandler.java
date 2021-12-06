package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackStateProvider;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider.FutureEventType;

public class HsaActivityEventHandler extends GpuEventHandler {

    public HsaActivityEventHandler(RocmCallStackStateProvider stateProvider) {
        super(stateProvider);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ssb, ITmfEvent event) throws AttributeNotFoundException {
        int copyQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, RocmStrings.MEMORY);
        int tempQuark1 = ssb.getQuarkRelativeAndAdd(copyQuark, RocmStrings.EMPTY_STRING);
        int tempQuark2 = ssb.getQuarkRelativeAndAdd(tempQuark1, RocmStrings.MEMORY_TRANSFERS);
        int callStackQuark = ssb.getQuarkRelativeAndAdd(tempQuark2, InstrumentedCallStackAnalysis.CALL_STACK);

        Long timestamp = event.getTimestamp().toNanos();
        Long timestampEnd = GpuEventHandler.getEndTime(event);
        ssb.pushAttribute(timestamp, RocmStrings.COPY, callStackQuark);
        if (timestampEnd != null) {
            fStateProvider.addFutureEvent(timestampEnd, null, callStackQuark, FutureEventType.POP);
        }
        // Add CallStack Identifier (tid equivalent) for the memory quark
        HostThreadIdentifier hostThreadIdentifier = new HostThreadIdentifier();
        addHostIdToStateSystemIfNotDefined(ssb, event.getTrace(), hostThreadIdentifier, callStackQuark);
    }

}
