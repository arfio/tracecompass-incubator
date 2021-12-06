package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackStateProvider;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.incubator.rocm.core.trace.ApiFunctionAspect;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider.FutureEventType;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;

/**
 * @author Arnaud Fiorini
 *
 */
public class ApiEventHandler extends GpuEventHandler {

    public ApiEventHandler(RocmCallStackStateProvider stateProvider) {
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
        int apiQuark = ssb.getQuarkRelativeAndAdd(threadQuark, event.getName().toUpperCase());
        int callStackQuark = ssb.getQuarkRelativeAndAdd(apiQuark, InstrumentedCallStackAnalysis.CALL_STACK);

        // Retrieve event information
        ITmfEventField content = event.getContent();
        Long timestamp = event.getTimestamp().toNanos();
        String eventName = getFunctionApiName(event);

        // Push the api function name to the call stack quark
        ssb.pushAttribute(timestamp, eventName, callStackQuark);
        Long timestampEnd = content.getFieldValue(Long.class, RocmStrings.END);

        if (timestampEnd != null) {
            fStateProvider.addFutureEvent(((CtfTmfTrace) event.getTrace()).timestampCyclesToNanos(timestampEnd),
                    timestampEnd, callStackQuark, FutureEventType.POP);
        }

        // Get and add HostThreadIdentifier if necessary
        Integer tid = content.getFieldValue(Integer.class, RocmStrings.TID);
        if (tid == null) {
            return;
        }
        HostThreadIdentifier hostThreadIdentifier = new HostThreadIdentifier(event.getName(), tid);
        addHostIdToStateSystemIfNotDefined(ssb, event.getTrace(), hostThreadIdentifier, callStackQuark);
    }

    public static String getArg(ITmfEventField content, int argPosition) {
        String args = content.getFieldValue(String.class, RocmStrings.ARGS);
        // Regex pattern to separate args in the event.
        Pattern p = Pattern.compile("(\\{[\\w=,\\s]*\\})|(([\\w*]+(\\s)?)+(\\<[,\\w\\s]*\\>)?)(\\([\\w\\s*,]*\\))?"); //$NON-NLS-1$
        Matcher m = p.matcher(args);
        for (int i = 0; i <= argPosition; i++) {
            m.find();
        }
        return m.group();
    }

    public static String getFunctionApiName(ITmfEvent event) {
        Object apiFunctionAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), ApiFunctionAspect.class, event);
        if (apiFunctionAspect == null) {
            // Api function not found, ignore this event.
            return RocmStrings.EMPTY_STRING;
        }
        return (String) apiFunctionAspect;
    }

}
