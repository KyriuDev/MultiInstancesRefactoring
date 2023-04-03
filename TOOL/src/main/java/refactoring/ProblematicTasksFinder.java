package refactoring;

import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.GraphUtils;
import bpmn.graph.Node;
import bpmn.types.process.*;
import bpmn.writing.generation.GraphicalGenerationWriter;
import javassist.tools.Dump;
import loops_management.Loop;
import loops_management.LoopFinder;
import other.Dumper;
import other.Pair;
import other.Triple;
import resources.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import static refactoring.dependencies.DependenciesParser.DUMMY_NODE;

public class ProblematicTasksFinder
{
	private static final int THRESHOLD = 100; //TODO TROUVER OPTIMAL
	private ArrayList<Node> problematicTasks;
	private final ResourcePool realPool;
	private final ResourcePool optimalPool;
	private final GlobalResourceSet globalResourceSet;
	private final Graph originalGraph;
	private final Graph copiedGraph;
	private final ArrayList<BpmnProcessObject> originalObjects;
	private final ArrayList<Resource> problematicResources;
	private final LoopFinder loopFinder;
	private final Optimizer optimizer;
	private final int optimalProcessDuration;
	private final int iat;

	public ProblematicTasksFinder(final ResourcePool realPool,
							 	  final ResourcePool optimalPool,
						 		  final Graph originalGraph,
								  final Optimizer optimizer,
								  final GlobalResourceSet globalResourceSet,
								  final int iat)
	{
		this.realPool = realPool;
		this.optimalPool = optimalPool;
		this.originalGraph = originalGraph;
		//Dumper.getInstance().dump(this.originalGraph, "avant_copy");
		this.copiedGraph = this.originalGraph.weakCopy();
		//Dumper.getInstance().dump(this.copiedGraph, "apres_copy");
		this.problematicResources = new ArrayList<>();
		this.loopFinder = new LoopFinder(this.copiedGraph);
		this.loopFinder.findLoops();
		this.optimizer = optimizer;
		this.globalResourceSet = globalResourceSet;
		final GraphToList graphToList = new GraphToList(this.copiedGraph);
		graphToList.convert();
		this.originalObjects = graphToList.objectsList();
		this.optimalProcessDuration = new LightOptimizer(this.originalObjects, this.copiedGraph).computeProcessExecutionTime();
		this.iat = iat;
	}

	public ArrayList<Node> findProblematicTasks()
	{
		this.computeProblematicResources(this.optimizer);

		if (this.problematicResources.isEmpty())
		{
			return this.problematicTasks = new ArrayList<>();
		}

		final HashMap<Resource, ArrayList<Triple<Node, ArrayList<Node>, Double>>> nonParallelizableTasks = this.computeNonParallelizableTasksV2();
		this.problematicTasks = this.computeProblematicTasks(nonParallelizableTasks);

		return this.problematicTasks;
	}

	public ArrayList<Node> problematicTasks()
	{
		return this.problematicTasks;
	}

	//Private methods

	private void computeProblematicResources(final Optimizer optimizer)
	{
		final HashMap<Resource, Integer> absorbancePerResource = optimizer.computeProcessAbsorbance(this.realPool);

		for (Resource resource : absorbancePerResource.keySet())
		{
			final int absorbance = absorbancePerResource.get(resource);

			if (absorbance > THRESHOLD)
			{
				this.problematicResources.add(resource);
			}
		}
	}

