package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class ApiFunctionMap {

    private final Table<String, Integer, String> fApiTable = HashBasedTable.create();

    public void processApiTable(@NonNull ITmfEvent event) {
        ITmfEventField content = event.getContent();
        if (content == null) {
            return;
        }
        Integer cid = content.getFieldValue(Integer.class, RocmStrings.CORRELATION_ID);
        String api = event.getName().substring(0, 3);
        String name = content.getFieldValue(String.class, RocmStrings.NAME);
        if (name != null && cid != null) {
            fApiTable.put(api, cid, name);
        }
    }

    public @NonNull String getEventName(@NonNull ITmfEvent event) {
        Integer cid = event.getContent().getFieldValue(Integer.class, RocmStrings.CID);
        String api = event.getName().substring(0, 3);
        String eventName = fApiTable.get(api, cid);
        if (eventName == null) {
            return "";
        }
        return eventName;
    }
}
