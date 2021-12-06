package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.internal.rocm.core.Activator;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.dependency.HipApiHipActivityDependencyMaker;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.dependency.IDependencyMaker;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.ApiEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.GpuEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.HipActivityEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.HsaActivityEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.HsaKernelEventHandler;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers.RoctxEventHandler;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventType;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceWithPreDefinedEvents;
import com.google.common.collect.ImmutableMap;

/**
 * @author Arnaud Fiorini
 *
 */
public class RocmCallStackStateProvider extends AbstractTmfStateProvider {

    private static final String ID = "org.eclipse.tracecompass.incubator.rocm.core.stateprovider.interval"; //$NON-NLS-1$
    private IDependencyMaker fDependencyMaker;
    private Map<String, GpuEventHandler> fEventNames;

    /**
     * @param trace trace to follow
     */
    public RocmCallStackStateProvider(@NonNull ITmfTrace trace) {
        super(trace, ID);
        fDependencyMaker = getDependencyMaker((ITmfTraceWithPreDefinedEvents) trace);
        fEventNames = buildEventNames();
    }


    private Map<String, GpuEventHandler> buildEventNames() {
        ImmutableMap.Builder<String, GpuEventHandler> builder = ImmutableMap.builder();

        builder.put(RocmStrings.HIP_API, new ApiEventHandler(this));
        builder.put(RocmStrings.HSA_API, new ApiEventHandler(this));
        builder.put(RocmStrings.KFD_API, new ApiEventHandler(this));
        builder.put(RocmStrings.HIP_ACTIVITY, new HipActivityEventHandler(this));
        builder.put(RocmStrings.ROCTX, new RoctxEventHandler(this));
        builder.put(RocmStrings.KERNEL_EVENT, new HsaKernelEventHandler(this));
        builder.put(RocmStrings.HSA_ACTIVITY, new HsaActivityEventHandler(this));

        return builder.build();
    }

    private static IDependencyMaker getDependencyMaker(ITmfTraceWithPreDefinedEvents trace) {
        IDependencyMaker dependencyMaker = null;
        for (ITmfEventType eventType : (trace).getContainedEventTypes()) {
            if (eventType.getName().equals(RocmStrings.HIP_ACTIVITY)) {
                dependencyMaker = new HipApiHipActivityDependencyMaker();
                break;
            }
        }
        return dependencyMaker;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new RocmCallStackStateProvider(getTrace());
    }

    @Override
    protected void eventHandle(@NonNull ITmfEvent event) {
        final String eventName = event.getName();
        final ITmfStateSystemBuilder ssb = NonNullUtils.checkNotNull(getStateSystemBuilder());
        try {
            GpuEventHandler handler = fEventNames.get(eventName);
            if (handler != null) {
                handler.handleEvent(ssb, event);
            }
        } catch (AttributeNotFoundException e) {
            Activator.getInstance().logError("Exception while building the RocmCallStack state system", e); //$NON-NLS-1$
        }
        if (fDependencyMaker != null) {
            fDependencyMaker.processEvent(event, ssb);
        }
        return;
    }

    /**
     * Accessor for the current dependency maker.
     *
     * The dependency maker is instantiated in the constructor depending
     * the event types present in the rocm trace.
     *
     * @return dependency maker
     */
    public IDependencyMaker getDependencyMaker() {
        return fDependencyMaker;
    }
}
