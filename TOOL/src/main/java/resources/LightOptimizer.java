package resources;

import bpmn.graph.Graph;
import bpmn.types.process.BpmnProcessObject;

import java.util.ArrayList;
import java.util.HashMap;

public class LightOptimizer extends Optimizer
{
	public LightOptimizer(final ArrayList<BpmnProcessObject> objects,
						  final Graph graph)
	{
		super(objects, graph, new GlobalResourceSet(objects), 1);
	}

	@Override
	@Deprecated
	public HashMap<Resource, Double> computeGlobalResourceUsageOverTime(final HashMap<Integer, ResourcePool> resourcesPerInstantOfTime,
																		final ResourcePool optimalPool)
	{
		throw new IllegalStateException("Should not be used in light optimizer.");
	}

	@Override
	@Deprecated
	public ResourcePool computeMaximalPoolForOneProcess()
	{
		throw new IllegalStateException("Should not be used in light optimizer.");
	}

	@Override
	@Deprecated
	public ResourcePool computeOptimalPoolForOneProcess()
	{
		throw new IllegalStateException("Should not be used in light optimizer.");
	}

	@Override
	@Deprecated
	public ResourcePool computeMaximalPoolForNProcesses()
	{
		throw new IllegalStateException("Should not be used in light optimizer.");
	}

	@Override
	@Deprecated
	public ResourcePool computeOptimalPoolForNProcesses()
	{
		throw new IllegalStateException("Should not be used in light optimizer.");
	}
}
