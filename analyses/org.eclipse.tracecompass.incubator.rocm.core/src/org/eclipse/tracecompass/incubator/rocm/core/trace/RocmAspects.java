package org.eclipse.tracecompass.incubator.rocm.core.trace;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;

import com.google.common.collect.ImmutableList;

@org.eclipse.jdt.annotation.NonNullByDefault
public class RocmAspects {
    private static final ITmfEventAspect<Integer> PID_ASPECT = new ITmfEventAspect<Integer>() {
        @Override
        public String getName() {
            return Messages.getMessage(Messages.AspectName_PID);
        }

        @Override
        public String getHelpText() {
            return ITmfEventAspect.EMPTY_STRING;
        }

        @Override
        public @Nullable Integer resolve(ITmfEvent event) {
            return event.getContent().getFieldValue(Integer.class, RocmStrings.PID);
        }
    };

    private static final ITmfEventAspect<Integer> TID_ASPECT = new ITmfEventAspect<Integer>() {
        @Override
        public String getName() {
            return Messages.getMessage(Messages.AspectName_TID);
        }

        @Override
        public String getHelpText() {
            return ITmfEventAspect.EMPTY_STRING;
        }

        @Override
        public @Nullable Integer resolve(ITmfEvent event) {
            return event.getContent().getFieldValue(Integer.class, RocmStrings.TID);
        }
    };

    private static final ITmfEventAspect<Integer> QUEUEID_ASPECT = new ITmfEventAspect<Integer>() {
        @Override
        public String getName() {
            return Messages.getMessage(Messages.AspectName_QueueID);
        }

        @Override
        public String getHelpText() {
            return ITmfEventAspect.EMPTY_STRING;
        }

        @Override
        public @Nullable Integer resolve(ITmfEvent event) {
            return event.getContent().getFieldValue(Integer.class, RocmStrings.QUEUE_ID);
        }
    };

    private static final ITmfEventAspect<Integer> STREAMID_ASPECT = new ITmfEventAspect<Integer>() {
        @Override
        public String getName() {
            return Messages.getMessage(Messages.AspectName_StreamID);
        }

        @Override
        public String getHelpText() {
            return ITmfEventAspect.EMPTY_STRING;
        }

        @Override
        public @Nullable Integer resolve(ITmfEvent event) {
            return event.getContent().getFieldValue(Integer.class, RocmStrings.STREAM_ID);
        }
    };

    private static final ITmfEventAspect<Integer> QUEUEINDEX_ASPECT = new ITmfEventAspect<Integer>() {
        @Override
        public String getName() {
            return Messages.getMessage(Messages.AspectName_QueueIndex);
        }

        @Override
        public String getHelpText() {
            return ITmfEventAspect.EMPTY_STRING;
        }

        @Override
        public @Nullable Integer resolve(ITmfEvent event) {
            return event.getContent().getFieldValue(Integer.class, RocmStrings.QUEUE_INDEX);
        }
    };

    private static final List<ITmfEventAspect<?>> ASPECTS = ImmutableList.of(
            getPIDAspect(),
            getTIDAspect(),
            getQueueIDAspect(),
            getStreamIDAspect(),
            getQueueIndexAspect());

    private RocmAspects() {

    }

    /**
     * Get the aspect for the event pid
     *
     * @return The process ID
     */
    public static ITmfEventAspect<Integer> getPIDAspect() {
        return PID_ASPECT;
    }

    /**
     * Get the aspect for the event tid
     *
     * @return The thread ID
     */
    public static ITmfEventAspect<Integer> getTIDAspect() {
        return TID_ASPECT;
    }

    /**
     * Get the aspect for the event HSA queue ID
     *
     * @return The queue ID
     */
    public static ITmfEventAspect<Integer> getQueueIDAspect() {
        return QUEUEID_ASPECT;
    }

    /**
     * Get the aspect for the event HIP stream ID
     *
     * @return The stream ID
     */
    public static ITmfEventAspect<Integer> getStreamIDAspect() {
        return STREAMID_ASPECT;
    }

    /**
     * Get the aspect for the event queue index
     *
     * @return The event index in its HSA queue
     */
    public static ITmfEventAspect<Integer> getQueueIndexAspect() {
        return QUEUEINDEX_ASPECT;
    }

    /**
     * Get the list of all Rocm aspects
     *
     * @return the list of aspects
     */
    public static List<ITmfEventAspect<?>> getAllAspects() {
        return ASPECTS;
    }
}
