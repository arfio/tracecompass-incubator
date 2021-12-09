package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;

/**
 * @author Arnaud Fiorini
 *
 * This class is used to identify the correct lane when defining dependencies.
 */
public class HostThreadIdentifier {
    private static final Map<String, Integer> fApiMap;
    static {
        fApiMap = new HashMap<>();
        fApiMap.put(RocmStrings.HIP_API, 1);
        fApiMap.put(RocmStrings.HSA_API, 2);
    }
    public enum KERNEL_CATEGORY {
        QUEUE, STREAM
    }
    public enum ROCM_CATEGORY {
        SYSTEM, MEMORY
    }

    private final int fApiId; // Api type, Queue id, Stream id
    private final int fThreadId; // Tid, Queue type, Stream type
    private final int fCategoryId; // System, Memory, GPU id


    private HostThreadIdentifier(int apiId, int threadId, int categoryId) {
        fApiId = apiId;
        fThreadId = threadId;
        fCategoryId = categoryId;
    }

    /**
     * Constructor for Memory transfer events, as there is only
     * one call stack for this, there is no parameters
     *
     */
    public HostThreadIdentifier() {
        this(0, 0, ROCM_CATEGORY.MEMORY.ordinal());
    }

    /**
     * Constructor for GPU events
     *
     * @param categoryId stream id or queue id
     * @param category Either queues or streams
     * @param gpuId
     */
    public HostThreadIdentifier(int categoryId, KERNEL_CATEGORY category, int gpuId) {
        // There are other categories (system, memory), this will separate the GPU categories.
        this(categoryId, category.ordinal(), gpuId + ROCM_CATEGORY.values().length);
    }

    /**
     * Constructor for API events
     *
     * @param apiType
     * @param tid
     */
    public HostThreadIdentifier(String apiType, int tid) {
        this(fApiMap.getOrDefault(apiType, -1), tid, ROCM_CATEGORY.SYSTEM.ordinal());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fApiId, fThreadId, fCategoryId);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (this.getClass() != other.getClass()) {
            return false;
        }
        HostThreadIdentifier that = (HostThreadIdentifier) other;
        return (fApiId == that.fApiId) && (fThreadId == that.fThreadId) && (fCategoryId == that.fCategoryId);
    }
}
