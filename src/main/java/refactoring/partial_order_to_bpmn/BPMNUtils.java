package refactoring.partial_order_to_bpmn;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import bpmn.types.process.Task;
import bpmn.types.process.events.Event;
import refactoring.Cluster;
import refactoring.dependencies.*;

import java.util.ArrayList;

public class BPMNUtils
{
	private BPMNUtils()
	{
		
	}

	public static EnhancedGraph generateEntireBPMNFromAbstractGraph(final AbstractGraph abstractGraph,
																	final Cluster cluster)
	{
		//TODO VOIR SI POSSIBLE DE REDUIRE LE TEMPS DE CA (10 à 20ms par génération) * plusieurs milliers voire dizaines de milliers de génération = long
		final long start = System.currentTimeMillis();

		final EnhancedGraph tempGraph = BPMNUtils.generateBPMNFromAbstractGraph(abstractGraph, cluster);

		if (tempGraph == null)
		{
			return null;
		}

		final Node startEvent = new Node(new Event(BpmnProcessType.START_EVENT, "StartEvent"));
		final Node endEvent = new Node(new Event(BpmnProcessType.END_EVENT, "EndEvent"));
		final Node firstFlow = new Node(BpmnProcessFactory.generateSequenceFlow(startEvent.bpmnObject().id(), tempGraph.initialNode().bpmnObject().id()));
		final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow(tempGraph.endNode().bpmnObject().id(), endEvent.bpmnObject().id()));

		startEvent.addChild(firstFlow);
		firstFlow.addParent(startEvent);

		firstFlow.addChild(tempGraph.initialNode());
		tempGraph.initialNode().addParent(firstFlow);

		tempGraph.endNode().addChild(lastFlow);
		lastFlow.addParent(tempGraph.endNode());

		lastFlow.addChild(endEvent);
		endEvent.addParent(lastFlow);

		final long end = System.currentTimeMillis();
		//System.out.println("Generation of BPMN process took " + (end - start) + "ms.");

