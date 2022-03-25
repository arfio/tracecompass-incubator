package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement.PriorityLevel;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAnalysisEventRequirement;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import com.google.common.collect.ImmutableSet;

public class RocmMetadataAnalysis extends TmfStateSystemAnalysisModule {

    public static final String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.functionname"; //$NON-NLS-1$

    private Set<@NonNull TmfAbstractAnalysisRequirement> fAnalysisRequirements;

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        return new RocmMetadataStateProvider(Objects.requireNonNull(getTrace()));
    }

    @Override
    public Iterable<@NonNull TmfAbstractAnalysisRequirement> getAnalysisRequirements() {
        Set<@NonNull TmfAbstractAnalysisRequirement> requirements = fAnalysisRequirements;
        if (requirements == null) {
            /* Initialize the requirements for the analysis */
            requirements = ImmutableSet.of(new TmfAnalysisEventRequirement(
                    ImmutableSet.of(RocmStrings.HIP_FUNCTION_NAME, RocmStrings.HSA_FUNCTION_NAME),
                    PriorityLevel.AT_LEAST_ONE));
            fAnalysisRequirements = requirements;
        }
        return requirements;
    }

}
