package org.eclipse.tracecompass.incubator.benchmark.sample.core.trace;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.benchmark.sample.core.Activator;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.filter.ITmfFilter;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceContext;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTraceValidationStatus;

public class MonotoneTrace extends CtfTmfTrace {

    private static final int CONFIDENCE = 100;

    /**
     * Constructor
     */
    public MonotoneTrace() {
        super();
    }

    @Override
    public Iterable<ITmfEventAspect<?>> getEventAspects() {
        return CTF_ASPECTS;
    }

    @Override
    public @Nullable IStatus validate(final @Nullable IProject project, final @Nullable String path) {
        IStatus status = super.validate(project, path);
        if (status instanceof CtfTraceValidationStatus) {
            Map<String, String> environment = ((CtfTraceValidationStatus) status).getEnvironment();
            String domain = environment.get("tracer_name"); //$NON-NLS-1$
            if (domain == null || !domain.equals("\"barectf\"")) { //$NON-NLS-1$
                return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "This trace is not a rocm trace"); //$NON-NLS-1$
            }
            return new TraceValidationStatus(CONFIDENCE, Activator.PLUGIN_ID);
        }
        return status;
    }

    @Override
    public TmfTraceContext createTraceContext(TmfTimeRange selection, TmfTimeRange windowRange, @Nullable IFile editorFile, @Nullable ITmfFilter filter) {
        return new TmfTraceContext(selection, windowRange, editorFile, filter);
    }
}
