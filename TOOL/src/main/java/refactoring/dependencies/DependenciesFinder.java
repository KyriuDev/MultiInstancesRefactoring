package refactoring.dependencies;

import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.Node;
import loops_management.LoopFinder;
import refactoring.Cluster;
import refactoring.exceptions.BadDependencyException;
import refactoring.partial_order_to_bpmn.AbstractGraph;
import refactoring.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.partial_order_to_bpmn.BPMNGenerator;
import resources.LightOptimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class DependenciesFinder
{
	private final HashSet<Cluster> problematicClusters;
	private final Graph graph;
	private final LoopFinder loopFinder;

	public DependenciesFinder(final HashSet<Cluster> problematicClusters,
							  final Graph graph,
							  final LoopFinder loopFinder)
	{
		this.problematicClusters = problematicClusters;
		this.graph = graph;
		this.loopFinder = loopFinder;
	}

	public void findOptimalDependencies()
	{
		ArrayList<Cluster> currentClusters = this.findUsableClusters();

		while (!currentClusters.isEmpty())
		{
			for (Cluster cluster : currentClusters)
			{
				this.optimizeCluster(cluster);
			}

			currentClusters = findUsableClusters();
		}
	}

	//Private methods

	private void optimizeCluster(final Cluster cluster)
	{
		final HashMap<DependencyGraph, HashSet<Dependency>> originalDependencies = new HashMap<>(cluster.graphsWithDependencies());

		for (DependencyGraph dependencyGraph : originalDependencies.keySet())
		{
			final HashSet<Dependency> dependencySet = originalDependencies.get(dependencyGraph);

			if (!new DependenciesAnalyzer().verifyDependencySet(dependencySet))
			{
				//The current set of dependencies does not respect SCC constraints
				cluster.graphsWithDependencies().remove(dependencyGraph);
				final ArrayList<DependencyGraph> validGraphs = new ArrayList<>();
				this.computeOptimalDependencies(dependencyGraph, validGraphs, new ArrayList<>());

				if (validGraphs.isEmpty())
				{
					throw new IllegalStateException("No valid dependency graph was found for the dependency graph:\n\n" + dependencyGraph);
				}

				final DependencyGraph optimalGraph = this.findOptimalGraph(validGraphs);
				cluster.graphsWithDependencies().put(optimalGraph, optimalGraph.toDependencySet());
			}
		}
	}

	private ArrayList<Cluster> findUsableClusters()
	{
		final ArrayList<Cluster> usableClusters = new ArrayList<>();

		for (Iterator<Cluster> iterator = this.problematicClusters.iterator(); iterator.hasNext(); )
		{
			final Cluster currentCluster = iterator.next();

			if (this.isUsableCluster(currentCluster))
			{
				usableClusters.add(currentCluster);
				iterator.remove();
			}
		}

		return usableClusters;
	}

	private boolean isUsableCluster(final Cluster cluster)
	{
		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster subCluster : enhancedChoice.clusters())
				{
					if (this.problematicClusters.contains(subCluster)
						|| !this.isUsableCluster(subCluster))
					{
						return false;
					}
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				if (this.problematicClusters.contains(enhancedLoop.entryToExitCluster())
					|| this.problematicClusters.contains(enhancedLoop.exitToEntryCluster())
					|| !this.isUsableCluster(enhancedLoop.entryToExitCluster())
					|| !this.isUsableCluster(enhancedLoop.exitToEntryCluster()))
				{
					return false;
				}
			}
		}

		return true;
	}

	private void computeOptimalDependencies(final DependencyGraph dependencyGraph,
											final ArrayList<DependencyGraph> validGraphs,
											final ArrayList<DependencyGraph> alreadyComputedGraphs)
	{
		final HashSet<Node> originalNodes = new HashSet<>(dependencyGraph.toSet());

		for (Node node : originalNodes)
		{
			final HashSet<Node> nodesToLink = new HashSet<>();

			//Compute all linkable nodes from current node
			for (Node nextNode : originalNodes)
			{
				if (!node.equals(nextNode))
				{
					if (!nextNode.isSuccessorOf(node)
						&& !nextNode.isAncestorOf(node))
					{
						nodesToLink.add(nextNode);
					}
				}
			}

			//Create 1 new graph per link added
			if (!nodesToLink.isEmpty())
			{
				for (Node nodeToLink : nodesToLink)
				{
					final DependencyGraph copiedGraph = dependencyGraph.copy();

					final Node copiedNode = copiedGraph.getNodeFromID(node.bpmnObject().id());
					final Node copiedNodeToLink = copiedGraph.getNodeFromID(nodeToLink.bpmnObject().id());

					copiedNode.addChild(copiedNodeToLink);
					copiedNodeToLink.addParent(copiedNode);
					copiedGraph.removeInitialNode(copiedNodeToLink);
					copiedGraph.removeInitialNode(copiedNode);

					if (copiedNode.parentNodes().isEmpty())
					{
						copiedGraph.addInitialNode(copiedNode);
					}

					/*
						Now, some transitions in the dependency graph may be useless:
							- The ones between copiedNode and some of its children which are now successors of copiedNodeToLink
							- The ones between some parents of copiedNode and some of their children which are now successors of copiedNodeToLink
					 */

					//Remove children of copied node reachable from copied node to link
					for (Iterator<Node> iterator = copiedNode.childNodes().iterator(); iterator.hasNext(); )
					{
						final Node child = iterator.next();

						if (!child.equals(copiedNodeToLink))
						{
							if (copiedNodeToLink.hasSuccessor(child)
								|| copiedNodeToLink.equals(child))
							{
								iterator.remove();
								child.removeParent(copiedNode);
							}
						}
					}

					//Remove children of parents of copied node reachable from copied node to link
					for (Node parent : copiedNode.parentNodes())
					{
						for (Iterator<Node> iterator = parent.childNodes().iterator(); iterator.hasNext(); )
						{
							final Node parentChild = iterator.next();

							if (!parentChild.equals(copiedNode))
							{
								if (copiedNodeToLink.hasSuccessor(parentChild)
									|| copiedNodeToLink.equals(parentChild))
								{
									iterator.remove();
									parentChild.removeParent(parent);
								}
							}
						}
					}

					if (this.graphInList(alreadyComputedGraphs, copiedGraph))
					{
						continue;
					}

					alreadyComputedGraphs.add(copiedGraph);

					/*
						Changed due to the SCC bug (see PartialOrder2AbstractGraph.java lines 125 to 134).
						In the new version, we continue iterating (i.e., adding new constraints) even when
						we found a valid graph (i.e., a graph that validates the SCC constraints) because
						it may not be possible to generate an abstract graph from these dependencies due to
						the bug. To solve the problem, we need to add other constraints.

						if (new DependenciesAnalyzer().verifyDependencySet(copiedGraph.toDependencySet()))
						{
							System.out.println("Valid graph:\n\n" + copiedGraph.stringify(0));
							validGraphs.add(copiedGraph);
						}
						else
						{
							this.computeOptimalDependencies(copiedGraph, validGraphs, alreadyComputedGraphs);
						}
					*/

					if (new DependenciesAnalyzer().verifyDependencySet(copiedGraph.toDependencySet()))
					{
						validGraphs.add(copiedGraph);
					}

					this.computeOptimalDependencies(copiedGraph, validGraphs, alreadyComputedGraphs);
				}
			}
		}
	}

	private DependencyGraph findOptimalGraph(final ArrayList<DependencyGraph> dependencyGraphs)
	{
		Graph firstGraph = null;
		int firstGraphIndex = 0;
		DependencyGraph optimalGraph = null;

		for (; firstGraphIndex < dependencyGraphs.size(); firstGraphIndex++)
		{
			optimalGraph = dependencyGraphs.get(firstGraphIndex);
			final HashSet<Dependency> dependencies = optimalGraph.toDependencySet();
			final DependenciesParser dependenciesParser = new DependenciesParser(dependencies, this.graph, this.loopFinder);

			try
			{
				dependenciesParser.parseFromSet();
			}
			catch (BadDependencyException e)
			{
				throw new IllegalStateException("Should never happen.");
			}

			final Cluster cluster = this.getDependenciesCluster(dependenciesParser.mainCluster(), dependencies);

			try
			{
				firstGraph = this.dependencyGraphToBpmnGraph(cluster);
				break;
			}
			catch (BadDependencyException ignored)
			{
				/*
					Happens when the dependencies validate the SCC constraints but are not
				 	sufficient to generate the abstract graph (see PartialOrder2AbstractGraph.java
				 	lines 125 to 134)
				 */
			}
		}

		if (firstGraph == null)
		{
			throw new IllegalStateException("No dependency graph making the generation of the abstract" +
					" graph possible could be found. Aborting.");
		}

		int executionTime = new LightOptimizer(new ArrayList<>(new GraphToList(firstGraph).convert()), firstGraph).computeProcessExecutionTime();

		for (int i = firstGraphIndex + 1; i < dependencyGraphs.size(); i++)
		{
			final DependencyGraph currentDependencyGraph = dependencyGraphs.get(i);
			final HashSet<Dependency> currentDependencies = currentDependencyGraph.toDependencySet();
			final DependenciesParser currentDependenciesParser = new DependenciesParser(currentDependencies, this.graph, this.loopFinder);

			try
			{
				currentDependenciesParser.parseFromSet();
			}
			catch (BadDependencyException e)
			{
				throw new IllegalStateException("Should never happen.");
			}

			final Graph currentBpmnGraph;

			try
			{
				currentBpmnGraph = this.dependencyGraphToBpmnGraph(this.getDependenciesCluster(currentDependenciesParser.mainCluster(), currentDependencies));
			}
			catch (BadDependencyException e)
			{
				//If the current graph is not valid, go to the next one
				continue;
			}

			final int currentExecutionTime = new LightOptimizer(new ArrayList<>(new GraphToList(currentBpmnGraph).convert()), currentBpmnGraph).computeProcessExecutionTime();

			if (currentExecutionTime < executionTime)
			{
				executionTime = currentExecutionTime;
				optimalGraph = currentDependencyGraph;
			}
		}

		return optimalGraph;
	}

	private Graph dependencyGraphToBpmnGraph(final Cluster cluster) throws BadDependencyException
	{
		final AbstractGraphsGenerator generator = new AbstractGraphsGenerator(cluster);
		generator.generateAbstractGraphs();

		final BPMNGenerator bpmnGenerator = new BPMNGenerator(cluster);
		bpmnGenerator.generate();

		return bpmnGenerator.optimalGraph();
	}

	private boolean graphInList(final ArrayList<DependencyGraph> dependencyGraphs,
								final DependencyGraph dependencyGraph)
	{
		for (DependencyGraph currentGraph : dependencyGraphs)
		{
			if (currentGraph.toDependencySet().equals(dependencyGraph.toDependencySet()))
			{
				return true;
			}
		}

		return false;
	}

	private String stringifyHashsets(final HashSet<Node> hashSet)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("[");

		for (Node node : hashSet)
		{
			builder.append(node.bpmnObject().id())
					.append(" - ");
		}

		builder.append("]");

		return builder.toString();
	}

	private Cluster getDependenciesCluster(final Cluster cluster,
										   final HashSet<Dependency> dependencies)
	{
		final Dependency dependency = dependencies.iterator().next();

		if (cluster.dependencies().contains(dependency))
		{
			return cluster;
		}

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedLoop loop = (EnhancedLoop) enhancedNode;

				final Cluster incCluster = this.getDependenciesCluster(loop.entryToExitCluster(), dependencies);

				if (incCluster != null)
				{
					return incCluster;
				}

				final Cluster outCluster = this.getDependenciesCluster(loop.exitToEntryCluster(), dependencies);

				if (outCluster != null)
				{
					return outCluster;
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedChoice choice = (EnhancedChoice) enhancedNode;

				for (Cluster subCluster : choice.clusters())
				{
					final Cluster goodCluster = this.getDependenciesCluster(subCluster, dependencies);

					if (goodCluster != null)
					{
						return goodCluster;
					}
				}
			}
		}

		return null;
	}
}
