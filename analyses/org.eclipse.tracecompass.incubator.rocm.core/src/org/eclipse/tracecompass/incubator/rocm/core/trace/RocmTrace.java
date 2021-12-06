package org.eclipse.tracecompass.incubator.rocm.core.trace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.rocm.core.Activator;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventType;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfBaseAspects;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.filter.ITmfFilter;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceWithPreDefinedEvents;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceContext;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfLocation;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfLocationInfo;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTraceValidationStatus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList.Builder;

public class RocmTrace extends CtfTmfTrace {

    private static final @NonNull Collection<@NonNull ITmfEventAspect<?>> ROCM_CTF_ASPECTS = ImmutableList.of(
            TmfBaseAspects.getTimestampAspect(),
            TmfBaseAspects.getEventTypeAspect(),
            TmfBaseAspects.getContentsAspect(),
            TmfBaseAspects.getTraceNameAspect());

    /** Collection of aspects, default values */
    private @NonNull Collection<ITmfEventAspect<?>> fAspects = ImmutableSet.copyOf(ROCM_CTF_ASPECTS);
    private static final int CONFIDENCE = 100;
    /**
     * This is a reduction factor to avoid overflows.
     */
    private static final int REDUCTION_FACTOR = 4096;

    /**
     * Constructor
     */
    public RocmTrace() {
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
        builder.add(createApiFunctionAspect(this));
        builder.addAll(ROCM_CTF_ASPECTS);
        builder.addAll(createCounterAspects(this));
        fAspects = builder.build();
    }


    private ITmfEventAspect<String> createApiFunctionAspect(ITmfTraceWithPreDefinedEvents trace) {
        ITmfContext context = seekEvent(new CtfLocation(new CtfLocationInfo(0L, 0L)));
        for (ITmfEventType eventType : trace.getContainedEventTypes()) {
            if (eventType.getName().endsWith("_api")) {
                while (true) {
                    ITmfEvent event = getNext(context);
                    ApiFunctionAspect.INSTANCE.addFunctionDeclaration(event);
                    if (!event.getName().endsWith("_name")) {
                        break;
                    }
                }
                break;
            }
        }
        return ApiFunctionAspect.INSTANCE;
    }
    private Collection<ITmfEventAspect<?>> createCounterAspects(ITmfTraceWithPreDefinedEvents trace) {
        ImmutableSet.Builder<ITmfEventAspect<?>> perfBuilder = new ImmutableSet.Builder<>();
        ITmfContext context = seekEvent(new CtfLocation(new CtfLocationInfo(0L, 0L)));

        for (ITmfEventType eventType : trace.getContainedEventTypes()) {
            if (eventType.getName().equals(RocmStrings.GPU_KERNEL)) {
                while (true) {
                    ITmfEvent event = getNext(context);
                    if (event.getName().equals(RocmStrings.GPU_KERNEL)) {
                        buildCounterAspectsFromEvent(perfBuilder, event);
                        break;
                    }
                }
                break;
            }
        }


        return perfBuilder.build();
    }

    private static void buildCounterAspectsFromEvent(ImmutableSet.Builder<ITmfEventAspect<?>> builder, ITmfEvent event) {
        List<String> blacklistFields = new ArrayList<>(List.of(
                RocmStrings.NAME, RocmStrings.ARGS, RocmStrings.KERNEL_NAME,
                RocmStrings.KERNEL_DISPATCH_ID, RocmStrings.GPU_ID,
                RocmStrings.QUEUE_ID, RocmStrings.TID, RocmStrings.PID,
                RocmStrings.SIGNAL, RocmStrings.OBJECT, RocmStrings.COMPLETE_TS,
                RocmStrings.DISPATCH_TS
        ));
        for (String fieldName: event.getContent().getFieldNames()) {
            if (blacklistFields.contains(fieldName) == false) {
                builder.add(new RocmCtfCounterAspect(fieldName, fieldName, GpuAspect.class));
            }
        }
    }

    @Override
    public int size() {
        Map<String, String> environment = getEnvironment();
        if (environment != null) {
            String size = environment.get("nb_events"); //$NON-NLS-1$
            if (size != null) {
                return (int) (Long.parseLong(size) / REDUCTION_FACTOR);
            }
        }
        return super.size();
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