	private HashMap<Resource, ArrayList<Triple<Node, ArrayList<Node>, Double>>> computeNonParallelizableTasksV2()
	{
		final HashMap<Resource, ArrayList<Triple<Node, ArrayList<Node>, Double>>> nonParallelizableTasks = new HashMap<>();

		for (BpmnProcessObject object : this.originalObjects)
		{
			if (object instanceof Task)
			{
				final Task task = (Task) object;

				for (Resource problematicResource : this.problematicResources)
				{
					if (task.resourceUsage().resources().contains(problematicResource))
					{
						final Node taskNode = this.copiedGraph.getNodeFromID(task.id());
						final ArrayList<ArrayList<Node>> allRestrictiveNodes = new ArrayList<>();
						final ArrayList<Node> firstRestrictiveNodes = new ArrayList<>();
						allRestrictiveNodes.add(firstRestrictiveNodes);
						this.findRestrictiveNodesOf(taskNode.bpmnObject().id(), this.copiedGraph.initialNode(), firstRestrictiveNodes, allRestrictiveNodes, new HashSet<>());
						final ArrayList<Node> finalRestrictiveNodes = this.finalizeRestrictiveNodes(allRestrictiveNodes);

						final double score;

						if (finalRestrictiveNodes.isEmpty())
						{
							score = this.computeScoreOf(taskNode, problematicResource);
						}
						else
						{
							final Node mostRestrictiveNode = finalRestrictiveNodes.get(0);

							if (mostRestrictiveNode.bpmnObject() instanceof SequenceFlow)
							{
								score = this.computeScoreOf(mostRestrictiveNode.parentNodes().iterator().next(), problematicResource);
							}
							else
							{
								score = this.computeScoreOf(mostRestrictiveNode, problematicResource);
							}
						}

						final Triple<Node, ArrayList<Node>, Double> triple = new Triple<>(taskNode, finalRestrictiveNodes, score);
						final ArrayList<Triple<Node, ArrayList<Node>, Double>> list = nonParallelizableTasks.computeIfAbsent(problematicResource, a -> new ArrayList<>());
						list.add(triple);
					}
				}
			}
		}

		return nonParallelizableTasks;
	}

	private ArrayList<Node> finalizeRestrictiveNodes(final ArrayList<ArrayList<Node>> restrictiveNodes)
	{
		final ArrayList<Node> finalRestrictiveNodes = new ArrayList<>();

		for (ArrayList<Node> currentRestrictiveNodes : restrictiveNodes)
		{
			if (!currentRestrictiveNodes.isEmpty())
			{
				if (!finalRestrictiveNodes.isEmpty())
				{
					throw new IllegalStateException();
				}

				finalRestrictiveNodes.addAll(currentRestrictiveNodes);
			}
		}

		return finalRestrictiveNodes;
	}

	/**
	 * The idea of this method is to attribute a score to each non parallelizable element,
	 * according to its duration and resource usage.
	 * Longest elements are more likely to be put in parallel.
	 * Highly consuming elements are less likely to be put in parallel.
	 *
	 * @param node
	 * @param resource
	 * @return
	 */
	private double computeScoreOf(final Node node,
							  	  final Resource resource)
	{
		final double duration;
		final double resourceUsage;

		if (this.loopFinder.nodeIsInLoop(node))
		{
			//Loop
			final Loop loop = this.loopFinder.findOuterLoopOf(node);

			if (!loop.entryPoint().equals(node))
			{
				throw new IllegalStateException();
			}

			final GraphToList graphToList = new GraphToList(loop);
			graphToList.convert();
			final Optimizer loopOptimizer = new Optimizer(graphToList.objectsList(), loop, this.globalResourceSet, -1);
			loopOptimizer.computeOptimalPoolForOneProcess();
			duration = ((((double) loopOptimizer.computeProcessExecutionTime()) / ((double) this.optimalProcessDuration)) * 100);
			resourceUsage = ((loopOptimizer.computeAverageUsageOf(resource) / ((double) this.realPool.getUsageOf(resource))) * 100);
		}
		else if (node.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			//Choice
			final Node mergeGateway = GraphUtils.findCorrespondingMergeGateway(node);
			final Graph copiedPortion = this.copiedGraph.weakCopy();
			final Node newInitialNode = copiedPortion.getNodeFromID(node.bpmnObject().id());
			newInitialNode.removeParents();
			copiedPortion.cutAt(mergeGateway);
			final Graph copiedChoice = new Graph(newInitialNode);
			final GraphToList graphToList = new GraphToList(copiedChoice);
			graphToList.convert();
			final Optimizer choiceOptimizer = new Optimizer(graphToList.objectsList(), copiedChoice, this.globalResourceSet, -1);
			choiceOptimizer.computeOptimalPoolForOneProcess();
			duration = ((((double) choiceOptimizer.computeProcessExecutionTime()) / ((double) this.optimalProcessDuration)) * 100);
			resourceUsage = ((choiceOptimizer.computeAverageUsageOf(resource) / ((double) this.realPool.getUsageOf(resource))) * 100);
		}
		else
		{
			//Task
			duration = ((((double) ((Task) node.bpmnObject()).duration()) / ((double) this.optimalProcessDuration)) * 100);
			resourceUsage = (((double) (((Task) node.bpmnObject()).resourceUsage().getUsageOf(resource)) / (double) (this.realPool.getUsageOf(resource))) * 100);
		}

		/*
			At this point, we have the duration of the object in units of time (UT),
			and the average percentage of usage of the resource over the execution time of the object.
			Then, we compute the corresponding score.
			The highest the resource usage, the highest the score.
			The highest the duration, the lowest the score.
			The resource usage is normalized by dividing it by the number of replicas available for this resource.
			The duration is normalized by dividing it by the duration of the process.
		 */

		return resourceUsage / duration;
	}

