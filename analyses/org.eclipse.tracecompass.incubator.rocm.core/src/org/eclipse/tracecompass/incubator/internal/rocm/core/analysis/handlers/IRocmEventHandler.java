package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmEventLayout;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public interface IRocmEventHandler {


    public void handleEvent(ITmfEvent event, ITmfStateSystemBuilder ssb, RocmEventLayout layout);
}
