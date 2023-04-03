package refactoring.partial_order_to_bpmn;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import bpmn.types.process.events.Event;
import refactoring.Cluster;
import refactoring.dependencies.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class BPMNGenerator
{
	private final Cluster mainCluster;

	private Graph optimalGraph;

	public BPMNGenerator(final Cluster mainCluster)
	{
		this.mainCluster = mainCluster;
	}

	public Graph generate()
	{
		return this.optimalGraph = this.generateOptimalGraph();
	}

	public Graph optimalGraph()
	{
		return this.optimalGraph;
	}

	public void unprocessClusters()
	{
		this.unprocessClusters(this.mainCluster);
	}

	//Private methods

	/**
	 * This method aims at generating the optimal process, using the clusters and the abstract
	 * graphs generated for each of them.
	 * To be done properly, the generation needs to start by the most nested constructs (i.e.,
	 * clusters) and progress to the less nested ones.
	 * To do so, we will rely on the "processed" value of the clusters, which is first set to
	 * False, and then updated to True when it has been processed.
	 * A cluster is processable whenever it contains no sub cluster, or when all its subclusters
	 * have already been processed (or a mixture of both conditions).
	 *
	 * @return the optimal BPMN graph
	 */
	private EnhancedGraph generateOptimalGraph()
	{
		final HashSet<Cluster> processableClusters = new HashSet<>();
		this.findProcessableClusters(this.mainCluster, processableClusters);
		EnhancedGraph graph = null;

		while (!processableClusters.isEmpty())
		{
			graph = null;

			for (Cluster processableCluster : processableClusters)
			{
				processableCluster.setProcessed();
				graph = this.generateClusterGraph(processableCluster);
				processableCluster.setBpmnGraph(graph);
			}

			processableClusters.clear();
			this.findProcessableClusters(this.mainCluster, processableClusters);
		}

		if (graph == null)
		{
			throw new IllegalStateException();
		}

		final Node startEvent = new Node(new Event(BpmnProcessType.START_EVENT, "StartEvent"));
		final Node endEvent = new Node(new Event(BpmnProcessType.END_EVENT, "EndEvent"));
		final Node startFlow = new Node(BpmnProcessFactory.generateSequenceFlow(startEvent.bpmnObject().id(), graph.initialNode().bpmnObject().id()));
		final Node lastElement = graph.endNode();
		final Node endFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastElement.bpmnObject().id(), endEvent.bpmnObject().id()));
		startEvent.addChild(startFlow);
		startFlow.addParent(startEvent);
		startFlow.addChild(graph.initialNode());
		graph.initialNode().addParent(startFlow);
		lastElement.addChild(endFlow);
		endFlow.addParent(lastElement);
		endFlow.addChild(endEvent);
		endEvent.addParent(endFlow);

		final EnhancedGraph optimalGraph =  new EnhancedGraph(startEvent);
		optimalGraph.setEndNode(endEvent);

		return optimalGraph;
	}

	private void findProcessableClusters(final Cluster currentCluster,
										 final HashSet<Cluster> innerClusters)
	{
		if (currentCluster.hasBeenProcessed()
			|| currentCluster.isEmpty())
		{
			return;
		}

		boolean currentClusterIsProcessable = true;

		for (EnhancedNode node : currentCluster.elements())
		{
			if (node.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) node;

				for (Cluster cluster : enhancedChoice.clusters())
				{
					if (!cluster.hasBeenProcessed()
							&& !cluster.isEmpty())
					{
						currentClusterIsProcessable = false;
						this.findProcessableClusters(cluster, innerClusters);
					}
				}
			}
			else if (node.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) node;

				if (!enhancedLoop.entryToExitCluster().hasBeenProcessed()
					&& !enhancedLoop.entryToExitCluster().isEmpty())
				{
					currentClusterIsProcessable = false;
					this.findProcessableClusters(enhancedLoop.entryToExitCluster(), innerClusters);
				}

				if (!enhancedLoop.exitToEntryCluster().hasBeenProcessed()
						&& !enhancedLoop.exitToEntryCluster().isEmpty())
				{
					currentClusterIsProcessable = false;
					this.findProcessableClusters(enhancedLoop.exitToEntryCluster(), innerClusters);
				}
			}
		}

		if (currentClusterIsProcessable)
		{
			innerClusters.add(currentCluster);
		}
	}

	/**
	 * This function generates the BPMN graph corresponding to the cluster passed as parameter.
	 * We suppose that the given clusters are ready to be processed, meaning that the inner
	 * clusters that they may contain have already been processed earlier.
	 *
	 * @param cluster the cluster to transform into BPMN
	 * @return the BPMN graph corresponding to the cluster
	 */
	private EnhancedGraph generateClusterGraph(final Cluster cluster)
	{
		final HashSet<EnhancedNode> nonDependentNodes = new HashSet<>(cluster.elements());
		final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelGateway());
		final ArrayList<Node> lastNodes = new ArrayList<>();
		final EnhancedGraph graph;

		//Remove all nodes belonging to dependencies
		for (Iterator<EnhancedNode> iterator = nonDependentNodes.iterator(); iterator.hasNext(); )
		{
			final EnhancedNode enhancedNode = iterator.next();

			for (HashSet<Dependency> dependencySet : cluster.graphsWithDependencies().values())
			{
				boolean shouldBreak = false;

				for (Dependency dependency : dependencySet)
				{
					if (dependency.firstNode().equals(enhancedNode.node())
						|| dependency.secondNode().equals(enhancedNode.node()))
					{
						iterator.remove();
						shouldBreak = true;
						break;
					}
				}

				if (shouldBreak)
				{
					break;
				}
			}
		}

		//Add all nodes in parallel
		for (EnhancedNode enhancedNode : nonDependentNodes)
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				lastNodes.add(BPMNUtils.generateChoice(enhancedNode, parallelSplit));
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				lastNodes.add(BPMNUtils.generateLoop(enhancedNode, parallelSplit));
			}
			else
			{
				lastNodes.add(BPMNUtils.generateTask(enhancedNode, parallelSplit));
			}
		}

		//Add all abstract graphs of the current cluster in parallel
		for (AbstractGraph abstractGraph : cluster.abstractGraphs())
		{
			final EnhancedGraph bpmnGraph = BPMNUtils.generateBPMNFromAbstractGraph(abstractGraph, cluster);

			if (bpmnGraph != null)
			{
				final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(parallelSplit.bpmnObject().id(), bpmnGraph.initialNode().bpmnObject().id()));

				parallelSplit.addChild(sequenceFlow);
				sequenceFlow.addParent(parallelSplit);

				sequenceFlow.addChild(bpmnGraph.initialNode());
				bpmnGraph.initialNode().addParent(sequenceFlow);

				lastNodes.add(bpmnGraph.endNode());
			}
		}

		//If parallel gateway has only 1 child, remove it, otherwise, close it
		if (parallelSplit.childNodes().size() <= 1)
		{
			final Node singleFlow = parallelSplit.childNodes().iterator().next();
			final Node singleFlowChild = singleFlow.childNodes().iterator().next();

			singleFlow.removeChildren(singleFlowChild);
			singleFlowChild.removeParent(singleFlow);
			graph = new EnhancedGraph(singleFlowChild);
			graph.setEndNode(lastNodes.get(0));
		}
		else
		{
			final Node parallelMerge = new Node(BpmnProcessFactory.generateParallelGateway());
			((Gateway) parallelMerge.bpmnObject()).markAsMergeGateway();

			for (Node lastNode : lastNodes)
			{
				final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), parallelMerge.bpmnObject().id()));
				lastNode.addChild(sequenceFlow);
				sequenceFlow.addParent(lastNode);
				sequenceFlow.addChild(parallelMerge);
				parallelMerge.addParent(sequenceFlow);
			}

			graph = new EnhancedGraph(parallelSplit);
			graph.setEndNode(parallelMerge);
		}

		return graph;
	}

	private void unprocessClusters(final Cluster cluster)
	{
		cluster.unprocess();

		for (EnhancedNode node : cluster.elements())
		{
			if (node.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) node;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.unprocessClusters(choiceCluster);
				}
			}
			else if (node.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) node;

				this.unprocessClusters(enhancedLoop.entryToExitCluster());
				this.unprocessClusters(enhancedLoop.exitToEntryCluster());
			}
		}
	}
}
