package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.rocm.core.trace.RocmTrace;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

public class RocmMetadataStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.rocm.core.stateprovider.functionname"; //$NON-NLS-1$

    public static final String FUNCTION_NAMES = "Function Names";

    public RocmMetadataStateProvider(@NonNull ITmfTrace trace) {
        super(trace, ID);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new RocmCallStackStateProvider(getTrace());
    }

    @Override
    protected void eventHandle(@NonNull ITmfEvent event) {
        if (event.getName().endsWith("function_name")) {
            ITmfStateSystemBuilder ssb = getStateSystemBuilder();
            if (ssb == null) {
                return;
            }
            int functionNameQuark = ssb.getQuarkAbsoluteAndAdd(FUNCTION_NAMES);
            int apiQuark = ssb.getQuarkRelativeAndAdd(functionNameQuark,
                    ((Integer) ((RocmTrace) event.getTrace()).getApiId(event.getName().split("_")[0] + "_api")).toString());
            String functionName = event.getContent().getFieldValue(String.class, RocmStrings.NAME);
            Integer cid = event.getContent().getFieldValue(Integer.class, RocmStrings.CORRELATION_ID);
            if (functionName == null || cid == null) {
                return;
            }
            ssb.modifyAttribute(ssb.getStartTime() + cid, functionName, apiQuark);
        }
        if (event.getName().equals("counters")) {
            ITmfStateSystemBuilder ssb = getStateSystemBuilder();
            if (ssb == null) {
                return;
            }
            int counterNameQuark = ssb.getQuarkAbsoluteAndAdd("Counters Name");
            String counterName = event.getContent().getFieldValue(String.class, RocmStrings.NAME);
            Integer counterId = event.getContent().getFieldValue(Integer.class, "id");
            if (counterName == null || counterId == null) {
                return;
            }
            ssb.modifyAttribute(ssb.getStartTime() + counterId, counterName, counterNameQuark);
        }
    }

    public static int getFunctionId(@NonNull ITmfEvent event) {
        int nApi = ((RocmTrace) event.getTrace()).getNApi();
        Integer cid = event.getContent().getFieldValue(Integer.class, RocmStrings.CID);
        if (cid == null) {
            cid = event.getContent().getFieldValue(Integer.class, RocmStrings.CORRELATION_ID);
        }
        if (cid == null) {
            return -1;
        }
        int apiId;
        if (event.getName().endsWith("function_name")) {
            apiId = ((RocmTrace) event.getTrace()).getApiId(event.getName().split("_")[0] + "_api");
        } else {
            apiId = ((RocmTrace) event.getTrace()).getApiId(event.getName());
        }
        return cid * nApi + apiId;
    }

}
