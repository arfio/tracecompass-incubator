package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfDeviceAspect;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;

public final class GpuAspect extends TmfDeviceAspect {

    /** The singleton instance */
    public static final GpuAspect INSTANCE = new GpuAspect();

    private GpuAspect() {
    }

    @Override
    public final String getName() {
        return Messages.getMessage(Messages.AspectName_GPU);
    }

    @Override
    public final String getHelpText() {
        return Messages.getMessage(Messages.AspectHelpText_GPU);
    }

    /**
     * Returns the GPU number of the GPU on which this event was executed or
     * {@code null} if the GPU is not available for an event.
     */
    @Override
    public @Nullable Integer resolve(ITmfEvent event) {
        if (!(event instanceof CtfTmfEvent)) {
            return null;
        }
        ITmfEventField content = event.getContent();
        if (content != null) {
            Integer fieldValue = content.getFieldValue(Integer.class, RocmStrings.DEVICE_ID);
            if (fieldValue == null) {
                fieldValue = content.getFieldValue(Integer.class, RocmStrings.GPU_ID);
            }
            return fieldValue == null ? null : fieldValue.intValue();
        }
        return null;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        /*
         * Consider all sub-instance of this type "equal", so that they get
         * merged in a single CPU column/aspect.
         */
        return (other instanceof GpuAspect);
    }

    @Override
    public String getDeviceType() {
        return "gpu"; //$NON-NLS-1$
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
