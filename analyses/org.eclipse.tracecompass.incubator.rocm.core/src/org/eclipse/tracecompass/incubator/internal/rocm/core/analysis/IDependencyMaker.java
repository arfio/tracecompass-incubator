package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public interface IDependencyMaker {

    /*private final Map<Long, ITmfEvent> fApiKernelDispatch = new HashMap<>();
    private final Map<Long, ITmfEvent> fApiMemCopy = new HashMap<>();
    private final Map<Long, ITmfEvent> fSynchronizeActivity = new HashMap<>();

    private final List<Long> fKernelDispatchActivity = new LinkedList<>();
    private final List<Long> fMemCopyActivity = new LinkedList<>();
    private final List<Long> fApiDeviceSynchronize = new LinkedList<>();

    private boolean fHipApiHsaActivityDependency;
    private boolean fHipApiHipActivityDependency;
    private boolean fHsaApiHsaActivityDependency;*/

    /**
     * @param event
     * @param ssb
     */
    public void processEvent(ITmfEvent event, ITmfStateSystemBuilder ssb);
}
