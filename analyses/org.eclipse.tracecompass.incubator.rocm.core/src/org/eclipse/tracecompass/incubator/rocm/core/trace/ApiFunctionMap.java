package org.eclipse.tracecompass.incubator.rocm.core.trace;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class ApiFunctionMap {

    private class FunctionID {
        private String fApiName;
        private Integer fCid;
        private Integer fHashCode;

        FunctionID(String apiName, int cid) {
            fApiName = apiName;
            fCid = cid;
        }

        FunctionID(int hashCode) {
            fHashCode = hashCode;
        }

        @Override
        public int hashCode() {
            if (fHashCode != null) {
                return fHashCode;
            }
            return Objects.hash(fApiName, fCid);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof FunctionID)) {
                return false;
            }
            return this.hashCode() == o.hashCode();
        }
    }

    private final Map<FunctionID, String> fApiMap = new HashMap<>();
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
            fApiMap.put(new FunctionID(api, cid), name);
        }
    }

    public @NonNull String getEventName(@NonNull ITmfEvent event) {
        Integer cid = event.getContent().getFieldValue(Integer.class, RocmStrings.CID);
        String api = event.getName().substring(0, 3);
        if (cid == null) {
            return StringUtils.EMPTY;
        }
        return getEventNameFromCID(api, cid);
    }

    public @NonNull String getEventNameFromCID(@NonNull String api, @NonNull Integer cid) {
        String eventName = fApiTable.get(api, cid);
        eventName = fApiMap.get(new FunctionID(api, cid));
        if (eventName == null) {
            return StringUtils.EMPTY;
        }
        return eventName;
    }

    public @NonNull String getEventNameFromFunctionID(Integer functionID) {
        String eventName = fApiMap.get(new FunctionID(functionID));
        if (eventName == null) {
            return StringUtils.EMPTY;
        }
        return eventName;
    }

    public @NonNull Integer getFunctionIDFromApiAndCid(String apiName, Integer cid) {
        return new FunctionID(apiName, cid).hashCode();
    }
}
