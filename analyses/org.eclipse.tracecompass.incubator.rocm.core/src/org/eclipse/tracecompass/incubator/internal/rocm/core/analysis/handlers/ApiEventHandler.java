package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

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
        String apiName = event.getName().substring(0, event.getName().indexOf('_'));
        Integer cid = event.getContent().getFieldValue(Integer.class, RocmStrings.CID);
        Integer functionID = ApiFunctionAspect.INSTANCE.getFunctionIDFromApiAndCid(apiName, cid);

        // Push the api function name to the call stack quark
        ssb.pushAttribute(timestamp, functionID, callStackQuark);
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

    /*public static String getArg(ITmfEventField content, int argPosition) {
        String args = content.getFieldValue(String.class, RocmStrings.ARGS);
        // Regex pattern to separate args in the event.
        Pattern p = Pattern.compile("(\\{[\\w=,\\s]*\\})|(([\\w*]+(\\s)?)+(\\<[,\\w\\s]*\\>)?)(\\([\\w\\s*,]*\\))?"); //$NON-NLS-1$
        Matcher m = p.matcher(args);
        for (int i = 0; i <= argPosition; i++) {
            m.find();
        }
        return m.group();
    }*/

    public static String getArg(ITmfEventField content, int argPosition) {
        String args = content.getFieldValue(String.class, RocmStrings.ARGS);
        if (args == null) {
            return RocmStrings.EMPTY_STRING;
        }
        int currentIndex = 0, argIndex = -1, currentPosition = 0;
        int depth = 0; // '(' increases depth, ')' decreases it
        while (currentIndex < args.length() && currentPosition <= argPosition) {
            if (args.charAt(currentIndex) == ',' && depth == 0) {
                currentPosition += 1;
            }
            if (args.charAt(currentIndex) == '(' || args.charAt(currentIndex) == '{' || args.charAt(currentIndex) == '<') {
                depth += 1;
            }
            if (args.charAt(currentIndex) == ')' || args.charAt(currentIndex) == '}' || args.charAt(currentIndex) == '>') {
                depth -= 1;
            }
            if (currentPosition == argPosition && argIndex < 0) {
                if (argPosition > 0) { // if there is a comma
                    argIndex = currentIndex + 1; // do not select it
                } else {
                    argIndex = currentIndex;
                }
            }
            currentIndex += 1;
        }
        if (currentPosition > argPosition) {
            currentIndex -= 1; // move back before the comma
        }
        if (argIndex >= 0) {
            return args.substring(argIndex, currentIndex).strip();
        }
        return RocmStrings.EMPTY_STRING;
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