		return new EnhancedGraph(startEvent);
	}

	public static EnhancedGraph generateBPMNFromAbstractGraph(final AbstractGraph abstractGraph,
															  final Cluster cluster)
	{
		if (abstractGraph.isEmpty())
		{
			return null;
		}

		AbstractNode currentAbstractNode = abstractGraph.startNode();
		EnhancedGraph graph = null;

		while (true)
		{
			final ArrayList<Node> lastNodes = new ArrayList<>();
			final Node parallelSplit = new Node(BpmnProcessFactory.generateParallelGateway());

			for (Node node : currentAbstractNode.listNodes())
			{
				final EnhancedNode enhancedNode = cluster.findEnhancedNodeFrom(node);

				if (node.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
				{
					//Can be a loop or a choice
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
						throw new IllegalStateException("BPMN Task can not be associated with exclusive gateway.");
					}
				}
				else
				{
					lastNodes.add(BPMNUtils.generateTask(enhancedNode, parallelSplit));
				}
			}

			for (AbstractGraph subGraph : currentAbstractNode.subGraphs())
			{
				final EnhancedGraph bpmnSubGraph = BPMNUtils.generateBPMNFromAbstractGraph(subGraph, cluster);

				if (bpmnSubGraph != null)
				{
					final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(parallelSplit.bpmnObject().id(), bpmnSubGraph.initialNode().bpmnObject().id()));
					parallelSplit.addChild(sequenceFlow);
					sequenceFlow.addParent(parallelSplit);
					sequenceFlow.addChild(bpmnSubGraph.initialNode());
					bpmnSubGraph.initialNode().addParent(sequenceFlow);
					lastNodes.add(bpmnSubGraph.endNode());
				}
			}

			if (parallelSplit.childNodes().size() <= 1)
			{
				final Node childFlow = parallelSplit.childNodes().iterator().next();
				final Node childFlowChild = childFlow.childNodes().iterator().next();

				childFlow.removeChildren(childFlowChild);
				childFlowChild.removeParent(childFlow);

				if (graph == null)
				{
					graph = new EnhancedGraph(childFlowChild);
				}
				else
				{
					final Node branchFlow = new Node(BpmnProcessFactory.generateSequenceFlow(graph.endNode().bpmnObject().id(), childFlowChild.bpmnObject().id()));
					graph.endNode().addChild(branchFlow);
					branchFlow.addParent(graph.endNode());
					branchFlow.addChild(childFlowChild);
					childFlowChild.addParent(branchFlow);
				}

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

				if (graph == null)
				{
					graph = new EnhancedGraph(parallelSplit);
				}
				else
				{
					final Node branchFlow = new Node(BpmnProcessFactory.generateSequenceFlow(graph.endNode().bpmnObject().id(), parallelSplit.bpmnObject().id()));
					graph.endNode().addChild(branchFlow);
					branchFlow.addParent(graph.endNode());
					branchFlow.addChild(parallelSplit);
					parallelSplit.addParent(branchFlow);
				}

				graph.setEndNode(parallelMerge);
			}

			if (currentAbstractNode.successors().isEmpty())
			{
				break;
			}
			else
			{
				if (currentAbstractNode.successors().size() > 1)
				{
					throw new IllegalStateException("Too much successors nodes found.");
				}

				currentAbstractNode = currentAbstractNode.successors().iterator().next();
			}
		}

		//System.out.println("Abstract graph TO BPMN:\n\n" + graph);

		return graph;
	}

	public static Node generateChoice(final EnhancedNode enhancedNode,
									   final Node parallelSplit)
	{
		//TODO voir si besoin de gérer le cas des choix avec chemin vide
		//If the node is a choice, just get the corresponding graph for each cluster
		final EnhancedChoice choice = (EnhancedChoice) enhancedNode;
		final Node exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveGateway());
		final ArrayList<Node> clustersLastNodes = new ArrayList<>();

		//Branch all choice paths
		for (Cluster subCluster : choice.clusters())
		{
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(exclusiveSplit.bpmnObject().id(), subCluster.bpmnGraph().initialNode().bpmnObject().id()));
			sequenceFlow.bpmnObject().setProbability(subCluster.probability());
			//Add sequence flow as child of the exclusive split
			exclusiveSplit.addChild(sequenceFlow);
			sequenceFlow.addParent(exclusiveSplit);
			//Add subgraph as child of the sequence flow
			sequenceFlow.addChild(subCluster.bpmnGraph().initialNode());
			subCluster.bpmnGraph().initialNode().addParent(sequenceFlow);
			//Add last node of the cluster
			clustersLastNodes.add(subCluster.bpmnGraph().endNode());
		}

		//Finalize choice
		final Node exclusiveMerge = new Node(BpmnProcessFactory.generateExclusiveGateway());
		((Gateway) exclusiveMerge.bpmnObject()).markAsMergeGateway();

		for (Node lastNode : clustersLastNodes)
		{
			final Node branchFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), exclusiveMerge.bpmnObject().id()));
			lastNode.addChild(branchFlow);
			branchFlow.addParent(lastNode);
			branchFlow.addChild(exclusiveMerge);
			exclusiveMerge.addParent(branchFlow);
		}

		//Branch to parallel and store last node
		final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(parallelSplit.bpmnObject().id(), exclusiveSplit.bpmnObject().id()));
		parallelSplit.addChild(sequenceFlow);
		sequenceFlow.addParent(parallelSplit);
		sequenceFlow.addChild(exclusiveSplit);
		exclusiveSplit.addParent(sequenceFlow);

		return exclusiveMerge;
	}

	public static Node generateLoop(final EnhancedNode enhancedNode,
									 final Node parallelSplit)
	{
		//If the node is a loop, just get the corresponding graphs
		final EnhancedLoop loop = (EnhancedLoop) enhancedNode;

		final Node exclusiveMerge = new Node(BpmnProcessFactory.generateExclusiveGateway());
		((Gateway) exclusiveMerge.bpmnObject()).markAsMergeGateway();
		final Node exclusiveSplit = new Node(BpmnProcessFactory.generateExclusiveGateway());
		final Node parallelFlow = new Node(BpmnProcessFactory.generateSequenceFlow(parallelSplit.bpmnObject().id(), exclusiveMerge.bpmnObject().id()));
		parallelSplit.addChild(parallelFlow);
		parallelFlow.addParent(parallelSplit);
		parallelFlow.addChild(exclusiveMerge);
		exclusiveMerge.addParent(parallelFlow);

		if (loop.entryToExitCluster().isEmpty())
		{
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(exclusiveMerge.bpmnObject().id(), exclusiveSplit.bpmnObject().id()));
			//Add sequence flow as child of merge gateway
			exclusiveMerge.addChild(sequenceFlow);
			sequenceFlow.addParent(exclusiveMerge);
			//Add split gateway as child of sequence flow
			sequenceFlow.addChild(exclusiveSplit);
			exclusiveSplit.addParent(sequenceFlow);
		}
		else
		{
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(exclusiveMerge.bpmnObject().id(), loop.entryToExitCluster().bpmnGraph().initialNode().bpmnObject().id()));
			//Add sequence flow as child of merge gateway
			exclusiveMerge.addChild(sequenceFlow);
			sequenceFlow.addParent(exclusiveMerge);
			//Add graph first node as child of sequence flow
			sequenceFlow.addChild(loop.entryToExitCluster().bpmnGraph().initialNode());
			loop.entryToExitCluster().bpmnGraph().initialNode().addParent(sequenceFlow);
			//Find last node and connect it to the split gateway
			final Node lastNode = loop.entryToExitCluster().bpmnGraph().endNode();
			final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), exclusiveSplit.bpmnObject().id()));
			lastNode.addChild(lastFlow);
			lastFlow.addParent(lastNode);
			lastFlow.addChild(exclusiveSplit);
			exclusiveSplit.addParent(lastFlow);
		}

		if (loop.exitToEntryCluster().isEmpty())
		{
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(exclusiveSplit.bpmnObject().id(), exclusiveMerge.bpmnObject().id()));
			//Add sequence flow as child of split gateway
			exclusiveSplit.addChild(sequenceFlow);
			sequenceFlow.addParent(exclusiveSplit);
			//Add merge gateway as child of sequence flow
			sequenceFlow.addChild(exclusiveMerge);
			exclusiveMerge.addParent(sequenceFlow);
			//Add probability
			sequenceFlow.bpmnObject().setProbability(loop.exitToEntryCluster().probability());
		}
		else
		{
			final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(exclusiveSplit.bpmnObject().id(), loop.exitToEntryCluster().bpmnGraph().initialNode().bpmnObject().id()));
			//Add sequence flow as child of split gateway
			exclusiveSplit.addChild(sequenceFlow);
			sequenceFlow.addParent(exclusiveSplit);
			//Add graph first node as child of sequence flow
			sequenceFlow.addChild(loop.exitToEntryCluster().bpmnGraph().initialNode());
			loop.exitToEntryCluster().bpmnGraph().initialNode().addParent(sequenceFlow);
			//Find last node and connect it to the split gateway
			final Node lastNode = loop.exitToEntryCluster().bpmnGraph().endNode();
			final Node lastFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), exclusiveMerge.bpmnObject().id()));
			lastNode.addChild(lastFlow);
			lastFlow.addParent(lastNode);
			lastFlow.addChild(exclusiveMerge);
			exclusiveMerge.addParent(lastFlow);
			//Add probability
			sequenceFlow.bpmnObject().setProbability(loop.exitToEntryCluster().probability());
		}

		return exclusiveSplit;
	}

	public static Node generateTask(final EnhancedNode enhancedNode,
									 final Node parallelSplit)
	{
		final Node task = enhancedNode.node().weakCopy();
		final Node sequenceFlow = new Node(BpmnProcessFactory.generateSequenceFlow(parallelSplit.bpmnObject().id(), task.bpmnObject().id()));
		parallelSplit.addChild(sequenceFlow);
		sequenceFlow.addParent(parallelSplit);
		sequenceFlow.addChild(task);
		task.addParent(sequenceFlow);

		return task;
	}
}
