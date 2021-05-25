package org.eclipse.tracecompass.incubator.internal.benchmark.sample.core.analysis;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * @author Arnaud Fiorini
 *
 * State provider for a CTF trace storing interval event
 */
public class IntervalStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.benchmark.sample.core.stateprovider.interval"; //$NON-NLS-1$
    private final Queue<FutureEvent> fFutureEvents = new PriorityQueue<>(Comparator.comparingLong(FutureEvent::getTime));

    private static final class FutureEvent {
        private final long fTime;
        private final @Nullable Object fValue;
        private final int fQuark;
        private final FutureEventType fType;

        public FutureEvent(long time, @Nullable Object futureValue, int quark, FutureEventType type) {
            fTime = time;
            fValue = futureValue;
            fQuark = quark;
            fType = type;
        }

        public long getTime() {
            return fTime;
        }
    }

    /**
     * @param trace trace to follow
     */
    public IntervalStateProvider(@NonNull ITmfTrace trace) {
        super(trace, ID);
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new IntervalStateProvider(getTrace());
    }

    @Override
    public void done() {
        ITmfStateSystemBuilder stateSystemBuilder = getStateSystemBuilder();
        if (stateSystemBuilder == null) {
            return;
        }

        while (!fFutureEvents.isEmpty()) {
            FutureEvent futureEvent = fFutureEvents.peek();
            while (futureEvent != null) {
                futureEvent = fFutureEvents.poll();
                if (futureEvent != null) {
                    applyFutureEvent(futureEvent, stateSystemBuilder);
                }
                futureEvent = fFutureEvents.peek();
            }
        }
    }

    @Override
    protected void eventHandle(@NonNull ITmfEvent event) {
        ITmfEventField content = event.getContent();
        if (content == null) {
            return;
        }
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }
        int quark = getCorrectQuark(ssb, event, content);
        if (quark == -1) {
            return;
        }

        Long timestamp = content.getFieldValue(Long.class, "context._ts");
        if (timestamp == null) {
            return;
        }
        handleFutureEvents(ssb, timestamp);
        ssb.pushAttribute(timestamp, content.getFieldValue(String.class, "name"), quark);
        // Pop attribute
        Long duration = content.getFieldValue(Long.class, "dur");
        if (duration != null) {
            Long timestampEnd = timestamp + duration;
            fFutureEvents.add(new FutureEvent(timestampEnd, content.getFieldValue(String.class, "name"), quark, FutureEventType.POP));
        } else {
            // Instantaneous event
            ssb.popAttribute(timestamp, quark);
        }
    }

    private void handleFutureEvents(ITmfStateSystemBuilder ssb, long currentTime) {
        FutureEvent futureEvent = fFutureEvents.peek();
        while (futureEvent != null && (currentTime >= futureEvent.fTime)) {
            futureEvent = fFutureEvents.poll();
            if (futureEvent != null) {
                applyFutureEvent(futureEvent, ssb);
            }
            futureEvent = fFutureEvents.peek();
        }
    }

    private static void applyFutureEvent(FutureEvent futureEvent, ITmfStateSystemBuilder stateSystemBuilder) {
        switch (futureEvent.fType) {
        case MODIFICATION:
            stateSystemBuilder.modifyAttribute(futureEvent.fTime, futureEvent.fValue, futureEvent.fQuark);
            break;
        case PUSH:
            stateSystemBuilder.pushAttribute(futureEvent.fTime, futureEvent.fValue, futureEvent.fQuark);
            break;
        case POP:
            stateSystemBuilder.popAttributeObject(futureEvent.fTime, futureEvent.fQuark);
            break;
        default:
            break;
        }
    }

    private static int getCorrectQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event, @NonNull ITmfEventField content) {
        String pid = content.getFieldValue(String.class, "context._vpid");
        String tid = content.getFieldValue(String.class, "context._vtid");
        int callStackQuark;
        switch (event.getName()) {
        case "lttng:device":
            int devicesQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, "Devices");
            String deviceId = content.getFieldValue(String.class, "did");
            int deviceQuark = ssb.getQuarkRelativeAndAdd(devicesQuark, deviceId);
            callStackQuark = ssb.getQuarkRelativeAndAdd(deviceQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            return callStackQuark;
        case "lttng:host":
            int processQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, pid);
            int threadQuark = ssb.getQuarkRelativeAndAdd(processQuark, tid);
            callStackQuark = ssb.getQuarkRelativeAndAdd(threadQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            return callStackQuark;
        default:
            return -1;
        }
    }

}
