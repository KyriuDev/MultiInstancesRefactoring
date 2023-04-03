package refactoring.dependencies;

import bpmn.graph.Node;
import refactoring.Cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class DependenciesAnalyzer
{
	/**
	 * In this class, we verify whether the conditions specified in SCC21
	 * (<a href="https://hal.inria.fr/hal-03330330/en">...</a>) are verified
	 * for the given dependencies.
	 * The two conditions are:
	 * 1) if there exists A, B, C s.t. (A,C) & (B,C), then succ(A) = succ(B)
	 * 2) if there exists A, B, C s.t. (A,B) & (A,C), then pred(B) = pred(C)
	 * If such conditions are respected, then there exists only 1 optimal representation
	 * of the given process that respects the given dependencies.
	 */

	private final HashSet<Cluster> problematicClusters;

	public DependenciesAnalyzer()
	{
		this.problematicClusters = new HashSet<>();
	}

	public boolean dependenciesValidated()
	{
		return this.problematicClusters.isEmpty();
	}

	public HashSet<Cluster> problematicClusters()
	{
		return this.problematicClusters;
	}

	public void verifyDependencies(final Cluster cluster)
	{
		for (HashSet<Dependency> dependencySet : cluster.graphsWithDependencies().values())
		{
			if (!this.verifyDependencySet(dependencySet))
			{
				this.problematicClusters.add(cluster);
			}
		}

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.verifyDependencies(choiceCluster);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				this.verifyDependencies(enhancedLoop.entryToExitCluster());
				this.verifyDependencies(enhancedLoop.exitToEntryCluster());
			}
		}
	}

	public boolean verifyDependencySet(final HashSet<Dependency> dependencySet)
	{
		final Map<Node, ArrayList<Dependency>> conditionOneDependencies = new HashMap<>();
		final Map<Node, ArrayList<Dependency>> conditionTwoDependencies = new HashMap<>();

		for (Dependency dependency : dependencySet)
		{
			//Group dependencies that have the same second node
			final ArrayList<Dependency> conditionOneList = conditionOneDependencies.computeIfAbsent(dependency.secondNode(), a -> new ArrayList<>());
			conditionOneList.add(dependency);

			//Group dependencies that have the same first node
			final ArrayList<Dependency> conditionTwoList = conditionTwoDependencies.computeIfAbsent(dependency.firstNode(), a -> new ArrayList<>());
			conditionTwoList.add(dependency);
		}

		//Check whether condition one is respected
		for (ArrayList<Dependency> conditionOneDependency : conditionOneDependencies.values())
		{
			if (conditionOneDependency.size() > 1)
			{
				HashSet<Node> previousSuccessors = new HashSet<>();

				for (Dependency dependency : conditionOneDependency)
				{
					if (previousSuccessors.isEmpty())
					{
						previousSuccessors = this.findSuccessorsOf(dependency.firstNode(), dependencySet);
					}
					else
					{
						final HashSet<Node> newSuccessors = this.findSuccessorsOf(dependency.firstNode(), dependencySet);

						if (!previousSuccessors.equals(newSuccessors))
						{
							//Some successors are not common
							return false;
						}
					}
				}
			}
		}

		//Check whether condition two is respected
		for (ArrayList<Dependency> conditionTwoDependency : conditionTwoDependencies.values())
		{
			if (conditionTwoDependency.size() > 1)
			{
				HashSet<Node> previousPredecessors = new HashSet<>();

				for (Dependency dependency : conditionTwoDependency)
				{
					if (previousPredecessors.isEmpty())
					{
						previousPredecessors = this.findPredecessorsOf(dependency.secondNode(), dependencySet);
					}
					else
					{
						final HashSet<Node> newPredecessors = this.findPredecessorsOf(dependency.secondNode(), dependencySet);

						if (!previousPredecessors.equals(newPredecessors))
						{
							//Some predecessors are not common
							return false;
						}
					}
				}
			}
		}

		return true;
	}

	//Private methods

	private HashSet<Node> findSuccessorsOf(final Node node,
												  final HashSet<Dependency> dependencies)
	{
		final HashSet<Node> successors = new HashSet<>();

		for (Dependency dependency : dependencies)
		{
			if (dependency.firstNode().equals(node))
			{
				successors.add(dependency.secondNode());
			}
		}

		return successors;
	}

	private HashSet<Node> findPredecessorsOf(final Node node,
													final HashSet<Dependency> dependencies)
	{
		final HashSet<Node> predecessors = new HashSet<>();

		for (Dependency dependency : dependencies)
		{
			if (dependency.secondNode().equals(node))
			{
				predecessors.add(dependency.firstNode());
			}
		}

		return predecessors;
	}
}
