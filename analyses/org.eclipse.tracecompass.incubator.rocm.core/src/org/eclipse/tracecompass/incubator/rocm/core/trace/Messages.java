package org.eclipse.tracecompass.incubator.rocm.core.trace;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.tracecompass.incubator.rocm.core.trace.messages"; //$NON-NLS-1$

    public static @Nullable String AspectName_GPU;

    public static @Nullable String AspectHelpText_GPU;

    /**
     * Nanosecond normalized timestamp
     * @since 6.2
     */
    public static @Nullable String AspectName_Timestamp_Nanoseconds;
    /**
     * Explanation of why use a nanosecond normalized timestamp
     * @since 6.2
     */
    public static @Nullable String AspectName_Timestamp_Nanoseconds_Help;

    static {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }

    /**
     * Helper method to expose externalized strings as non-null objects.
     */
    static String getMessage(@Nullable String msg) {
        if (msg == null) {
            return ""; //$NON-NLS-1$
        }
        return msg;
    }
}
