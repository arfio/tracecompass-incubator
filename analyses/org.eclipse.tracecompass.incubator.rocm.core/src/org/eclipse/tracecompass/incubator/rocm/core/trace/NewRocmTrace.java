package org.eclipse.tracecompass.incubator.rocm.core.trace;

import java.util.Collection;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.rocm.core.Activator;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfBaseAspects;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTraceValidationStatus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList.Builder;

public class NewRocmTrace extends CtfTmfTrace {

    private static final @NonNull Collection<@NonNull ITmfEventAspect<?>> ROCM_CTF_ASPECTS = ImmutableList.of(
            TmfBaseAspects.getTimestampAspect(),
            TmfBaseAspects.getEventTypeAspect(),
            TmfBaseAspects.getContentsAspect(),
            TmfBaseAspects.getTraceNameAspect());

    /** Collection of aspects, default values */
    private @NonNull Collection<ITmfEventAspect<?>> fAspects = ImmutableSet.copyOf(ROCM_CTF_ASPECTS);

    private static final int CONFIDENCE = 100;

    /**
     * Constructor
     */
    public NewRocmTrace() {
        super();
    }

    @Override
    public Iterable<ITmfEventAspect<?>> getEventAspects() {
        return fAspects;
    }

    @Override
    public void initTrace(final IResource resource, final String path,
            final Class<? extends ITmfEvent> eventType) throws TmfTraceException {
        super.initTrace(resource, path, eventType);

        ImmutableList.Builder<ITmfEventAspect<?>> builder = new Builder<>();
        builder.add(GpuAspect.INSTANCE);
        builder.addAll(ROCM_CTF_ASPECTS);
        fAspects = builder.build();
    }

    @Override
    public @Nullable IStatus validate(final @Nullable IProject project, final @Nullable String path) {
        IStatus status = super.validate(project, path);
        if (status instanceof CtfTraceValidationStatus) {
            Map<String, String> environment = ((CtfTraceValidationStatus) status).getEnvironment();
            /* Make sure the domain is "kernel" in the trace's env vars */
            String domain = environment.get("tracer_name"); //$NON-NLS-1$
            boolean isRocTracerVersionPresent = environment.get("roc_tracer_version") != null; //$NON-NLS-1$
            if (domain == null || !domain.equals("\"barectf\"")) { //$NON-NLS-1$
                return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
                        "This trace was not recognized as a ROCm trace. You can update your rocprofiler version or you can change manually the tracer name to \"rocprof\" in the metadata file to force the validation."); //$NON-NLS-1$
            }
            if (isRocTracerVersionPresent) {
                return new TraceValidationStatus(CONFIDENCE, Activator.PLUGIN_ID);
            }
        }
        return status;
    }
}