	private void findRestrictiveNodesOf(final String activity,
										final Node currentNode,
										final ArrayList<Node> currentRestrictiveNodes,
										final ArrayList<ArrayList<Node>> allRestrictiveNodes,
										final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			currentRestrictiveNodes.clear();
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == BpmnProcessType.END_EVENT)
		{
			currentRestrictiveNodes.clear();
		}

		final ArrayList<Pair<Node, ArrayList<Node>>> nextNodesAndRestrictiveNodes = new ArrayList<>();

		if (currentNode.bpmnObject().id().equals(activity))
		{
			//Current node is our activity
			return;
		}
		else if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			if (((Gateway) currentNode.bpmnObject()).isSplitGateway())
			{
				if (this.loopFinder.nodeIsInLoop(currentNode))
				{
					//Can be loop exit node or choice start node
					final Loop innerLoop = this.loopFinder.findInnerLoopOf(currentNode);

					if (innerLoop.exitPoint().equals(currentNode))
					{
						//Exit point of a loop: all paths exiting the loop lose one restrictive node (the loop)
						for (Node child : currentNode.childNodes())
						{
							final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);

							if (!innerLoop.hasNode(child))
							{
								//Child flow exits the loop --> current part loses a constraint
								newRestrictiveNodes.remove(newRestrictiveNodes.size() - 1);
							}

							nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
						}

					}
					else
					{
						//Beginning of choice inside the loop
						for (Node child : currentNode.childNodes())
						{
							final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);
							newRestrictiveNodes.add(child);
							nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
						}
					}

				}
				else
				{
					//Classical choice
					//currentRestrictiveNodes.add(currentNode);
					for (Node child : currentNode.childNodes())
					{
						final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);
						newRestrictiveNodes.add(child);
						nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
					}
				}

			}
			else
			{
				if (this.loopFinder.nodeIsInLoop(currentNode))
				{
					//Can be loop entry node or choice exit node
					final Loop innerLoop = this.loopFinder.findInnerLoopOf(currentNode);

					if (innerLoop.entryPoint().equals(currentNode))
					{
						//Loop entry node
						for (Node child : currentNode.childNodes())
						{
							final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);
							newRestrictiveNodes.add(currentNode);
							nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
						}
					}
					else
					{
						//Choice exit node
						for (Node child : currentNode.childNodes())
						{
							final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);
							newRestrictiveNodes.remove(newRestrictiveNodes.size() - 1);
							nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
						}
					}
				}
				else
				{
					//Classical choice
					for (Node child : currentNode.childNodes())
					{
						final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);
						newRestrictiveNodes.remove(newRestrictiveNodes.size() - 1);
						nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
					}
				}
			}
		}
		else
		{
			for (Node child : currentNode.childNodes())
			{
				nextNodesAndRestrictiveNodes.add(new Pair<>(child, new ArrayList<>(currentRestrictiveNodes)));
			}
		}

		for (int i = 0; i < nextNodesAndRestrictiveNodes.size(); i++)
		{
			final Pair<Node, ArrayList<Node>> nextNodeAndRestrictiveNode = nextNodesAndRestrictiveNodes.get(i);
			final ArrayList<Node> nextRestrictiveNodes;

			if (i == 0)
			{
				currentRestrictiveNodes.clear();
				currentRestrictiveNodes.addAll(nextNodeAndRestrictiveNode.second());
				nextRestrictiveNodes = currentRestrictiveNodes;
			}
			else
			{
				nextRestrictiveNodes = nextNodeAndRestrictiveNode.second();
				allRestrictiveNodes.add(nextRestrictiveNodes);
			}

			this.findRestrictiveNodesOf(activity, nextNodeAndRestrictiveNode.first(), nextRestrictiveNodes, allRestrictiveNodes, visitedNodes);
		}
	}

	private ArrayList<Node> computeProblematicTasks(final HashMap<Resource, ArrayList<Triple<Node, ArrayList<Node>, Double>>> nonParallelizableTasks)
	{
		final HashSet<Node> problematicTasks = new HashSet<>();
		final HashSet<Node> unparallelizedRestrictiveNodes = new HashSet<>();
		final HashSet<Resource> nonModifiableAnymoreResources = new HashSet<>();
		int i = 0;

		while (!this.problematicResources.isEmpty())
		{
			final Resource resource = this.problematicResources.get(0);
			final ArrayList<Triple<Node, ArrayList<Node>, Double>> candidates = nonParallelizableTasks.get(resource);

			if (candidates == null
				|| candidates.isEmpty())
			{
				nonModifiableAnymoreResources.add(resource);
				this.problematicResources.remove(resource);
			}
			else
			{
				final Triple<Node, ArrayList<Node>, Double> bestCandidate = this.electBestCandidate(candidates);
				problematicTasks.add(bestCandidate.first());

				for (Node restrictiveNode : bestCandidate.second())
				{
					if (restrictiveNode.bpmnObject() instanceof SequenceFlow)
					{
						problematicTasks.add(restrictiveNode.parentNodes().iterator().next());
					}
					else
					{
						problematicTasks.add(restrictiveNode);
					}
				}

				this.removeFromParallelism(bestCandidate, unparallelizedRestrictiveNodes);
				this.copiedGraph.clearParallelGatewaysTime();

				final GraphToList graphToList = new GraphToList(this.copiedGraph);
				graphToList.convert();
				final Optimizer optimizer = new Optimizer(graphToList.objectsList(), this.copiedGraph, this.globalResourceSet, this.iat);
				optimizer.computeOptimalPoolForNProcesses();
				this.problematicResources.clear();
				this.computeProblematicResources(optimizer);
				this.problematicResources.removeAll(nonModifiableAnymoreResources);
			}
		}

		return new ArrayList<>(problematicTasks);
	}

	private Triple<Node, ArrayList<Node>, Double> electBestCandidate(final ArrayList<Triple<Node, ArrayList<Node>, Double>> candidates)
	{
		Triple<Node, ArrayList<Node>, Double> bestCandidate = candidates.get(0);
		int index = 0;

		for (int i = 1; i < candidates.size(); i++)
		{
			final Triple<Node, ArrayList<Node>, Double> currentCandidate = candidates.get(i);

			if (currentCandidate.third() > bestCandidate.third())
			{
				bestCandidate = currentCandidate;
				index = i;
			}
		}

		candidates.remove(index);
		return bestCandidate;
	}

	private void removeFromParallelism(final Triple<Node, ArrayList<Node>, Double> bestCandidate,
									   final HashSet<Node> unparallelizedRestrictiveNodes)
	{
		if (bestCandidate.second().isEmpty())
		{
			//Candidate has no restrictive node
			final Node parallelGateway = this.findFurthestParallelSplit(bestCandidate.first(), null);
			this.moveBlockOutOfParallelGateway(parallelGateway, bestCandidate.first(), bestCandidate.first());
		}
		else
		{
			//Check whether some restrictive nodes of the current node have already been unparallelized.
			final Node lastUnparallelizedNode = this.findLastUnparallelizedRestrictiveNodes(unparallelizedRestrictiveNodes, bestCandidate.second());
			final ArrayList<Node> restrictiveNodesToManage = new ArrayList<>(bestCandidate.second());

			if (lastUnparallelizedNode != null)
			{
				for (Node node : bestCandidate.second())
				{
					restrictiveNodesToManage.remove(node);

					if (node.equals(lastUnparallelizedNode))
					{
						break;
					}
				}
			}

			if (restrictiveNodesToManage.isEmpty())
			{
				return;
			}

			//Remove the task from parallelism
			//Dumper.getInstance().dump(this.copiedGraph, "before");
			final Node parallelGateway = this.findFurthestParallelSplit(bestCandidate.first(), restrictiveNodesToManage.get(restrictiveNodesToManage.size() - 1));
			this.moveBlockOutOfParallelGateway(parallelGateway, bestCandidate.first(), bestCandidate.first());
			//Dumper.getInstance().dump(this.copiedGraph, "after");



			//Remove all the restricted restrictive nodes from parallelism
			Collections.reverse(restrictiveNodesToManage);

			for (int i = 0; i < restrictiveNodesToManage.size() - 1; i++)
			{
				final Node nodeToMove = restrictiveNodesToManage.get(i);
				final Node restrictiveNode = restrictiveNodesToManage.get(i + 1);
				final Node currentParallelGateway = this.findFurthestParallelSplit(nodeToMove, restrictiveNode);
				final Node correspondingMergeGateway;
				final Node correspondingSplitGateway;

				if (this.loopFinder.nodeIsInLoop(nodeToMove))
				{
					final Loop loop = this.loopFinder.findInnerLoopOf(nodeToMove);

					if (loop.entryPoint().equals(nodeToMove))
					{
						//Loop
						correspondingMergeGateway = this.copiedGraph.getNodeFromID(loop.exitPoint().bpmnObject().id());
						correspondingSplitGateway = nodeToMove;
					}
					else
					{
						//Choice
						correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(nodeToMove.parentNodes().iterator().next());
						correspondingSplitGateway = nodeToMove.parentNodes().iterator().next();
					}
				}
				else
				{
					//Choice
					correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(nodeToMove.parentNodes().iterator().next());
					correspondingSplitGateway = nodeToMove.parentNodes().iterator().next();
				}

				if (correspondingSplitGateway.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY
					|| correspondingMergeGateway.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY)
				{
					throw new IllegalStateException("Expects two exclusive gateways, got |" + nodeToMove.bpmnObject().type() + "| and |" + correspondingMergeGateway.bpmnObject().type() + "|.");
				}

				this.moveBlockOutOfParallelGateway(currentParallelGateway, correspondingSplitGateway, correspondingMergeGateway);
			}

			//Remove the last restrictive node (that is not restricted)
			final Node nodeToMove = restrictiveNodesToManage.get(restrictiveNodesToManage.size() - 1);
			final Node lastParallelGateway = this.findFurthestParallelSplit(nodeToMove, null);
			final Node correspondingMergeGateway;
			final Node correspondingSplitGateway;

			if (this.loopFinder.nodeIsInLoop(nodeToMove))
			{
				final Loop loop = this.loopFinder.findInnerLoopOf(nodeToMove);

				if (loop.entryPoint().equals(nodeToMove))
				{
					//Loop
					correspondingMergeGateway = this.copiedGraph.getNodeFromID(loop.exitPoint().bpmnObject().id());
					correspondingSplitGateway = nodeToMove;
				}
				else
				{
					//Choice
					correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(nodeToMove.parentNodes().iterator().next());
					correspondingSplitGateway = nodeToMove.parentNodes().iterator().next();
				}
			}
			else
			{
				//Choice
				correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(nodeToMove.parentNodes().iterator().next());
				correspondingSplitGateway = nodeToMove.parentNodes().iterator().next();
			}

			if (correspondingSplitGateway.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY
					|| correspondingMergeGateway.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY)
			{
				throw new IllegalStateException("Expects two exclusive gateways, got |" + nodeToMove.bpmnObject().type() + "| and |" + correspondingMergeGateway.bpmnObject().type() + "|.");
			}

			this.moveBlockOutOfParallelGateway(lastParallelGateway, correspondingSplitGateway, correspondingMergeGateway);
			unparallelizedRestrictiveNodes.addAll(bestCandidate.second());

			//Dumper.getInstance().dump(this.copiedGraph, "after2");


		}
	}

	private void moveBlockOutOfParallelGateway(final Node parallelGateway,
											   final Node firstNode,
											   final Node lastNode)
	{
		if (parallelGateway.equals(DUMMY_NODE))
		{
			return;
		}

		final boolean isLoop;

		if (this.loopFinder.nodeIsInLoop(firstNode))
		{
			final Loop loop = this.loopFinder.findInnerLoopOf(firstNode);
			isLoop = loop.entryPoint().equals(firstNode);
		}
		else
		{
			isLoop = false;
		}

		final Node correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(parallelGateway);

		if (parallelGateway.childNodes().size() > 2)
		{
			//Gateway will remain
			final Node parentFlow = isLoop ? this.findOutOfLoopEntryFlow(firstNode) : firstNode.parentNodes().iterator().next();
			final Node childFlow = isLoop ? this.findOutOfLoopExitFlow(lastNode) : lastNode.childNodes().iterator().next();
			final Node childFlowChild = childFlow.childNodes().iterator().next();
			parentFlow.removeChildren(firstNode);
			childFlowChild.removeParent(childFlow);
			parentFlow.addChild(childFlowChild);
			childFlowChild.addParent(parentFlow);

			if (isLoop)
			{
				firstNode.removeParent(this.findOutOfLoopEntryFlow(firstNode));
				lastNode.removeChildren(this.findOutOfLoopExitFlow(lastNode));
			}
			else
			{
				firstNode.removeParents();
				lastNode.removeChildren();
			}

			if (parentFlow.hasParent(parallelGateway)
				&& parentFlow.hasChild(correspondingMergeGateway))
			{
				//If we removed the entire path, remove the flow
				parallelGateway.removeChildren(parentFlow);
				correspondingMergeGateway.removeParent(parentFlow);
			}

			final Node parallelGatewayParentFlow = parallelGateway.parentNodes().iterator().next();
			parallelGatewayParentFlow.removeChildren();
			parallelGateway.removeParents();
			parallelGatewayParentFlow.addChild(firstNode);
			firstNode.addParent(parallelGatewayParentFlow);
			final Node newSeqFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), parallelGateway.bpmnObject().id()));
			lastNode.addChild(newSeqFlow);
			newSeqFlow.addParent(lastNode);
			newSeqFlow.addChild(parallelGateway);
			parallelGateway.addParent(newSeqFlow);
		}
		else
		{
			final Node firstRealNodeBefore = (isLoop ? this.findOutOfLoopEntryFlow(firstNode) : firstNode.parentNodes().iterator().next()).parentNodes().iterator().next();
			final Node firstRealNodeAfter = (isLoop ? this.findOutOfLoopExitFlow(lastNode) : lastNode.childNodes().iterator().next()).childNodes().iterator().next();

			if (firstRealNodeBefore.equals(parallelGateway)
				&& firstRealNodeAfter.equals(correspondingMergeGateway))
			{
				//Gateway has to be removed
				parallelGateway.removeChildren(isLoop ? this.findOutOfLoopEntryFlow(firstNode) : firstNode.parentNodes().iterator().next());
				correspondingMergeGateway.removeParent(isLoop ? this.findOutOfLoopExitFlow(lastNode) : lastNode.childNodes().iterator().next());
				final Node initialNodeToPreserve = parallelGateway.childNodes().iterator().next().childNodes().iterator().next();
				final Node lastNodeToPreserve = correspondingMergeGateway.parentNodes().iterator().next().parentNodes().iterator().next();

				final boolean preserveInLoop;

				if (this.loopFinder.nodeIsInLoop(initialNodeToPreserve))
				{
					final Loop loop = this.loopFinder.findInnerLoopOf(initialNodeToPreserve);
					preserveInLoop = loop.entryPoint().equals(initialNodeToPreserve);
				}
				else
				{
					preserveInLoop = false;
				}

				if (preserveInLoop)
				{
					initialNodeToPreserve.removeParent(this.findOutOfLoopEntryFlow(initialNodeToPreserve));
					lastNodeToPreserve.removeChildren(this.findOutOfLoopExitFlow(lastNodeToPreserve));
				}
				else
				{
					initialNodeToPreserve.removeParents();
					lastNodeToPreserve.removeChildren();
				}

				if (isLoop)
				{
					firstNode.removeParent(this.findOutOfLoopEntryFlow(firstNode));
					lastNode.removeParent(this.findOutOfLoopExitFlow(lastNode));
				}
				else
				{
					firstNode.removeParents();
					lastNode.removeChildren();
				}

				final Node splitParentFlow = parallelGateway.parentNodes().iterator().next();
				final Node mergeChildFlow = correspondingMergeGateway.childNodes().iterator().next();
				splitParentFlow.removeChildren();
				mergeChildFlow.removeParents();
				splitParentFlow.addChild(firstNode);
				firstNode.addParent(splitParentFlow);
				final Node newSeqFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), initialNodeToPreserve.bpmnObject().id()));
				lastNode.addChild(newSeqFlow);
				newSeqFlow.addParent(lastNode);
				newSeqFlow.addChild(initialNodeToPreserve);
				initialNodeToPreserve.addParent(newSeqFlow);
				lastNodeToPreserve.addChild(mergeChildFlow);
				mergeChildFlow.addParent(lastNodeToPreserve);
			}
			else
			{
				//Gateway will remain
				final Node parentFlow = isLoop ? this.findOutOfLoopEntryFlow(firstNode) : firstNode.parentNodes().iterator().next();
				final Node childFlow = isLoop ? this.findOutOfLoopExitFlow(lastNode) : lastNode.childNodes().iterator().next();
				final Node childFlowChild = childFlow.childNodes().iterator().next();
				parentFlow.removeChildren(firstNode);
				childFlowChild.removeParent(childFlow);
				parentFlow.addChild(childFlowChild);
				childFlowChild.addParent(parentFlow);

				if (isLoop)
				{
					firstNode.removeParent(this.findOutOfLoopEntryFlow(firstNode));
					lastNode.removeChildren(this.findOutOfLoopExitFlow(lastNode));
				}
				else
				{
					lastNode.removeChildren();
					firstNode.removeParents();
				}

				final Node parallelGatewayParentFlow = parallelGateway.parentNodes().iterator().next();
				parallelGatewayParentFlow.removeChildren();
				parallelGateway.removeParents();
				parallelGatewayParentFlow.addChild(firstNode);
				firstNode.addParent(parallelGatewayParentFlow);
				final Node newSeqFlow = new Node(BpmnProcessFactory.generateSequenceFlow(lastNode.bpmnObject().id(), parallelGateway.bpmnObject().id()));
				lastNode.addChild(newSeqFlow);
				newSeqFlow.addParent(lastNode);
				newSeqFlow.addChild(parallelGateway);
				parallelGateway.addParent(newSeqFlow);
			}
		}
	}

	private Node findOutOfLoopEntryFlow(final Node node)
	{
		final Loop loop = this.loopFinder.findInnerLoopOf(node);

		for (Node parentFlow : node.parentNodes())
		{
			if (!loop.hasNode(parentFlow))
			{
				return parentFlow;
			}
		}

		throw new IllegalStateException();
	}

	private Node findOutOfLoopExitFlow(final Node node)
	{
		final Loop loop = this.loopFinder.findInnerLoopOf(node);

		for (Node childFlow : node.childNodes())
		{
			if (!loop.hasNode(childFlow))
			{
				return childFlow;
			}
		}

		throw new IllegalStateException();
	}

