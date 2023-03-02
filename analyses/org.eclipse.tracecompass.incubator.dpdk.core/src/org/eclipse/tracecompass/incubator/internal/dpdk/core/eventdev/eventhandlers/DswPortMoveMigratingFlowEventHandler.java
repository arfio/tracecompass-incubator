package org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.eventhandlers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.analysis.DpdkEventDevStateProvider;
import org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.analysis.EventDevModel;
import org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.analysis.PortModel;
import org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.analysis.PortStatus;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

/**
 * @author Adel Belkhiri
 *
 */
public class DswPortMoveMigratingFlowEventHandler extends DpdkEventHandler {

    /**
     * @param layout
     *      DpdkPipelineAnalysisEventLayout instance
     * @param stateProvider
     *      Pipelinesstate provider
     */
    public DswPortMoveMigratingFlowEventHandler(@NonNull DpdkEventDevAnalysisEventLayout layout, DpdkEventDevStateProvider stateProvider) {
        super(layout, stateProvider);
    }

    @Override
    public void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event) throws AttributeNotFoundException {
        DpdkEventDevAnalysisEventLayout layout = getLayout();

        /* unpack the event */
        ITmfEventField content = event.getContent();
        long ts = event.getTimestamp().getValue();

        Integer dsw = content.getFieldValue(Integer.class, layout.fieldDsw());
        Integer srcPortId = content.getFieldValue(Integer.class, layout.fieldSrcPortId());
        Integer queueId = content.getFieldValue(Integer.class, layout.fieldQueueId());
        Integer hashFlow = content.getFieldValue(Integer.class, layout.fieldFlowHash());

        if (dsw == null || srcPortId == null || queueId == null || hashFlow == null) {
            throw new IllegalArgumentException(layout.eventDswPortEndMigration() + " event does not have expected fields"); //$NON-NLS-1$ ;
        }

        EventDevModel device = fEventdevStateProvier.searchEventDevByBackendId(dsw);
        if(device != null) {
            PortModel port = device.getPort(srcPortId);
            if(port != null) {
                port.updateStatus(PortStatus.UNPAUSING, ts);
            }
        }
    }

}
