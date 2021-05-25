package org.eclipse.tracecompass.incubator.internal.benchmark.sample.core.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackStateProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEventField;

public class MonotoneStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.benchmark.sample.core.stateprovider.monotone"; //$NON-NLS-1$
    private final Map<String, List<String>> apiTables = new HashMap<>();
    private final Map<Integer, Integer> tidToQuark = new HashMap<>();

    /**
     * @param trace trace
     */
    public MonotoneStateProvider(@NonNull ITmfTrace trace) {
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
        // Add event
        long timestamp = event.getTimestamp().toNanos();
        if (event.getName().endsWith("_end")) {
            ssb.popAttribute(timestamp, quark);
        } else {
            List<String> apiTable = apiTables.get(event.getName().substring(0, 3));
            if (apiTable == null) {
                return;
            }
            Integer cid = content.getFieldValue(Integer.class, "cid");
            if (cid == null) {
                return;
            }
            String eventName = apiTable.get(cid);
            ssb.pushAttribute(timestamp, eventName, quark);
        }
    }

    private int getCorrectQuark(ITmfStateSystemBuilder ssb, @NonNull ITmfEvent event, @NonNull ITmfEventField content) {
        String pid = content.getFieldValue(String.class, "pid");
        String tid = content.getFieldValue(String.class, "tid");
        int callStackQuark;
        switch (event.getName()) {
        case "hsa_api_table":
            processApiTable(event, content);
            return -1;
        //$FALL-THROUGH$
        case "hsa_begin":
        case "hsa_end":
            Integer quark = tidToQuark.get(Integer.valueOf(tid));
            if (quark != null) {
                return quark;
            }
            int processQuark = ssb.getQuarkAbsoluteAndAdd(CallStackStateProvider.PROCESSES, pid);
            int threadQuark = ssb.getQuarkRelativeAndAdd(processQuark, tid);
            callStackQuark = ssb.getQuarkRelativeAndAdd(threadQuark, InstrumentedCallStackAnalysis.CALL_STACK);
            tidToQuark.put(Integer.valueOf(tid), callStackQuark);
            return callStackQuark;
        default:
            return -1;
        }
    }

    private void processApiTable(@NonNull ITmfEvent event, @NonNull ITmfEventField content) {
        List<String> apiTable = apiTables.get(event.getName().substring(0, 3));
        if (apiTable == null) {
            apiTable = new ArrayList<>();
            apiTables.put(event.getName().substring(0, 3), apiTable);
        }
        CtfTmfEventField[] eventTable = (CtfTmfEventField[]) content.getField("api_table").getValue();
        for (int i = 0; i < eventTable.length; i++) {
            apiTable.add((String) eventTable[i].getValue());
        }
    }

}