/*	private Node findInLoopEntryFlow(final Node node)
	{
		final Loop loop = this.loopFinder.findInnerLoopOf(node);

		for (Node parentFlow : node.parentNodes())
		{
			if (loop.hasNode(parentFlow))
			{
				return parentFlow;
			}
		}

		throw new IllegalStateException();
	}

	private Node findInOfLoopExitFlow(final Node node)
	{
		final Loop loop = this.loopFinder.findInnerLoopOf(node);

		for (Node childFlow : node.childNodes())
		{
			if (loop.hasNode(childFlow))
			{
				return childFlow;
			}
		}

		throw new IllegalStateException();
	}*/

	private Node findLastUnparallelizedRestrictiveNodes(final HashSet<Node> unparallelizedNodes,
														final ArrayList<Node> restrictiveNodes)
	{
		Node lastRestrictiveNode = null;

		for (Node restrictiveNode : restrictiveNodes)
		{
			if (unparallelizedNodes.contains(restrictiveNode))
			{
				lastRestrictiveNode = restrictiveNode;
			}
			else
			{
				break;
			}
		}

		return lastRestrictiveNode;
	}

	private Node findFurthestParallelSplit(final Node currentNode,
										   final Node closestRestrictiveNode)
	{
		final Node parallelSplit;

		if (closestRestrictiveNode == null)
		{
			parallelSplit = this.findFurthestEnclosingParallelGatewayV2(currentNode, new ArrayList<>(), new HashSet<>(), null);
		}
		else
		{
			if (this.loopFinder.nodeIsInLoop(closestRestrictiveNode))
			{
				final Loop loop = this.loopFinder.findInnerLoopOf(closestRestrictiveNode);

				if (loop.entryPoint().equals(closestRestrictiveNode))
				{
					//Loop
					parallelSplit = this.findFurthestEnclosingParallelGatewayV2(currentNode, new ArrayList<>(), new HashSet<>(), loop.entryPoint());
				}
				else
				{
					//Choice
					parallelSplit = this.findFurthestEnclosingParallelGatewayV2(currentNode, new ArrayList<>(), new HashSet<>(), closestRestrictiveNode);
				}
			}
			else
			{
				//Choice
				parallelSplit = this.findFurthestEnclosingParallelGatewayV2(currentNode, new ArrayList<>(), new HashSet<>(), closestRestrictiveNode);
			}
		}

		if (parallelSplit == null)
		{
			throw new IllegalStateException();
		}

		return parallelSplit;
	}

	private Node findFurthestEnclosingParallelGateway(final Node node,
													  final Node currentNode,
													  final ArrayList<Node> enclosingParallelGateways,
													  final HashSet<Node> visitedNodes,
													  final Node bound)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(bound))
		{
			return null;
		}

		if (node.equals(currentNode))
		{
			if (enclosingParallelGateways.isEmpty())
			{
				return DUMMY_NODE;
			}
			else
			{
				return enclosingParallelGateways.get(0);
			}
		}

		if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
		{
			if (((Gateway) currentNode.bpmnObject()).isSplitGateway())
			{
				enclosingParallelGateways.add(currentNode);
			}
			else
			{
				enclosingParallelGateways.remove(enclosingParallelGateways.size() - 1);
			}
		}
		else if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			return null;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node parallelGateway = this.findFurthestEnclosingParallelGateway(node, child, enclosingParallelGateways, visitedNodes, bound);

			if (parallelGateway != null)
			{
				return parallelGateway;
			}
		}

		return null;
	}

	private Node findFurthestEnclosingParallelGatewayV2(final Node currentNode,
													  	final ArrayList<Node> enclosingParallelGateways,
													  	final HashSet<Node> visitedNodes,
													  	final Node bound)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(bound)
			|| currentNode.bpmnObject().type() == BpmnProcessType.START_EVENT)
		{
			if (enclosingParallelGateways.isEmpty())
			{
				return DUMMY_NODE;
			}
			else
			{
				return enclosingParallelGateways.get(enclosingParallelGateways.size() - 1);
			}
		}

		final ArrayList<Node> nextNodes = new ArrayList<>();

		if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
		{
			if (((Gateway) currentNode.bpmnObject()).isSplitGateway())
			{
				enclosingParallelGateways.add(currentNode);
				nextNodes.addAll(currentNode.parentNodes());
			}
			else
			{
				final Node correspondingSplit = GraphUtils.findCorrespondingSplitGateway(currentNode);
				nextNodes.addAll(correspondingSplit.parentNodes());
			}
		}
		else
		{
			nextNodes.addAll(currentNode.parentNodes());
		}

		for (Node nextNode : nextNodes)
		{
			final Node parallelGateway = this.findFurthestEnclosingParallelGatewayV2(nextNode, enclosingParallelGateways, visitedNodes, bound);

			if (parallelGateway != null)
			{
				return parallelGateway;
			}
		}

		return null;
	}
}
