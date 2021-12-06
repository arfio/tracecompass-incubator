package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.dependency;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.incubator.callstack.core.base.EdgeStateValue;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;

public abstract class DependencyMaker implements IDependencyMaker {

    public static void addArrow(ITmfStateSystemBuilder ssb, Long startTime, Long endTime, int id,
            @NonNull HostThread src, @NonNull HostThread dest) {
        int edgeQuark = getAvailableEdgeQuark(ssb, startTime);

        Object edgeStateValue = new EdgeStateValue(id, src, dest);
        ssb.modifyAttribute(startTime, edgeStateValue, edgeQuark);
        ssb.modifyAttribute(endTime, (Object) null, edgeQuark);
    }

    private static int getAvailableEdgeQuark(ITmfStateSystemBuilder ssb, long startTime) {
        int edgeRoot = ssb.getQuarkAbsoluteAndAdd(RocmStrings.EDGES);
        List<@NonNull Integer> subQuarks = ssb.getSubAttributes(edgeRoot, false);

        for (int quark : subQuarks) {
            long start = ssb.getOngoingStartTime(quark);
            Object value = ssb.queryOngoing(quark);
            if (value == null && start <= startTime) {
                return quark;
            }
        }
        return ssb.getQuarkRelativeAndAdd(edgeRoot, Integer.toString(subQuarks.size()));
    }

}
