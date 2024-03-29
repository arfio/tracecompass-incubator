/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.services;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.trace.server.jersey.rest.core.model.views.QueryParameters;
import org.eclipse.tracecompass.incubator.internal.trace.server.jersey.rest.core.services.ExperimentManagerService;
import org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs.ExperimentModelStub;
import org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs.TraceModelStub;
import org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.utils.RestServerTest;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

/**
 * Test the {@link ExperimentManagerService}
 *
 * @author Loic Prieur-Drevon
 */
public class ExperimentManagerServiceTest extends RestServerTest {

    private static final String TEST = "test";
    private static final @NonNull String EXPERIMENT_UUID = "bb12687f-9866-3a9f-bf55-b4d9da0137ed";
    private static final @NonNull String EXPERIMENT2_UUID = "b3050981-d846-3ff4-95b0-af8342c519c5";
    private static final @NonNull String EXPERIMENT3_UUID = "5466b39b-6f53-3aa7-98da-0b9992e6bae6";
    private static final @NonNull ImmutableSet<TraceModelStub> CONTEXT_SWITCH_SET = ImmutableSet.of(CONTEXT_SWITCHES_KERNEL_STUB, CONTEXT_SWITCHES_UST_STUB);
    private static final ExperimentModelStub EXPECTED = new ExperimentModelStub(TEST,
            UUID.fromString(EXPERIMENT_UUID), 0L, 0L, 0L, "RUNNING", CONTEXT_SWITCH_SET);
    private static final ExperimentModelStub EXPECTED2 = new ExperimentModelStub(TEST,
            UUID.fromString(EXPERIMENT2_UUID), 0L, 0L, 0L, "RUNNING", ImmutableSet.of(CONTEXT_SWITCHES_KERNEL_STUB));
    private static final ExperimentModelStub EXPECTED3 = new ExperimentModelStub(TEST,
            UUID.fromString(EXPERIMENT3_UUID), 0L, 0L, 0L, "RUNNING", ImmutableSet.of(ARM_64_KERNEL_STUB, CONTEXT_SWITCHES_UST_STUB));

    /**
     * Basic test for the {@link ExperimentManagerService}
     */
    @Test
    public void testExperiment() {
        WebTarget application = getApplicationEndpoint();
        WebTarget traces = application.path(TRACES);
        WebTarget expTarget = application.path(EXPERIMENTS);

        assertPost(traces, CONTEXT_SWITCHES_UST_STUB);
        assertPost(traces, CONTEXT_SWITCHES_KERNEL_STUB);
        assertEquals(CONTEXT_SWITCH_SET, getTraces(traces));

        assertEquals("experiment set should be empty at this point", Collections.emptySet(), getExperiments(expTarget));

        List<String> traceUUIDs = new ArrayList<>();
        traceUUIDs.add(CONTEXT_SWITCHES_KERNEL_UUID.toString());
        traceUUIDs.add(CONTEXT_SWITCHES_UST_UUID.toString());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put(NAME, EXPECTED.getName());
        parameters.put(TRACES, traceUUIDs);

        Response response = expTarget.request().post(Entity.json(new QueryParameters(parameters, Collections.emptyList())));
        assertEquals("Failed to POST the experiment", EXPECTED, response.readEntity(ExperimentModelStub.class));
        assertEquals("Failed to add experiment to set of experiments", Collections.singleton(EXPECTED), getExperiments(expTarget));
        assertEquals("Adding an experiment should not change the trace set", CONTEXT_SWITCH_SET, getTraces(traces));
        assertEquals("Failed to get the experiment by its UUID", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().get(ExperimentModelStub.class));

        assertEquals("Failed to DELETE the experiment", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().delete().readEntity(ExperimentModelStub.class));
        assertEquals("experiment set should be empty at this point", Collections.emptySet(), getExperiments(expTarget));
        assertEquals("Deleting an experiment should not change the trace set", CONTEXT_SWITCH_SET, getTraces(traces));
        response.close();
    }

    /**
     * Test posting an experiment twice, it should be OK
     */
    @Test
    public void testRePost() {
        WebTarget application = getApplicationEndpoint();
        WebTarget traces = application.path(TRACES);
        WebTarget expTarget = application.path(EXPERIMENTS);

        assertPost(traces, CONTEXT_SWITCHES_UST_STUB);
        assertPost(traces, CONTEXT_SWITCHES_KERNEL_STUB);
        assertEquals(CONTEXT_SWITCH_SET, getTraces(traces));

        assertEquals("experiment set should be empty at this point", Collections.emptySet(), getExperiments(expTarget));

        List<String> traceUUIDs = new ArrayList<>();
        traceUUIDs.add(CONTEXT_SWITCHES_KERNEL_UUID.toString());
        traceUUIDs.add(CONTEXT_SWITCHES_UST_UUID.toString());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put(NAME, EXPECTED.getName());
        parameters.put(TRACES, traceUUIDs);

        Response response = expTarget.request().post(Entity.json(new QueryParameters(parameters, Collections.emptyList())));
        assertEquals("Failed to POST the experiment", EXPECTED, response.readEntity(ExperimentModelStub.class));
        assertEquals("Failed to add experiment to set of experiments", Collections.singleton(EXPECTED), getExperiments(expTarget));
        assertEquals("Adding an experiment should not change the trace set", CONTEXT_SWITCH_SET, getTraces(traces));
        assertEquals("Failed to get the experiment by its UUID", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().get(ExperimentModelStub.class));
        response.close();

        // Make a second post with the same name and traces, should return the experiment
        Response response2 = expTarget.request().post(Entity.json(new QueryParameters(parameters, Collections.emptyList())));
        assertEquals("Status of second post", Status.OK.getStatusCode(), response2.getStatus());
        assertEquals("Failed to POST the experiment a second time", EXPECTED, response2.readEntity(ExperimentModelStub.class));
        assertEquals("There should still be only one experiment", Collections.singleton(EXPECTED), getExperiments(expTarget));
        assertEquals("Adding an experiment should not change the trace set", CONTEXT_SWITCH_SET, getTraces(traces));
        assertEquals("Failed to get the experiment by its UUID", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().get(ExperimentModelStub.class));
        response2.close();

        assertEquals("Failed to DELETE the experiment", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().delete().readEntity(ExperimentModelStub.class));
        assertEquals("experiment set should be empty at this point", Collections.emptySet(), getExperiments(expTarget));
        assertEquals("Deleting an experiment should not change the trace set", CONTEXT_SWITCH_SET, getTraces(traces));

    }

