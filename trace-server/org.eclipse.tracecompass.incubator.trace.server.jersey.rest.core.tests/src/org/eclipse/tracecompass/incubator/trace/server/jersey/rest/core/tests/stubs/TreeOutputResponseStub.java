/**********************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A stub class for the response to a tree request for xy charts and data trees.
 * It contains the generic response, as well as an {@link EntryModelStub}
 *
 * @author Geneviève Bastien
 */
public class TreeOutputResponseStub extends OutputResponseStub {

    private static final long serialVersionUID = -2273261726401144959L;

    private final EntryModelStub fModel;

    /**
     * {@link JsonCreator} Constructor from json
     *
     * @param model
     *            The model for this response
     * @param status
     *            The status of the response
     * @param statusMessage
     *            The custom status message of the response
     */
    public TreeOutputResponseStub(@JsonProperty("model") EntryModelStub model,
            @JsonProperty("status") String status,
            @JsonProperty("statusMessage") String statusMessage) {
        super(status, statusMessage);
        fModel = model;
    }

    /**
     * Get the model for this response
     *
     * @return The model for the response
     */
    public EntryModelStub getModel() {
        return fModel;
    }

}
