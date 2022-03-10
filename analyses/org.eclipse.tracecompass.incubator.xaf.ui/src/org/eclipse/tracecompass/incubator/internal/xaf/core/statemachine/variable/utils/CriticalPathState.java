/*******************************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.xaf.core.statemachine.variable.utils;

import java.util.Objects;

import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.graph.ITmfEdgeContextState;
import org.eclipse.tracecompass.analysis.os.linux.core.execution.graph.OsWorker;

/**
 * Element representing a Critical Path state
 *
 * @author Raphaël Beamonte
 */
public class CriticalPathState extends InterruptionReason {
    private TmfEdge.EdgeType type;
    private ITmfEdgeContextState contextState;
    private OsWorker worker;

    /**
     * @param type
     *            The edge type of the critical path state
     * @param worker
     *            The worker related to the critical path state
     */
    @Deprecated
    public CriticalPathState(TmfEdge.EdgeType type, OsWorker worker) {
        this.type = type;
        this.worker = worker;
    }

    public CriticalPathState(ITmfEdgeContextState contextState, OsWorker worker) {
        this.contextState = contextState;
        this.worker = worker;
    }

    /**
     * @return The edge type of the critical path state
     */
    @Deprecated
    public TmfEdge.EdgeType getType() {
        return type;
    }

    public ITmfEdgeContextState getContextState() {
        return contextState;
    }

    /**
     * @return The worker related to the critical path state
     */
    public OsWorker getWorker() {
        return worker;
    }

    @Override
    public String getID() {
        // String id = worker.getName() + " (tid " +
        // worker.getHostThread().getTid() + ") " + type.name();
        return String.format("%s %s", worker.getName(), contextState.getContextEnum().name()); //$NON-NLS-1$
    }

    @Override
    public String getTaskId() {
        return worker.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CriticalPathState)) {
            return false;
        }

        CriticalPathState b = (CriticalPathState) o;
        return getID().equals(b.getID());
    }

    @Override
    public int hashCode() {
        int hash = 379;

        String id = getID();
        hash = 131 * hash + (id != null ? id.hashCode() : 0);

        return hash;
    }

    @Override
    public String toString() {
        return getID();
    }

    @Override
    public int compareTo(InterruptionReason ir) {
        if (ir instanceof CriticalPathState) {
            CriticalPathState cps = (CriticalPathState) ir;
            if (contextState == null) {
                if (cps.contextState == null) {
                    return 0;
                }
                return -1;
            }
            int cmp = contextState.getContextEnum().name().compareTo(Objects.requireNonNull(cps.contextState.getContextEnum().name()));
            if (cmp == 0) {
                if (worker == null) {
                    if (cps.worker == null) {
                        return 0;
                    }
                    return -1;
                }
                // cmp =
                // worker.getHostThread().getTid().compareTo(cps.worker.getHostThread().getTid());
                // if (cmp == 0) {
                return worker.getName().compareTo(cps.worker.getName());
                // }
            }
            return cmp;
        }
        return 0;
    }
}