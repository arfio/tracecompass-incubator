package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

public class RocmEventLayout {

    /* Event name variables */
    private static final String HIP_PREFIX = "hip"; //$NON-NLS-1$
    private static final String HIP_BEGIN_SUFFIX = "Begin"; //$NON-NLS-1$
    private static final String HIP_END_SUFFIX = "End"; //$NON-NLS-1$
    private static final String HSA_PREFIX = "hsa"; //$NON-NLS-1$
    private static final String HSA_BEGIN_SUFFIX = "_begin"; //$NON-NLS-1$
    private static final String HSA_END_SUFFIX = "_end"; //$NON-NLS-1$
    private static final String HIP_OPERATION_BEGIN = "hip_op_begin"; //$NON-NLS-1$
    private static final String HIP_OPERATION_END = "hip_op_end"; //$NON-NLS-1$
    private static final String HSA_OPERATION_BEGIN = "hip_op_begin"; //$NON-NLS-1$
    private static final String HSA_OPERATION_END = "hip_op_end"; //$NON-NLS-1$

    private static final String HIP_MEMCPY_BEGIN = "hipMemcpyBegin";
    private static final String HIP_MEMCPY_END = "hipMemcpyEnd";


    /* Common event field names */
    private static final String THREAD_ID = "context._thread_id"; //$NON-NLS-1$
    private static final String QUEUE_ID = "context._correlation_id"; //$NON-NLS-1$
    private static final String AGENT_ID = "context._correlation_id"; //$NON-NLS-1$
    private static final String CORRELATION_ID = "context._correlation_id"; //$NON-NLS-1$

    /* HIP event field names */
    private static final String KERNEL_NAME = "context._kernel_name"; //$NON-NLS-1$

    /* HSA event field names */

    /* HIP operation event field names */
    private static final String OP_KERNEL_NAME = "kernel_name"; //$NON-NLS-1$

    /* HSA operation event field names */


    // ------------------------------------------------------------------------
    // Event names
    // ------------------------------------------------------------------------

    public String hipMemcpyBegin() {
        return HIP_MEMCPY_BEGIN;
    }


    public String hipMemcpyEnd() {
        return HIP_MEMCPY_END;
    }

    /**
     * This event is generated at the end of the function that changes the state
     * of a logical core
     *
     * @return The event name
     */
    public String getHipPrefix() {
        return HIP_PREFIX;
    }

    public String getHipBeginSuffix() {
        return HIP_BEGIN_SUFFIX;
    }

    public String getHipEndSuffix() {
        return HIP_END_SUFFIX;
    }

    public String getHsaPrefix() {
        return HSA_PREFIX;
    }

    public String getHsaBeginSuffix() {
        return HSA_BEGIN_SUFFIX;
    }

    public String getHsaEndSuffix() {
        return HSA_END_SUFFIX;
    }

    public String getHipOperationBegin() {
        return HIP_OPERATION_BEGIN;
    }

    public String getHipOperationEnd() {
        return HIP_OPERATION_END;
    }

    public String getHsaOperationBegin() {
        return HSA_OPERATION_BEGIN;
    }

    public String getHsaOperationEnd() {
        return HSA_OPERATION_END;
    }

    // ------------------------------------------------------------------------
    // Event field names
    // ------------------------------------------------------------------------
    public String fieldThreadId() {
        return THREAD_ID;
    }

    public String fieldQueueId() {
        return QUEUE_ID;
    }

    public String fieldAgentId() {
        return AGENT_ID;
    }

    public String fieldCorrelationId() {
        return CORRELATION_ID;
    }

    public String fieldKernelName() {
        return KERNEL_NAME;
    }

    public String fieldOperationName() {
        return OP_KERNEL_NAME;
    }
}
