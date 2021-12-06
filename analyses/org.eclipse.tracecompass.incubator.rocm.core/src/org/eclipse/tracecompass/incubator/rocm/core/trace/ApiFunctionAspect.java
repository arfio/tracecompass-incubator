package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;

public class ApiFunctionAspect implements ITmfEventAspect<String> {

    private final ApiFunctionMap fApiFunctionMap = new ApiFunctionMap();
    /** The singleton instance */
    public static final ApiFunctionAspect INSTANCE = new ApiFunctionAspect();

    private ApiFunctionAspect() {
    }

    public void addFunctionDeclaration(@NonNull ITmfEvent event) {
        fApiFunctionMap.processApiTable(event);
    }

    @Override
    public @NonNull String getName() {
        return Messages.getMessage(Messages.AspectName_ApiFunction);
    }

    @Override
    public @NonNull String getHelpText() {
        return Messages.getMessage(Messages.AspectHelpText_ApiFunction);
    }

    @Override
    public @Nullable String resolve(@NonNull ITmfEvent event) {
        return fApiFunctionMap.getEventName(event);
    }

}
