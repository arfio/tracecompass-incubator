package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.ApiEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.IRocmEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.OperationEventHandler;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

public class RocmCallStackStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.rocm.core.stateprovider.atomic"; //$NON-NLS-1$

    private final RocmEventLayout fLayout;
    private IRocmEventHandler fApiEventHandler;
    private IRocmEventHandler fOperationEventHandler;

    public RocmCallStackStateProvider(ITmfTrace trace, RocmEventLayout layout) {
        super(trace, ID);
        fLayout = layout;
        fApiEventHandler = new ApiEventHandler();
        fOperationEventHandler = new OperationEventHandler();
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new RocmCallStackStateProvider(getTrace(), fLayout);
    }


    @Override
    protected void eventHandle(ITmfEvent event) {
        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (event.getName().equals(fLayout.getHsaOperationBegin()) || event.getName().equals(fLayout.getHsaOperationEnd())) {
            fOperationEventHandler.handleEvent(event, ssb, fLayout);
        } else if (event.getName().equals(fLayout.getHipOperationBegin()) || event.getName().equals(fLayout.getHipOperationEnd())) {
            fOperationEventHandler.handleEvent(event, ssb, fLayout);
        } else if (event.getName().startsWith(fLayout.getHipPrefix()) || event.getName().startsWith(fLayout.getHsaPrefix())) {
            fApiEventHandler.handleEvent(event, ssb, fLayout);
        }
    }
}