    /**
     * Test posting an experiment with different conflicting situations
     */
    @Test
    public void testPostConflicts() {
        WebTarget application = getApplicationEndpoint();
        WebTarget traces = application.path(TRACES);
        WebTarget expTarget = application.path(EXPERIMENTS);

        assertPost(traces, CONTEXT_SWITCHES_UST_STUB);
        assertPost(traces, CONTEXT_SWITCHES_KERNEL_STUB);
        assertPost(traces, ARM_64_KERNEL_STUB);
        ImmutableSet<TraceModelStub> traceSet = ImmutableSet.of(CONTEXT_SWITCHES_UST_STUB, CONTEXT_SWITCHES_KERNEL_STUB, ARM_64_KERNEL_STUB);
        assertEquals(traceSet, getTraces(traces));

        assertEquals("experiment set should be empty at this point", Collections.emptySet(), getExperiments(expTarget));

        List<String> traceUUIDs = new ArrayList<>();
        traceUUIDs.add(CONTEXT_SWITCHES_KERNEL_UUID.toString());
        traceUUIDs.add(CONTEXT_SWITCHES_UST_UUID.toString());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put(NAME, EXPECTED.getName());
        parameters.put(TRACES, traceUUIDs);

        Response response = expTarget.request().post(Entity.json(new QueryParameters(parameters, Collections.emptyList())));
        assertEquals("Failed to POST the experiment", EXPECTED, response.readEntity(ExperimentModelStub.class));
        assertEquals("Failed to add experiment to set of experiments", Collections.singleton(EXPECTED), getExperiments(expTarget));
        assertEquals("Adding an experiment should not change the trace set", traceSet, getTraces(traces));
        assertEquals("Failed to get the experiment by its UUID", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().get(ExperimentModelStub.class));
        response.close();

        // Post same name experiment, but different trace size, should return an OK status with a new experiment
        List<String> traceUUIDs2 = new ArrayList<>();
        traceUUIDs2.add(CONTEXT_SWITCHES_KERNEL_UUID.toString());
        Map<String, Object> parameters2 = new HashMap<>();
        parameters2.put(NAME, EXPECTED.getName());
        parameters2.put(TRACES, traceUUIDs2);
        Response response2 = expTarget.request().post(Entity.json(new QueryParameters(parameters2, Collections.emptyList())));
        assertEquals("Expected a success for posting different experiment", Status.OK.getStatusCode(), response2.getStatus());
        assertEquals("Conflict should return different experiment", EXPECTED2, response2.readEntity(ExperimentModelStub.class));
        assertEquals("There should now be two experiments", ImmutableSet.of(EXPECTED, EXPECTED2), getExperiments(expTarget));
        assertEquals("Adding a new experiment should not change the trace set", traceSet, getTraces(traces));
        assertEquals("Failed to get the new experiment by its UUID", EXPECTED2, expTarget.path(EXPERIMENT2_UUID).request().get(ExperimentModelStub.class));
        response2.close();

        // Post same experiment name, but with traces with the same names, but not the same traces
        List<String> traceUUIDs3 = new ArrayList<>();
        traceUUIDs3.add(ARM_64_KERNEL_UUID.toString());
        traceUUIDs3.add(CONTEXT_SWITCHES_UST_UUID.toString());
        Map<String, Object> parameters3 = new HashMap<>();
        parameters3.put(NAME, EXPECTED.getName());
        parameters3.put(TRACES, traceUUIDs3);
        Response response3 = expTarget.request().post(Entity.json(new QueryParameters(parameters3, Collections.emptyList())));
        assertEquals("Expected a success for posting different experiment", Status.OK.getStatusCode(), response3.getStatus());
        assertEquals("Conflict should return different experiment", EXPECTED3, response3.readEntity(ExperimentModelStub.class));
        assertEquals("There should now be two experiments", ImmutableSet.of(EXPECTED, EXPECTED2, EXPECTED3), getExperiments(expTarget));
        assertEquals("Adding a new experiment should not change the trace set", traceSet, getTraces(traces));
        assertEquals("Failed to get the new experiment by its UUID", EXPECTED3, expTarget.path(EXPERIMENT3_UUID).request().get(ExperimentModelStub.class));
        response3.close();

        assertEquals("Failed to DELETE the experiment", EXPECTED, expTarget.path(EXPERIMENT_UUID).request().delete().readEntity(ExperimentModelStub.class));
        assertEquals("Failed to DELETE the experiment", EXPECTED2, expTarget.path(EXPERIMENT2_UUID).request().delete().readEntity(ExperimentModelStub.class));
        assertEquals("Failed to DELETE the experiment", EXPECTED3, expTarget.path(EXPERIMENT3_UUID).request().delete().readEntity(ExperimentModelStub.class));
        assertEquals("experiment set should be empty at this point", Collections.emptySet(), getExperiments(expTarget));
        assertEquals("Deleting an experiment should not change the trace set", traceSet, getTraces(traces));
    }

}
