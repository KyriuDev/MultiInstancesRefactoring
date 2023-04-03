package refactoring;

import other.Utils;
import bpmn.graph.Node;
import refactoring.dependencies.*;
import refactoring.partial_order_to_bpmn.AbstractGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class Cluster
{
	private final ArrayList<AbstractGraph> abstractGraphs;
	private double probability;
	private final HashSet<EnhancedNode> elements;
	private final HashSet<Dependency> tempDependencies;

	private final ArrayList<DependencyGraph> tempDependencyGraphs;
	private final HashMap<DependencyGraph, HashSet<Dependency>> graphsWithDependencies;
	private final String id;

	private boolean processed;

	private EnhancedGraph bpmnGraph;

	private final HashSet<AbstractGraph> possiblyOptimalBalancedClusters;
	private final HashMap<String, EnhancedNode> enhancedNodesHashMap;

	public Cluster()
	{
		this.elements = new HashSet<>();
		this.tempDependencies = new HashSet<>();
		this.probability = 1d;
		this.id = Utils.generateRandomIdentifier(20);
		this.tempDependencyGraphs = new ArrayList<>();
		this.abstractGraphs = new ArrayList<>();
		this.graphsWithDependencies = new HashMap<>();
		this.processed = false;
		this.bpmnGraph = null;
		this.possiblyOptimalBalancedClusters = new HashSet<>();
		this.enhancedNodesHashMap = new HashMap<>();
	}

	public void addPossiblyOptimalBalancedAbstractGraph(final AbstractGraph abstractGraph)
	{
		this.possiblyOptimalBalancedClusters.add(abstractGraph);
	}

	public void addAllPossiblyOptimalBalancedAbstractGraphs(final Collection<AbstractGraph> abstractGraphs)
	{
		this.possiblyOptimalBalancedClusters.addAll(abstractGraphs);
	}

	public HashSet<AbstractGraph> possiblyOptimalBalancedGraphs()
	{
		return this.possiblyOptimalBalancedClusters;
	}

	public boolean hasBeenCorrected()
	{
		return this.possiblyOptimalBalancedClusters.isEmpty();
	}

	public void addElement(final EnhancedNode enhancedNode)
	{
		this.elements.add(enhancedNode);
	}

	public void addDependency(final Dependency dependency)
	{
		this.tempDependencies.add(dependency);
	}

	public HashMap<DependencyGraph, HashSet<Dependency>> graphsWithDependencies()
	{
		return this.graphsWithDependencies;
	}

	public void setProbability(final double probability)
	{
		this.probability = probability;
	}

	public double probability()
	{
		return this.probability;
	}

	public void setProcessed()
	{
		this.processed = true;
	}

	public void unprocess()
	{
		this.processed = false;
	}

	public boolean hasBeenProcessed()
	{
		return this.processed;
	}

	public boolean isEmpty()
	{
		return this.elements.isEmpty() && this.graphsWithDependencies.isEmpty();
	}

	public void addDependencyGraph(final DependencyGraph graph)
	{
		this.tempDependencyGraphs.add(graph);
	}

	public ArrayList<DependencyGraph> dependencyGraphs()
	{
		return this.tempDependencyGraphs;
	}

	public HashSet<Dependency> dependencies()
	{
		return this.tempDependencies;
	}

	public HashSet<EnhancedNode> elements()
	{
		return this.elements;
	}

	public void addAbstractGraph(final AbstractGraph abstractGraph)
	{
		this.abstractGraphs.add(abstractGraph);
	}

	public void setBpmnGraph(final EnhancedGraph graph)
	{
		this.bpmnGraph = graph;
	}

	public EnhancedGraph bpmnGraph()
	{
		return this.bpmnGraph;
	}

	public ArrayList<AbstractGraph> abstractGraphs()
	{
		return this.abstractGraphs;
	}

	public String showDependencyGraphs(final int depth)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("	".repeat(depth))
				.append("Current cluster has the following dependency graphs:\n");

		int i = 1;

		for (DependencyGraph dependencyGraph : this.tempDependencyGraphs)
		{
			builder.append("	".repeat(depth + 1))
					.append("Graph n°")
					.append(i++)
					.append(":\n\n");

			for (Node initialNode : dependencyGraph.initialNodes())
			{
				builder.append(initialNode.stringify(depth + 3, new ArrayList<>()))
						.append("\n\n");
			}
		}

		for (EnhancedNode enhancedNode : this.elements)
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster cluster : enhancedChoice.clusters())
				{
					builder.append(cluster.showDependencyGraphs(depth + 1))
							.append("\n");
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				builder.append(enhancedLoop.entryToExitCluster())
						.append("\n")
						.append(enhancedLoop.exitToEntryCluster())
						.append("\n");
			}
		}

		return builder.toString();
	}

	public String stringify(final int depth)
	{
		final StringBuilder builder = new StringBuilder();

		if (this.isEmpty())
		{
			builder.append("	".repeat(depth))
					.append("Cluster |")
					.append(this.id)
					.append("| contains no elements.");
		}
		else
		{
			builder.append("	".repeat(depth))
					.append("Cluster |")
					.append(this.id)
					.append("| contains the following elements:\n");

			for (EnhancedNode enhancedNode : this.elements)
			{
				builder.append(enhancedNode.stringify(depth + 1))
						.append("\n");
			}

			if (!this.tempDependencies.isEmpty())
			{
				builder.append("\n")
						.append("	".repeat(depth))
						.append("and the following dependencies:\n");

				for (Dependency dependency : this.tempDependencies)
				{
					builder.append(dependency.stringify(depth + 1))
							.append("\n");
				}
			}
		}

		return builder.toString();
	}

	public EnhancedNode findEnhancedNodeFrom(Node node)
	{
		if (this.enhancedNodesHashMap.isEmpty())
		{
			for (EnhancedNode currentNode : this.elements)
			{
				if (currentNode.node().equals(node))
				{
					return currentNode;
				}
			}
		}
		else
		{
			return this.enhancedNodesHashMap.get(node.bpmnObject().id());
		}

		throw new IllegalStateException("No corresponding enhanced node found for node |" + node.bpmnObject().id() + "|.");
	}

	public void hashElements()
	{
		for (EnhancedNode enhancedNode : this.elements)
		{
			this.enhancedNodesHashMap.put(enhancedNode.node().bpmnObject().id(), enhancedNode);
		}
	}

	public String id()
	{
		return this.id;
	}

	//Overrides

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Cluster))
		{
			return false;
		}

		return this.id.equals(((Cluster) o).id);
	}

	@Override
	public int hashCode()
	{
		int hash = 7;

		for (int i = 0; i < this.id.length(); i++)
		{
			hash = hash * 31 + this.id.charAt(i);
		}

		return hash;
	}
}
