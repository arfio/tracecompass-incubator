package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmEventLayout;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class ApiEventHandler implements IRocmEventHandler {

    private boolean fIsThreadIdProvidedHSA = false;
    private boolean fIsThreadIdProvidedHIP = false;

    private static void provideThreadId(ITmfEvent event, ITmfStateSystemBuilder ssb, int quark, RocmEventLayout layout) {
        Integer tid = event.getContent().getFieldValue(Integer.class, layout.fieldThreadId());
        ssb.modifyAttribute(event.getTimestamp().getValue(), tid, quark);
    }

    @Override
    public void handleEvent(ITmfEvent event, ITmfStateSystemBuilder ssb, RocmEventLayout layout) {
        try {
            addEventToOperationQueue(event, ssb, layout);
        } catch (StateSystemDisposedException e) {
            e.printStackTrace();
        }
        Integer tid = event.getContent().getFieldValue(Integer.class, layout.fieldThreadId());
        if (tid == null) {
            return;
        }
        String apiType = event.getName().replace("_", "").substring(0, 3).toUpperCase(); //$NON-NLS-1$ //$NON-NLS-2$
        int processQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, tid.toString());
        boolean isEndEvent = false;

        int callStackQuark = ITmfStateSystem.INVALID_ATTRIBUTE;
        if (apiType.equals("HIP")) { //$NON-NLS-1$
            int apiQuark = ssb.getQuarkRelativeAndAdd(processQuark, "HIP"); //$NON-NLS-1$
            callStackQuark = ssb.getQuarkRelativeAndAdd(apiQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            isEndEvent = event.getName().endsWith(layout.getHipEndSuffix());
            if (!fIsThreadIdProvidedHIP) {
                provideThreadId(event, ssb, processQuark, layout);
                provideThreadId(event, ssb, apiQuark, layout);
                fIsThreadIdProvidedHIP = true;
            }
        } else if (apiType.equals("HSA")) { //$NON-NLS-1$
            if (event.getName().equals("hsa_handle_type")) { //$NON-NLS-1$
                return;
            }
            int apiQuark = ssb.getQuarkRelativeAndAdd(processQuark, "HSA"); //$NON-NLS-1$
            callStackQuark = ssb.getQuarkRelativeAndAdd(apiQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            isEndEvent = event.getName().endsWith(layout.getHsaEndSuffix());
            if (!fIsThreadIdProvidedHSA) {
                provideThreadId(event, ssb, processQuark, layout);
                provideThreadId(event, ssb, apiQuark, layout);
                fIsThreadIdProvidedHSA = true;
            }
        }
        if (isEndEvent) {
            ssb.popAttribute(event.getTimestamp().getValue(), callStackQuark);
            return;
        }
        Integer trimIndex = event.getName().length() - (5 + (apiType.equals("HIP") ? 0 : 1)); // Underscore present or not //$NON-NLS-1$
        ssb.pushAttribute(event.getTimestamp().getValue(), event.getName().substring(0, trimIndex), callStackQuark);
    }

    private static void addEventToOperationQueue(ITmfEvent event, ITmfStateSystemBuilder ssb, RocmEventLayout layout) throws StateSystemDisposedException {
        Integer queueId = event.getContent().getFieldValue(Integer.class, layout.fieldQueueId());
        if (queueId == null) {
            return;
        }
        int queuesQuark = ssb.getQuarkAbsoluteAndAdd("Queues", queueId.toString());
        long ts = event.getTimestamp().getValue();
        Long correlationId = event.getContent().getFieldValue(Long.class, layout.fieldCorrelationId());
        if (event.getName().equals(layout.hipMemcpyBegin())) {
            int depth = 1;
            int subQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, String.valueOf(depth));
            // While there is already activity on the quark
            while (ssb.querySingleState(ts, subQuark).getValue() != null) {
                depth += 1;
                subQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, String.valueOf(depth));
            }
            // Register event name in the call stack
            ssb.modifyAttribute(ts, correlationId, subQuark);
        } else if (event.getName().equals(layout.hipMemcpyEnd())) {
            int depth = 1;
            int subQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, String.valueOf(depth));
            // While there is already activity on the quark
            while (ssb.querySingleState(ts, subQuark).getValue() != correlationId) {
                depth += 1;
                subQuark = ssb.getQuarkRelativeAndAdd(queuesQuark, String.valueOf(depth));
            }
            ssb.modifyAttribute(ts, null, subQuark);
        }
    }
}
