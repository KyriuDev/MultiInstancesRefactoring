package refactoring;

import bpmn.types.process.Task;
import other.Utils;
import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.Node;
import refactoring.dependencies.*;
import refactoring.partial_order_to_bpmn.AbstractGraph;
import refactoring.partial_order_to_bpmn.AbstractNode;
import refactoring.partial_order_to_bpmn.BPMNGenerator;
import refactoring.partial_order_to_bpmn.BPMNUtils;
import resources.LightOptimizer;
import other.Pair;

import java.util.*;

public class TasksBalancer
{
	private static final Heuristic HEURISTIC = Heuristic.COMBINATION;
	private final Cluster mainCluster;
	private final ArrayList<Node> problematicNodes;
	private Graph finalGraph;

	public TasksBalancer(final Cluster mainCluster,
						 final ArrayList<Node> problematicNodes)
	{
		this.mainCluster = mainCluster;
		this.problematicNodes = problematicNodes;
	}

	public Graph balanceTasks()
	{
		this.computeSubgraphsTasks(this.mainCluster);
		this.setAlreadyCorrectNodesPerCluster(this.mainCluster);

		final HashSet<Cluster> clusters = new HashSet<>();
		this.findAllClusters(this.mainCluster, clusters);

		if (HEURISTIC == Heuristic.COMBINATION)
		{
			/*
			 * For the combination, we can generate the combinations for any cluster
			 * without any issue, as we only manipulate nodes as entities to combine
			 * with others.
			 * Once all combinations are done, the clusters are processed from the most
			 * nested ones to the less nested ones.
			 */
			final HashMap<Cluster, Boolean> nonProblematicClusters = new HashMap<>();

			for (Cluster cluster : clusters)
			{
				nonProblematicClusters.put(cluster, this.generateAbstractGraphsCombinations(cluster));
			}

			ArrayList<Cluster> readyToGenerateClusters = this.findReadyToGenerateClusters(clusters);

			while (!readyToGenerateClusters.isEmpty())
			{
				for (Cluster cluster : readyToGenerateClusters)
				{
					this.generateOptimalGraphForCluster(cluster, nonProblematicClusters);
				}

				readyToGenerateClusters.clear();
				readyToGenerateClusters = this.findReadyToGenerateClusters(clusters);
			}
		}
		else
		{
			/*
				For the closest heuristic, we need to compute information from
				the most nested clusters to the less nested ones directly.
				Otherwise, we may lack some information regarding a cluster while
				processing another one.
			 */
			final HashSet<Cluster> generatedClusters = new HashSet<>();
			ArrayList<Cluster> readyToGenerateClusters = this.findGenerableClusters(clusters, generatedClusters);

			while (!readyToGenerateClusters.isEmpty())
			{
				for (Cluster cluster : readyToGenerateClusters)
				{
					final boolean abstractGraphsCombined = this.generateAbstractGraphsCombinations(cluster);

					if (cluster.possiblyOptimalBalancedGraphs().isEmpty())
					{
						throw new IllegalStateException();
					}

					final AbstractGraph bestGraph;
					final EnhancedGraph bestBPMN;

					if (cluster.possiblyOptimalBalancedGraphs().size() > 1)
					{
						Iterator<AbstractGraph> iterator = cluster.possiblyOptimalBalancedGraphs().iterator();
						AbstractGraph optimalGraph = iterator.next();
						EnhancedGraph optimalBPMN = BPMNUtils.generateEntireBPMNFromAbstractGraph(optimalGraph, cluster);
						final GraphToList graphToList = new GraphToList(optimalBPMN);
						graphToList.convert();
						int optimalDuration = new LightOptimizer(graphToList.objectsList(), optimalBPMN).computeProcessExecutionTime();

						while (iterator.hasNext())
						{
							final AbstractGraph currentGraph = iterator.next();
							final EnhancedGraph currentBPMN = BPMNUtils.generateEntireBPMNFromAbstractGraph(currentGraph, cluster);
							final GraphToList currentGraphToList = new GraphToList(currentBPMN);
							currentGraphToList.convert();
							final int duration = new LightOptimizer(currentGraphToList.objectsList(), currentBPMN).computeProcessExecutionTime();

							if (duration < optimalDuration)
							{
								optimalDuration = duration;
								optimalGraph = currentGraph;
								optimalBPMN = currentBPMN;
							}
						}

						bestGraph = optimalGraph;
						bestBPMN = optimalBPMN;
					}
					else
					{
						bestGraph = cluster.possiblyOptimalBalancedGraphs().iterator().next();
						bestBPMN = BPMNUtils.generateEntireBPMNFromAbstractGraph(bestGraph, cluster);
					}

					cluster.abstractGraphs().clear();
					cluster.addAbstractGraph(bestGraph);
					cluster.setBpmnGraph(bestBPMN);
					cluster.possiblyOptimalBalancedGraphs().clear();

					if (abstractGraphsCombined)
					{
						//Put all enhanced nodes in dependencies so that none is put in parallel during the final generation process
						for (EnhancedNode enhancedNode : cluster.elements())
						{
							final HashSet<Dependency> fakeDependencies = cluster.graphsWithDependencies().computeIfAbsent(new DependencyGraph(), h -> new HashSet<>());
							fakeDependencies.add(new Dependency(enhancedNode.node(), enhancedNode.node()));
						}
					}
				}

				generatedClusters.addAll(readyToGenerateClusters);
				readyToGenerateClusters.clear();
				this.findGenerableClusters(clusters, generatedClusters);
			}
		}

		final BPMNGenerator bpmnGenerator = new BPMNGenerator(this.mainCluster);
		return this.finalGraph = bpmnGenerator.generate();
	}

	public Graph balancedGraph()
	{
		return this.finalGraph;
	}

	//Private methods

	private ArrayList<Cluster> findGenerableClusters(final HashSet<Cluster> clusters,
													 final HashSet<Cluster> generatedClusters)
	{
		final ArrayList<Cluster> generableClusters = new ArrayList<>();

		for (Cluster currentCluster : clusters)
		{
			if (!generatedClusters.contains(currentCluster))
			{
				boolean generable = true;

				for (EnhancedNode enhancedNode : currentCluster.elements())
				{
					if (enhancedNode.type() == EnhancedType.CHOICE)
					{
						final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

						for (Cluster cluster : enhancedChoice.clusters())
						{
							if (!generatedClusters.contains(cluster))
							{
								generable = false;
								break;
							}
						}
					}
					else if (enhancedNode.type() == EnhancedType.LOOP)
					{
						final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

						if (!generatedClusters.contains(enhancedLoop.entryToExitCluster()))
						{
							generable = false;
							break;
						}

						if (!generatedClusters.contains(enhancedLoop.exitToEntryCluster()))
						{
							generable = false;
							break;
						}
					}

					if (!generable)
					{
						break;
					}
				}

				if (generable)
				{
					generableClusters.add(currentCluster);
				}
			}
		}

		return generableClusters;
	}

	private void generateOptimalGraphForCluster(Cluster cluster,
												final HashMap<Cluster, Boolean> clusterProblematicity)
	{
		cluster.hashElements();

		AbstractGraph optimalAbstractGraph = cluster.possiblyOptimalBalancedGraphs().iterator().next();
		EnhancedGraph optimalGraph = BPMNUtils.generateEntireBPMNFromAbstractGraph(optimalAbstractGraph, cluster);

		if (optimalGraph == null)
		{
			cluster.abstractGraphs().clear();
			cluster.possiblyOptimalBalancedGraphs().clear();
			return;
		}

		final GraphToList graphToList = new GraphToList(optimalGraph);
		graphToList.convert();
		int optimalExecTime = new LightOptimizer(graphToList.objectsList(), optimalGraph).computeProcessExecutionTime();
		final Iterator<AbstractGraph> iterator = cluster.possiblyOptimalBalancedGraphs().iterator();
		iterator.next();

		while (iterator.hasNext())
		{
			final AbstractGraph currentGraph = iterator.next();
			final EnhancedGraph currentGeneratedGraph = BPMNUtils.generateEntireBPMNFromAbstractGraph(currentGraph, cluster);
			final GraphToList currentGraphToList = new GraphToList(currentGeneratedGraph);
			currentGraphToList.convert();
			final int currentExecTime = new LightOptimizer(currentGraphToList.objectsList(), currentGeneratedGraph).computeProcessExecutionTime();

			if (currentExecTime < optimalExecTime)
			{
				optimalExecTime = currentExecTime;
				optimalAbstractGraph = currentGraph;
				optimalGraph = currentGeneratedGraph;
			}
		}

		final EnhancedGraph finalGraph = BPMNUtils.generateBPMNFromAbstractGraph(optimalAbstractGraph, cluster);

		if (finalGraph == null)
		{
			cluster.abstractGraphs().clear();
			cluster.possiblyOptimalBalancedGraphs().clear();
			return;
		}

		cluster.abstractGraphs().clear();
		cluster.addAbstractGraph(optimalAbstractGraph);
		cluster.setBpmnGraph(finalGraph);
		cluster.possiblyOptimalBalancedGraphs().clear();

		if (clusterProblematicity.get(cluster))
		{
			//Put all enhanced nodes in dependencies so that none is put in parallel during the final generation process
			for (EnhancedNode enhancedNode : cluster.elements())
			{
				final HashSet<Dependency> fakeDependencies = cluster.graphsWithDependencies().computeIfAbsent(new DependencyGraph(), h -> new HashSet<>());
				fakeDependencies.add(new Dependency(enhancedNode.node(), enhancedNode.node()));
			}
		}
	}

	private ArrayList<Cluster> findReadyToGenerateClusters(final HashSet<Cluster> clusters)
	{
		//A cluster is ready to be generated when all its subclusters have already been generated
		final ArrayList<Cluster> readyToGenerateCluster = new ArrayList<>();

		for (Cluster cluster : clusters)
		{
			if (!cluster.hasBeenCorrected())
			{
				boolean clusterReady = true;

				for (EnhancedNode enhancedNode : cluster.elements())
				{
					if (enhancedNode.type() == EnhancedType.CHOICE)
					{
						final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

						for (Cluster subCluster : enhancedChoice.clusters())
						{
							if (!subCluster.hasBeenCorrected())
							{
								clusterReady = false;
								break;
							}
						}
					}
					else if (enhancedNode.type() == EnhancedType.LOOP)
					{
						final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

						if (!enhancedLoop.entryToExitCluster().hasBeenCorrected()
							|| !enhancedLoop.exitToEntryCluster().hasBeenCorrected())
						{
							clusterReady = false;
						}
					}

					if (!clusterReady)
					{
						break;
					}
				}

				if (clusterReady)
				{
					readyToGenerateCluster.add(cluster);
				}
			}
		}

		return readyToGenerateCluster;
	}

	private void findAllClusters(final Cluster cluster,
								 final HashSet<Cluster> allClusters)
	{
		allClusters.add(cluster);

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.findAllClusters(choiceCluster, allClusters);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				this.findAllClusters(enhancedLoop.entryToExitCluster(), allClusters);
				this.findAllClusters(enhancedLoop.exitToEntryCluster(), allClusters);
			}
		}
	}

	private boolean generateAbstractGraphsCombinations(final Cluster cluster)
	{
		final HashMap<AbstractGraph, HashSet<AbstractGraph>> currentClusterDependencyGraphsMap = new HashMap<>();
		ArrayList<Pair<AbstractNode, AbstractNode>> abstractNodesToCorrect = new ArrayList<>();
		this.findAbstractNodesToCorrect(cluster, abstractNodesToCorrect);

		final HashSet<Node> nonDependentNodes = this.computeAllNonDependentNodes(cluster);
		final HashSet<Node> nonDependentNonProblematicNodes = new HashSet<>();
		final HashSet<Node> nonDependentProblematicNodes = new HashSet<>();

		for (Node node : nonDependentNodes)
		{
			boolean nodeIsProblematic = false;

			for (Node problematicNode : this.problematicNodes)
			{
				if (node.equals(problematicNode))
				{
					nodeIsProblematic = true;
					break;
				}
			}

			if (nodeIsProblematic)
			{
				nonDependentProblematicNodes.add(node);
			}
			else
			{
				nonDependentNonProblematicNodes.add(node);
			}
		}

		if (abstractNodesToCorrect.isEmpty()
				&& clusterIsValid(cluster))
		{
			//The current cluster is already correct
			final AbstractNode abstractNode = new AbstractNode();
			final AbstractGraph abstractGraph = new AbstractGraph(abstractNode);

			if (cluster.abstractGraphs().size() == 1)
			{
				abstractNode.addSubgraph(cluster.abstractGraphs().get(0));
			}
			else if (cluster.abstractGraphs().size() > 1)
			{
				for (AbstractGraph clusterGraphs : cluster.abstractGraphs())
				{
					abstractNode.addSubgraph(clusterGraphs);
				}
			}

			for (Node node : nonDependentNodes)
			{
				abstractNode.addNode(node);
			}

			cluster.addPossiblyOptimalBalancedAbstractGraph(abstractGraph);
		}
		else
		{
			if (cluster.abstractGraphs().isEmpty())
			{
				//Add all non-dependent non-parallelizable nodes one after the other
				final AbstractNode firstNode = new AbstractNode();
				final AbstractGraph abstractGraph = new AbstractGraph(firstNode);
				AbstractNode currentNode = firstNode;
				currentNode.addNode(nonDependentProblematicNodes.iterator().next());
				final Iterator<Node> iterator = nonDependentProblematicNodes.iterator();
				iterator.next();

				while (iterator.hasNext())
				{
					Node node = iterator.next();
					final AbstractNode newNode = new AbstractNode();
					newNode.addNode(node);
					currentNode.addSuccessor(newNode);
					currentNode = newNode;
				}

				//Add all non-dependent parallelizable nodes at the end of the graph
				if (!nonDependentNonProblematicNodes.isEmpty())
				{
					final AbstractNode abstractNode = new AbstractNode();
					currentNode.addSuccessor(abstractNode);

					for (Node node : nonDependentNonProblematicNodes)
					{
						abstractNode.addNode(node);
					}
				}

				//Add all possibilities to the current cluster
				cluster.addPossiblyOptimalBalancedAbstractGraph(abstractGraph);
			}
			else
			{
				//The current cluster needs to be corrected
				while (!abstractNodesToCorrect.isEmpty())
				{
					this.correctAbstractNodes(abstractNodesToCorrect, currentClusterDependencyGraphsMap, cluster);
					abstractNodesToCorrect.clear();

					for (AbstractGraph referenceGraph : currentClusterDependencyGraphsMap.keySet())
					{
						this.findAbstractNodesToCorrect(currentClusterDependencyGraphsMap.get(referenceGraph), abstractNodesToCorrect);
					}
				}

				/*
					Normally, after this step, currentClusterDependencyGraphsMap contains all possibly optimal
					abstract graphs for each initial abstract graph of the main cluster
					The last step involving the dependency graphs is to combine the possible versions
					of the initial graphs.
				 */

				final HashSet<AbstractGraph> finalCombinations = this.finalizeAbstractGraphCombinations(currentClusterDependencyGraphsMap, cluster);

				//To conclude, we need to merge all the nodes not involved in dependencies with the dependency graphs
				//Remove all nodes belonging to dependencies
				final HashSet<AbstractGraph> fullyCombinedGraphs = new HashSet<>();

				if (!nonDependentProblematicNodes.isEmpty())
				{
					//Some non-dependent nodes cannot be put in parallel --> add them to each graph
					for (AbstractGraph abstractGraph : finalCombinations)
					{
						final AbstractNode firstNonParallelizableNode = this.findFirstNonParallelizableNode(abstractGraph);
						final AbstractNode firstNode = new AbstractNode();
						AbstractNode currentNode = firstNode;
						currentNode.addNode(nonDependentProblematicNodes.iterator().next());
						final Iterator<Node> iterator = nonDependentProblematicNodes.iterator();
						iterator.next();

						while (iterator.hasNext())
						{
							Node node = iterator.next();
							final AbstractNode newNode = new AbstractNode();
							newNode.addNode(node);
							currentNode.addSuccessor(newNode);
							currentNode = newNode;
						}

						if (firstNonParallelizableNode == null)
						{
							//All nodes are parallelizable in the current graph -> put the non parallelizable nodes at its beginning
							currentNode.addSuccessor(abstractGraph.startNode());
							abstractGraph.setStartNode(firstNode);
						}
						else
						{
							//There is (at least) one non parallelizable node -> link the non parallelizable nodes to it
							if (firstNonParallelizableNode.successors().isEmpty())
							{
								//The first non parallelizable node is the last one
								firstNonParallelizableNode.addSuccessor(firstNonParallelizableNode);
							}
							else
							{
								//The first non parallelizable node is a middle node
								final AbstractNode successor = firstNonParallelizableNode.successors().get(0);
								firstNonParallelizableNode.successors().clear();
								firstNonParallelizableNode.addSuccessor(firstNode);
								currentNode.addSuccessor(successor);
							}
						}
					}
				}

				//Add all non-dependent parallelizable nodes to each graph
				for (AbstractGraph abstractGraph : finalCombinations)
				{
					final ArrayList<Pair<AbstractNode, AbstractNode>> combinations = this.mergeFreeNodesAndSubGraph(nonDependentNonProblematicNodes, abstractGraph, cluster);

					for (Pair<AbstractNode, AbstractNode> combination : combinations)
					{
						final AbstractGraph generatedAbstractGraph = new AbstractGraph();
						generatedAbstractGraph.setStartNode(combination.first());
						fullyCombinedGraphs.add(generatedAbstractGraph);
					}
				}

				//Add all possibilities to the current cluster
				cluster.addAllPossiblyOptimalBalancedAbstractGraphs(fullyCombinedGraphs);
			}
		}

		return true;
	}

	private boolean clusterIsValid(final Cluster cluster)
	{
		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (this.problematicNodes.contains(enhancedNode.node()))
			{
				return false;
			}
		}

		return true;
	}

	private AbstractNode findFirstNonParallelizableNode(final AbstractGraph abstractGraph)
	{
		final ArrayList<AbstractNode> abstractNodes = abstractGraph.extractNodes();

		for (AbstractNode abstractNode : abstractNodes)
		{
			if (abstractNode.isProblematic())
			{
				return abstractNode;
			}
		}

		return null;
	}

	private HashSet<Node> computeAllNonDependentNodes(final Cluster cluster)
	{
		final HashSet<EnhancedNode> nonDependentEnhancedNodes = new HashSet<>(cluster.elements());

		for (Iterator<EnhancedNode> iterator = nonDependentEnhancedNodes.iterator(); iterator.hasNext(); )
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

		final HashSet<Node> nonDependentNodes = new HashSet<>();

		for (EnhancedNode enhancedNode : nonDependentEnhancedNodes)
		{
			nonDependentNodes.add(enhancedNode.node());
		}

		return nonDependentNodes;
	}

	private HashSet<AbstractGraph> finalizeAbstractGraphCombinations(final HashMap<AbstractGraph, HashSet<AbstractGraph>> mainClusterDependencyGraphsMap,
																	 final Cluster cluster)
	{
		final Set<AbstractGraph> keySet = mainClusterDependencyGraphsMap.keySet();
		final HashSet<AbstractGraph> finalCombinations = mainClusterDependencyGraphsMap.get(keySet.iterator().next());

		if (keySet.size() > 1)
		{
			final Iterator<AbstractGraph> iterator = mainClusterDependencyGraphsMap.keySet().iterator();
			iterator.next();

			while (iterator.hasNext())
			{
				final AbstractGraph key = iterator.next();
				final ArrayList<AbstractGraph> currentGraphs = new ArrayList<>(mainClusterDependencyGraphsMap.get(key));
				final ArrayList<AbstractGraph> currentCombination = new ArrayList<>();

				for (AbstractGraph abstractGraph : finalCombinations)
				{
					currentCombination.addAll(this.mergeGraphsWithGraph(currentGraphs, abstractGraph, cluster));
				}

				finalCombinations.clear();
				finalCombinations.addAll(currentCombination);
			}
		}

		return finalCombinations;
	}

	private void correctAbstractNodes(final ArrayList<Pair<AbstractNode, AbstractNode>> incorrectNodes,
									  final HashMap<AbstractGraph, HashSet<AbstractGraph>> dependencyGraphsMap,
									  final Cluster cluster)
	{
		for (Pair<AbstractNode, AbstractNode> incorrectNode : incorrectNodes)
		{
			//If the main cluster contains several dependency graphs, map the current modifications
			//with the good one, and get its corresponding set of combinations graphs
			final AbstractGraph originalReferenceGraph = this.findReferenceGraph(incorrectNode.second(), cluster);
			final HashSet<AbstractGraph> generatedGraphs;

			if (dependencyGraphsMap.containsKey(originalReferenceGraph))
			{
				generatedGraphs = dependencyGraphsMap.get(originalReferenceGraph);
			}
			else
			{
				generatedGraphs = new HashSet<>();
				generatedGraphs.add(originalReferenceGraph);
				dependencyGraphsMap.put(originalReferenceGraph, generatedGraphs);
			}

			//List of possibly optimal graphs after node correction
			final HashSet<AbstractGraph> possibleOptimalVersionsOfReferenceGraph = new HashSet<>();

			//Add to each already generated graph the correction of the current node (incorrectNode.third())
			for (AbstractGraph referenceGraph : generatedGraphs)
			{
				final HashSet<AbstractGraph> possiblyOptimalVersionsOfCurrentNode = new HashSet<>();

				//Compute parallelizable and non parallelizable nodes of current abstract node
				final AbstractNode abstractNodeToProcess = incorrectNode.second();
				final HashSet<Node> nonParallelizableFreeNodes = new HashSet<>();
				final HashSet<Node> parallelizableFreeNodes = new HashSet<>(abstractNodeToProcess.listNodes());

				for (Node node : abstractNodeToProcess.listNodes())
				{
					for (Node problematicNode : this.problematicNodes)
					{
						if (node.equals(problematicNode))
						{
							nonParallelizableFreeNodes.add(node);
						}
					}
				}

				parallelizableFreeNodes.removeAll(nonParallelizableFreeNodes);

				//Check whether subgraphs of current node contain non parallelizable elements or not
				final AbstractGraph tempPivot = this.electPivot(abstractNodeToProcess);
				final AbstractGraph pivotGraph;

				if (tempPivot == null)
				{
					//All the subgraphs do not contain any non-parallelizable task
					pivotGraph = abstractNodeToProcess.subGraphs().isEmpty() ? null : abstractNodeToProcess.subGraphs().iterator().next();
				}
				else
				{
					pivotGraph = tempPivot;
				}

				if (pivotGraph == null)
				{
					//The current node has no subgraph -> put all non parallelizable nodes one after the other
					//and all the parallelizable nodes together
					AbstractNode currentNode = new AbstractNode();
					currentNode.setProblematic();
					currentNode.setCorrected();
					AbstractNode startNode = currentNode;
					final Iterator<Node> iterator = nonParallelizableFreeNodes.iterator();
					Node nonParallelizableNode = iterator.next();
					currentNode.addNode(nonParallelizableNode);

					while (iterator.hasNext())
					{
						nonParallelizableNode = iterator.next();
						final AbstractNode nextNode = new AbstractNode();
						nextNode.setProblematic();
						nextNode.setCorrected();
						nextNode.addNode(nonParallelizableNode);
						currentNode.addSuccessor(nextNode);
						currentNode = nextNode;
					}

					if (!parallelizableFreeNodes.isEmpty())
					{
						final ArrayList<AbstractGraph> possibleAbstractGraphs = this.generateAbstractGraphsFromParallelizableNodes(parallelizableFreeNodes);
						final AbstractGraph tempGraph = new AbstractGraph();
						tempGraph.setStartNode(startNode);

						for (AbstractGraph possibleAbstractGraph : possibleAbstractGraphs)
						{
							final AbstractGraph copiedGraph = tempGraph.copy();
							final AbstractNode lastNode = copiedGraph.lastNode();
							lastNode.addSuccessor(possibleAbstractGraph.startNode());
							possiblyOptimalVersionsOfCurrentNode.add(copiedGraph);
						}
					}
					else
					{
						final AbstractGraph abstractGraph = new AbstractGraph();
						abstractGraph.setStartNode(startNode);
						possiblyOptimalVersionsOfCurrentNode.add(abstractGraph);
					}
				}
				else
				{
					final AbstractGraph copiedPivot = pivotGraph.copy();

					if (tempPivot == null)
					{
						/*
							There is no subgraph containing non parallelizable tasks. We need to:
								- Add all non parallelizable tasks at the beginning of the pivot
								- Merge all parallelizable tasks with the pivot
								- Merge the pivot with all the other subgraphs
						 */

						//Add all non parallelizable nodes at the beginning
						AbstractNode currentNode = new AbstractNode();
						currentNode.setProblematic();
						currentNode.setCorrected();
						AbstractNode startNode = currentNode;
						final Iterator<Node> iterator = nonParallelizableFreeNodes.iterator();
						currentNode.addNode(iterator.next());

						while (iterator.hasNext())
						{
							final Node nonParallelizableNode = iterator.next();
							final AbstractNode nextNode = new AbstractNode();
							nextNode.setProblematic();
							nextNode.setCorrected();
							nextNode.addNode(nonParallelizableNode);
							currentNode.addSuccessor(nextNode);
							currentNode = nextNode;
						}

						//currentNode.addSuccessor(copiedPivot.startNode());
						final AbstractGraph beginningGraph = new AbstractGraph();
						beginningGraph.setStartNode(startNode);

						//Merge all parallelizable nodes with the pivot
						final ArrayList<Pair<AbstractNode, AbstractNode>> combinations = this.mergeFreeNodesAndSubGraph(parallelizableFreeNodes, copiedPivot, cluster);
						final ArrayList<AbstractGraph> possibleGraphs = new ArrayList<>();

						for (Pair<AbstractNode, AbstractNode> combination : combinations)
						{
							final AbstractGraph currentGraph = beginningGraph.copy();
							currentGraph.lastNode().addSuccessor(combination.first());
							possibleGraphs.add(currentGraph);
						}

						//Merge all subgraphs together
						ArrayList<AbstractGraph> currentGraphsToMerge = possibleGraphs;

						for (int j = 1; j < abstractNodeToProcess.subGraphs().size(); j++)
						{
							final AbstractGraph currentAbstractGraph = ((ArrayList<AbstractGraph>) abstractNodeToProcess.subGraphs()).get(j);
							currentGraphsToMerge = this.mergeGraphsWithGraph(currentGraphsToMerge, currentAbstractGraph, cluster);
						}

						possiblyOptimalVersionsOfCurrentNode.addAll(currentGraphsToMerge);
					}
					else
					{
						/*
							There is at least one subgraph containing non parallelizable tasks. We need to:
								- Link all the non parallelizable nodes to the earliest non parallelizable task of the pivot
								- Merge all parallelizable tasks with the pivot
								- Merge the pivot with all the other subgraphs
						 */

						//Link all non parallelizable nodes to the earliest non parallelizable node of the pivot
						if (!nonParallelizableFreeNodes.isEmpty())
						{
							AbstractNode firstNonParallelizableNode = null;
							AbstractNode currentNode = null;

							while (true)
							{
								if (currentNode == null)
								{
									currentNode = copiedPivot.startNode();
								}

								if (currentNode.isProblematic())
								{
									firstNonParallelizableNode = currentNode;
									break;
								}

								if (currentNode.successors().isEmpty())
								{
									break;
								}
								else
								{
									currentNode = currentNode.successors().get(0);
								}
							}

							if (firstNonParallelizableNode == null)
							{
								throw new IllegalStateException();
							}

							final AbstractNode nodeAfterNonParallelizableFirstNode = firstNonParallelizableNode.successors().isEmpty() ? null : firstNonParallelizableNode.successors().get(0);
							firstNonParallelizableNode.successors().clear();

							AbstractNode currentNonParallelizableNode = new AbstractNode();
							currentNonParallelizableNode.setProblematic();
							currentNonParallelizableNode.setCorrected();
							final Iterator<Node> iterator = nonParallelizableFreeNodes.iterator();
							currentNonParallelizableNode.addNode(iterator.next());
							firstNonParallelizableNode.addSuccessor(currentNonParallelizableNode);

							while (iterator.hasNext())
							{
								final Node nonParallelizableNode = iterator.next();
								final AbstractNode nextNode = new AbstractNode();
								nextNode.setProblematic();
								nextNode.setCorrected();
								nextNode.addNode(nonParallelizableNode);
								currentNonParallelizableNode.addSuccessor(nextNode);
								currentNonParallelizableNode = nextNode;
							}

							if (nodeAfterNonParallelizableFirstNode != null)
							{
								currentNonParallelizableNode.addSuccessor(nodeAfterNonParallelizableFirstNode);
							}
						}

						//Merge all parallelizable nodes with the pivot
						final ArrayList<Pair<AbstractNode, AbstractNode>> combinations = this.mergeFreeNodesAndSubGraph(parallelizableFreeNodes, copiedPivot, cluster);
						final ArrayList<AbstractGraph> possibleGraphs = new ArrayList<>();

						for (Pair<AbstractNode, AbstractNode> combination : combinations)
						{
							final AbstractGraph newAbstractGraph = new AbstractGraph();
							newAbstractGraph.setStartNode(combination.first());
							possibleGraphs.add(newAbstractGraph);
						}

						//Merge all subgraphs together
						ArrayList<AbstractGraph> currentGraphsToMerge = possibleGraphs;

						for (int j = 1; j < abstractNodeToProcess.subGraphs().size(); j++)
						{
							final AbstractGraph currentAbstractGraph = ((ArrayList<AbstractGraph>) abstractNodeToProcess.subGraphs()).get(j);
							currentGraphsToMerge = this.mergeGraphsWithGraph(currentGraphsToMerge, currentAbstractGraph, cluster);
						}

						possiblyOptimalVersionsOfCurrentNode.addAll(currentGraphsToMerge);
					}
				}

				//Once all combinations of current node have been computed, generate a copy of the current
				//reference graph for each, and replace the problematic node with its corrected version
				for (final AbstractGraph possiblyOptimalVersionOfNode : possiblyOptimalVersionsOfCurrentNode)
				{
					this.markAllNodesAsCorrected(possiblyOptimalVersionOfNode);
					final AbstractGraph graphCopy = referenceGraph.copy();
					final AbstractNode firstNode = possiblyOptimalVersionOfNode.startNode();
					final AbstractNode lastNode = possiblyOptimalVersionOfNode.lastNode();
					final AbstractNode previousNodeInGraph = incorrectNode.first() == null ? new AbstractNode() : graphCopy.findNodeOfID(incorrectNode.first().id());
					final AbstractNode currentNodeInGraph = graphCopy.findNodeOfID(incorrectNode.second().id());
					final AbstractNode nextNodeInGraph = incorrectNode.second().successors().isEmpty() ? new AbstractNode() : graphCopy.findNodeOfID(incorrectNode.second().successors().iterator().next().id());

					if (previousNodeInGraph == null
						|| currentNodeInGraph == null
						|| nextNodeInGraph == null)
					{
						throw new IllegalStateException("Some nodes were null. previousNodeInGraph: " + (previousNodeInGraph == null) + " / currentNodeInGraph: " + (currentNodeInGraph == null) + " / nextNodeInGraph: " + (nextNodeInGraph == null));
					}

					if (incorrectNode.first() != null)
					{
						//The current node has a previous node
						previousNodeInGraph.successors().clear();
						previousNodeInGraph.addSuccessor(firstNode);
					}
					else
					{
						final AbstractGraph currentSubGraph = graphCopy.findSubgraphStartingWith(incorrectNode.second());
						currentSubGraph.setStartNode(firstNode);
					}

					if (!incorrectNode.second().successors().isEmpty())
					{
						//The current node has a next node
						lastNode.addSuccessor(nextNodeInGraph);
					}

					currentNodeInGraph.successors().clear();
					possibleOptimalVersionsOfReferenceGraph.add(graphCopy);
				}
			}

			generatedGraphs.clear();
			generatedGraphs.addAll(possibleOptimalVersionsOfReferenceGraph);
			break;
		}
	}

	private ArrayList<AbstractGraph> generateAbstractGraphsFromParallelizableNodes(final HashSet<Node> nodes)
	{
		final ArrayList<AbstractGraph> generatedGraphs = new ArrayList<>();

		if (HEURISTIC == Heuristic.COMBINATION)
		{
			final Collection<Collection<Node>> nodesCombinations = Utils.getCombinationsOf(nodes);
			final ArrayList<ArrayList<Collection<Node>>> combinations = new ArrayList<>();
			final ArrayList<Collection<Node>> firstCombination = new ArrayList<>();
			combinations.add(firstCombination);

			this.generateAbstractGraphsFromParallelizableNodesRec(nodesCombinations, combinations, firstCombination);

			for (ArrayList<Collection<Node>> combination : combinations)
			{
				AbstractNode currentNode = new AbstractNode();
				final AbstractGraph abstractGraph = new AbstractGraph();
				abstractGraph.setStartNode(currentNode);
				generatedGraphs.add(abstractGraph);
				final Iterator<Collection<Node>> iterator = combination.iterator();
				final Collection<Node> firstNodeContent = iterator.next();

				for (Node node : firstNodeContent)
				{
					currentNode.addNode(node);
				}

				while (iterator.hasNext())
				{
					final Collection<Node> currentContent = iterator.next();
					final AbstractNode nextNode = new AbstractNode();
					currentNode.addSuccessor(nextNode);
					currentNode = nextNode;

					for (Node node : currentContent)
					{
						currentNode.addNode(node);
					}
				}
			}
		}
		else
		{
			final AbstractNode abstractNode = new AbstractNode();
			final AbstractGraph abstractGraph = new AbstractGraph();
			abstractGraph.setStartNode(abstractNode);

			for (Node node : nodes)
			{
				abstractNode.addNode(node);
			}

			generatedGraphs.add(abstractGraph);
		}

		return generatedGraphs;
	}

	private void generateAbstractGraphsFromParallelizableNodesRec(final Collection<Collection<Node>> nodesCombinations,
																  final ArrayList<ArrayList<Collection<Node>>> allCombinations,
																  final ArrayList<Collection<Node>> currentCombination)
	{
		char i = 0;

		for (Collection<Node> combination : nodesCombinations)
		{
			final ArrayList<Collection<Node>> nextCombination;

			if (i == 0)
			{
				nextCombination = currentCombination;
				currentCombination.add(combination);
				i = 1;
			}
			else
			{
				final ArrayList<Collection<Node>> copiedCombination = new ArrayList<>(currentCombination);
				nextCombination = copiedCombination;
				allCombinations.add(copiedCombination);
				copiedCombination.add(combination);
			}

			final Collection<Collection<Node>> nodesCombinationsCopy = new ArrayList<>(nodesCombinations);
			this.removeIfContains(nodesCombinationsCopy, combination);

			this.generateAbstractGraphsFromParallelizableNodesRec(nodesCombinationsCopy, allCombinations, nextCombination);
		}
	}

	private void markAllNodesAsCorrected(final AbstractGraph graph)
	{
		AbstractNode currentNode = graph.startNode();
		currentNode.setCorrected();

		while (!currentNode.successors().isEmpty())
		{
			currentNode = currentNode.successors().get(0);
			currentNode.setCorrected();
		}
	}

	private ArrayList<AbstractGraph> mergeGraphsWithGraph(final ArrayList<AbstractGraph> combinedGraphs,
														  final AbstractGraph graph,
														  final Cluster cluster)
	{
		final ArrayList<AbstractGraph> combinations = new ArrayList<>();

		for (AbstractGraph combinedGraph : combinedGraphs)
		{
			final ArrayList<AbstractGraph> currentCombinations = this.mergeGraphWithGraph(combinedGraph, graph, cluster);
			combinations.addAll(currentCombinations);
		}

		return combinations;
	}

	private ArrayList<AbstractGraph> mergeGraphWithGraph(final AbstractGraph graph1,
														 final AbstractGraph graph2,
														 final Cluster cluster)
	{
		final ArrayList<AbstractNode> graph1Nodes = graph1.extractNodes();
		final ArrayList<AbstractNode> graph2Nodes = graph2.extractNodes();
		final ArrayList<ArrayList<AbstractNode>> graph1SplitNodes = this.splitInParallelizableBlocks(graph1Nodes);
		final ArrayList<ArrayList<AbstractNode>> graph2SplitNodes = this.splitInParallelizableBlocks(graph2Nodes);
		final ArrayList<ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>>> combinations = new ArrayList<>();

		if (HEURISTIC == Heuristic.COMBINATION)
		{
			final List<List<AbstractNode>> graph1Combinations = new ArrayList<>();
			final List<List<AbstractNode>> graph2Combinations = new ArrayList<>();
			final ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> firstCombination = new ArrayList<>();
			combinations.add(firstCombination);

			for (ArrayList<AbstractNode> abstractNodes : graph1SplitNodes)
			{
				final List<List<AbstractNode>> combination = Utils.getOrderedCombinationsOf(abstractNodes);
				graph1Combinations.addAll(combination);
			}

			for (ArrayList<AbstractNode> abstractNodes : graph2SplitNodes)
			{
				final List<List<AbstractNode>> combination = Utils.getOrderedCombinationsOf(abstractNodes);
				graph2Combinations.addAll(combination);
			}

			this.computeCombinationsOf(graph1Combinations, graph2Combinations, graph1Nodes, graph2Nodes, firstCombination, combinations);
		}
		else
		{
			final ArrayList<AbstractGraph> graph1Blocks = this.buildAbstractGraphFromNodes(graph1SplitNodes);
			final ArrayList<AbstractGraph> graph2Blocks = this.buildAbstractGraphFromNodes(graph2SplitNodes);
			final ArrayList<Pair<AbstractGraph, Integer>> graph1BlocksAndDuration = new ArrayList<>();
			final ArrayList<Pair<AbstractGraph, Integer>> graph2BlocksAndDuration = new ArrayList<>();

			for (AbstractGraph graph : graph1Blocks)
			{
				final EnhancedGraph enhancedGraph = BPMNUtils.generateEntireBPMNFromAbstractGraph(graph, cluster);
				final GraphToList graphToList = new GraphToList(enhancedGraph);
				graphToList.convert();
				final int duration = new LightOptimizer(graphToList.objectsList(), enhancedGraph).computeProcessExecutionTime();
				graph1BlocksAndDuration.add(new Pair<>(graph, duration));
			}

			for (AbstractGraph graph : graph2Blocks)
			{
				final EnhancedGraph enhancedGraph = BPMNUtils.generateEntireBPMNFromAbstractGraph(graph, cluster);
				final GraphToList graphToList = new GraphToList(enhancedGraph);
				graphToList.convert();
				final int duration = new LightOptimizer(graphToList.objectsList(), enhancedGraph).computeProcessExecutionTime();
				graph2BlocksAndDuration.add(new Pair<>(graph, duration));
			}

			final ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> combination = new ArrayList<>();
			combinations.add(combination);

			if (graph1Blocks.size() > graph2Blocks.size())
			{
				for (int i = 0; i < graph2BlocksAndDuration.size(); i++)
				{
					final Pair<AbstractGraph, Integer> blockToLink = graph2BlocksAndDuration.get(i);
					final int nbRemainingBlocks = graph2Blocks.size() - (i + 1);
					final int potentialBlocks = graph1Blocks.size() - nbRemainingBlocks;
					int smallestDistance = Integer.MAX_VALUE;
					int smallestNonImpactedDistance = Integer.MAX_VALUE;
					int bestNonImpactingCandidate = -1;
					int bestCandidate = -1;

					for (int j = 0; j < potentialBlocks; j++)
					{
						final Pair<AbstractGraph, Integer> candidate = graph1BlocksAndDuration.get(j);
						final int absorbance = candidate.second() - blockToLink.second();

						if (absorbance >= 0)
						{
							//Candidate can absorb
							if (absorbance < smallestNonImpactedDistance)
							{
								smallestNonImpactedDistance = absorbance;
								bestNonImpactingCandidate = j;
							}
						}

						if (absorbance < smallestDistance)
						{
							smallestDistance = absorbance;
							bestCandidate = j;
						}
					}

					final AbstractGraph bestGraph = (bestNonImpactingCandidate == -1) ? graph1Blocks.get(bestCandidate) : graph1Blocks.get(bestNonImpactingCandidate);
					combination.add(new Pair<>(bestGraph.extractNodes(), blockToLink.first().extractNodes()));

					for (int r = 0; r <= (bestNonImpactingCandidate == -1 ? bestCandidate : bestNonImpactingCandidate); r++)
					{
						graph1Blocks.remove(0);
						graph1BlocksAndDuration.remove(0);
					}
				}
			}
			else if (graph1Blocks.size() < graph2Blocks.size())
			{
				for (int i = 0; i < graph1BlocksAndDuration.size(); i++)
				{
					final Pair<AbstractGraph, Integer> blockToLink = graph1BlocksAndDuration.get(i);
					final int nbRemainingBlocks = graph1Blocks.size() - (i + 1);
					final int potentialBlocks = graph2Blocks.size() - nbRemainingBlocks;
					int smallestDistance = Integer.MAX_VALUE;
					int smallestNonImpactedDistance = Integer.MAX_VALUE;
					int bestNonImpactingCandidate = -1;
					int bestCandidate = -1;

					for (int j = 0; j < potentialBlocks; j++)
					{
						final Pair<AbstractGraph, Integer> candidate = graph2BlocksAndDuration.get(j);
						final int absorbance = candidate.second() - blockToLink.second();

						if (absorbance >= 0)
						{
							//Candidate can absorb
							if (absorbance < smallestNonImpactedDistance)
							{
								smallestNonImpactedDistance = absorbance;
								bestNonImpactingCandidate = j;
							}
						}

						if (absorbance < smallestDistance)
						{
							smallestDistance = absorbance;
							bestCandidate = j;
						}
					}

					final AbstractGraph bestGraph = (bestNonImpactingCandidate == -1) ? graph2Blocks.get(bestCandidate) : graph2Blocks.get(bestNonImpactingCandidate);
					combination.add(new Pair<>(blockToLink.first().extractNodes(), bestGraph.extractNodes()));

					for (int r = 0; r <= (bestNonImpactingCandidate == -1 ? bestCandidate : bestNonImpactingCandidate); r++)
					{
						graph2Blocks.remove(0);
						graph2BlocksAndDuration.remove(0);
					}
				}
			}
			else
			{
				for (int i = 0; i < graph1Blocks.size(); i++)
				{
					final ArrayList<AbstractNode> block1 = graph1Blocks.get(i).extractNodes();
					final ArrayList<AbstractNode> block2 = graph2Blocks.get(i).extractNodes();
					combination.add(new Pair<>(block1, block2));
				}
			}
		}

		final ArrayList<AbstractGraph> mergedGraphs = new ArrayList<>();

		for (ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> combination : combinations)
		{
			final AbstractGraph abstractGraph = this.buildGraphFromCombination(combination, graph1Nodes, graph2Nodes);
			mergedGraphs.add(abstractGraph);
		}

		return mergedGraphs;
	}

	private ArrayList<AbstractGraph> buildAbstractGraphFromNodes(final ArrayList<ArrayList<AbstractNode>> nodes)
	{
		final ArrayList<AbstractGraph> abstractGraphs = new ArrayList<>();

		for (ArrayList<AbstractNode> currentNodes : nodes)
		{
			AbstractNode currentNode = currentNodes.get(0).copy();
			final AbstractGraph abstractGraph = new AbstractGraph(currentNode);
			abstractGraphs.add(abstractGraph);

			for (int i = 1; i < currentNodes.size(); i++)
			{
				final AbstractNode nextNode = currentNodes.get(i).copy();
				currentNode.addSuccessor(nextNode);
				currentNode = nextNode;
			}
		}

		return abstractGraphs;
	}

	private AbstractGraph buildGraphFromCombination(final ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> combination,
													final ArrayList<AbstractNode> graph1Nodes,
													final ArrayList<AbstractNode> graph2Nodes)
	{
		if (combination.isEmpty())
		{
			//At least one of the abstract graphs to merge has only problematic nodes -> branch one to the other
			final AbstractGraph graph1Copy = new AbstractGraph(graph1Nodes.get(0)).copy();
			final AbstractGraph graph2Copy = new AbstractGraph(graph2Nodes.get(0)).copy();
			final ArrayList<AbstractNode> graph1CopiedNodes = graph1Copy.extractNodes();
			final AbstractGraph newGraph = new AbstractGraph(graph1CopiedNodes.get(0));
			graph1CopiedNodes.get(graph1CopiedNodes.size() - 1).addSuccessor(graph2Copy.startNode());
			return newGraph;
		}

		final AbstractNode firstNode1 = combination.get(0).first().get(0);
		final AbstractNode firstNode2 = combination.get(0).second().get(0);
		AbstractNode initialNode = null;
		AbstractNode currentNode = null;

		//If the first node of the combination for graph 1 is not the first node of graph 1,
		//add all missing nodes between the beginning of graph 1 and the initial node of the combination
		if (!firstNode1.id().equals(graph1Nodes.get(0).id()))
		{
			initialNode = graph1Nodes.get(0).copy();
			currentNode = initialNode;

			for (int i = 1; i < graph1Nodes.size(); i++)
			{
				final AbstractNode node = graph1Nodes.get(i);

				if (node.id().equals(firstNode1.id()))
				{
					break;
				}

				final AbstractNode copiedNode = node.copy();
				currentNode.addSuccessor(copiedNode);
				currentNode = copiedNode;
			}
		}

		//If the first node of the combination for graph 2 is not the first node of graph 2,
		//add all missing nodes between the beginning of graph 2 and the initial node of the combination
		if (!firstNode2.id().equals(graph2Nodes.get(0).id()))
		{
			if (initialNode == null)
			{
				initialNode = graph2Nodes.get(0).copy();
				currentNode = initialNode;
			}
			else
			{
				final AbstractNode currentInitialNode = graph2Nodes.get(0).copy();
				currentNode.addSuccessor(currentInitialNode);
				currentNode = currentInitialNode;
			}

			for (int i = 1; i < graph2Nodes.size(); i++)
			{
				final AbstractNode node = graph2Nodes.get(i);

				if (node.id().equals(firstNode2.id()))
				{
					break;
				}

				final AbstractNode copiedNode = node.copy();
				currentNode.addSuccessor(copiedNode);
				currentNode = copiedNode;
			}
		}

		//Add all combination nodes
		if (combination.size() == 1)
		{
			//If there is only one combination, generate it and add it
			final Pair<List<AbstractNode>, List<AbstractNode>> uniquePair = combination.get(0);
			final AbstractNode uniqueNode = this.mergeNodes(uniquePair);

			if (initialNode == null)
			{
				initialNode = uniqueNode;
			}
			else
			{
				currentNode.addSuccessor(uniqueNode);
			}

			currentNode = uniqueNode;
		}
		else
		{
			//If there are several combinations, generate them 2 by 2 and verify
			//whether they can be linked directly or not.
			//Sometimes, some nodes are missing, so we need to add them before linking.
			for (int i = 0; i < combination.size() - 1; i++)
			{
				final Pair<List<AbstractNode>, List<AbstractNode>> currentPair = combination.get(i);
				final Pair<List<AbstractNode>, List<AbstractNode>> nextPair = combination.get(i + 1);
				final AbstractNode nextNode = this.mergeNodes(nextPair);

				if (i == 0)
				{
					final AbstractNode firstNode = this.mergeNodes(currentPair);

					if (initialNode == null)
					{
						initialNode = firstNode;
					}
					else
					{
						currentNode.addSuccessor(firstNode);
					}

					currentNode = firstNode;
				}

				final AbstractNode lastCurrentNode1 = currentPair.first().get(currentPair.first().size() - 1);
				final AbstractNode firstNextNode1 = nextPair.first().get(0);

				if (this.areNotConsecutiveNodes(lastCurrentNode1, firstNextNode1, graph1Nodes))
				{
					//Consecutive abstract nodes currentNode and nextNode are not originally consecutive in first graph:
					//Add all missing nodes between them

					int index = 0;

					for (; index < graph1Nodes.size(); index++)
					{
						if (lastCurrentNode1.id().equals(graph1Nodes.get(index).id()))
						{
							index++;
							break;
						}
					}

					for (; index < graph1Nodes.size(); index++)
					{
						final AbstractNode nodeToCopy = graph1Nodes.get(index);

						if (firstNextNode1.id().equals(nodeToCopy.id()))
						{
							break;
						}

						final AbstractNode copiedNode = nodeToCopy.copy();
						currentNode.addSuccessor(copiedNode);
						currentNode = copiedNode;
					}
				}

				final AbstractNode lastCurrentNode2 = currentPair.second().get(currentPair.second().size() - 1);
				final AbstractNode firstNextNode2 = nextPair.second().get(0);

				if (this.areNotConsecutiveNodes(lastCurrentNode2, firstNextNode2, graph2Nodes))
				{
					//Consecutive abstract nodes currentNode and nextNode are not originally consecutive in second graph:
					//Add all missing nodes between them

					int index = 0;

					for (; index < graph2Nodes.size(); index++)
					{
						if (lastCurrentNode2.id().equals(graph2Nodes.get(index).id()))
						{
							index++;
							break;
						}
					}

					for (; index < graph2Nodes.size(); index++)
					{
						final AbstractNode nodeToCopy = graph2Nodes.get(index);

						if (firstNextNode2.id().equals(nodeToCopy.id()))
						{
							break;
						}

						final AbstractNode copiedNode = nodeToCopy.copy();
						currentNode.addSuccessor(copiedNode);
						currentNode = copiedNode;
					}
				}

				currentNode.addSuccessor(nextNode);
				currentNode = nextNode;
			}
		}

		if (initialNode == null
			|| currentNode == null)
		{
			throw new IllegalStateException();
		}

		//Sometimes, the last combination did not add all nodes of graph 1 & 2, so we add them
		final AbstractNode lastCombinedNode1 = combination.get(combination.size() - 1).first().get(combination.get(combination.size() - 1).first().size() - 1);
		final AbstractNode lastCombinedNode2 = combination.get(combination.size() - 1).second().get(combination.get(combination.size() - 1).second().size() - 1);

		if (!lastCombinedNode1.id().equals(graph1Nodes.get(graph1Nodes.size() - 1).id()))
		{
			int index = 0;

			for (; index < graph1Nodes.size(); index++)
			{
				if (graph1Nodes.get(index).id().equals(lastCombinedNode1.id()))
				{
					index++;
					break;
				}
			}

			for (; index < graph1Nodes.size(); index++)
			{
				final AbstractNode copiedNode = graph1Nodes.get(index).copy();
				currentNode.addSuccessor(copiedNode);
				currentNode = copiedNode;
			}
		}

		if (!lastCombinedNode2.id().equals(graph2Nodes.get(graph2Nodes.size() - 1).id()))
		{
			int index = 0;

			for (; index < graph2Nodes.size(); index++)
			{
				if (graph2Nodes.get(index).id().equals(lastCombinedNode2.id()))
				{
					index++;
					break;
				}
			}

			for (; index < graph2Nodes.size(); index++)
			{
				final AbstractNode copiedNode = graph2Nodes.get(index).copy();
				currentNode.addSuccessor(copiedNode);
				currentNode = copiedNode;
			}
		}

		final AbstractGraph abstractGraph = new AbstractGraph();
		abstractGraph.setStartNode(initialNode);
		return abstractGraph;
	}

	private boolean areNotConsecutiveNodes(final AbstractNode abstractNode1,
										   final AbstractNode abstractNode2,
										   final ArrayList<AbstractNode> graphNodes)
	{
		for (int i = 0; i < graphNodes.size(); i++)
		{
			final AbstractNode currentNode = graphNodes.get(i);

			if (currentNode.id().equals(abstractNode1.id()))
			{
				if (i < graphNodes.size() - 1
					&& graphNodes.get(i + 1).id().equals(abstractNode2.id()))
				{
					return false;
				}
			}
		}

		return true;
	}

	private AbstractNode mergeNodes(final Pair<List<AbstractNode>, List<AbstractNode>> pair)
	{
		final AbstractNode newNode = new AbstractNode();
		final AbstractGraph subGraph1 = new AbstractGraph();
		final AbstractGraph subGraph2 = new AbstractGraph();
		AbstractNode currentNode1 = null;
		AbstractNode currentNode2 = null;

		for (AbstractNode abstractNode : pair.first())
		{
			final AbstractNode nextNode = abstractNode.copy();

			if (currentNode1 == null)
			{
				currentNode1 = nextNode;
				subGraph1.setStartNode(nextNode);
			}
			else
			{
				currentNode1.addSuccessor(nextNode);
			}
		}

		for (AbstractNode abstractNode : pair.second())
		{
			final AbstractNode nextNode = abstractNode.copy();

			if (currentNode2 == null)
			{
				currentNode2 = nextNode;
				subGraph2.setStartNode(nextNode);
			}
			else
			{
				currentNode2.addSuccessor(nextNode);
			}
		}

		newNode.addSubgraph(subGraph1);
		newNode.addSubgraph(subGraph2);

		return newNode;
	}

	private void computeCombinationsOf(final List<List<AbstractNode>> abstractGraph1,
									   final List<List<AbstractNode>> abstractGraph2,
									   final ArrayList<AbstractNode> graph1Nodes,
									   final ArrayList<AbstractNode> graph2Nodes,
									   final ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> currentCombination,
									   final ArrayList<ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>>> allCombinations)
	{
		if (abstractGraph1.isEmpty()
			|| abstractGraph2.isEmpty())
		{
			return;
		}

		char i = 0;

		final ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> stableCombination = new ArrayList<>(currentCombination);

		for (List<AbstractNode> abstractNodeCombination1 : abstractGraph1)
		{
			for (List<AbstractNode> abstractNodeCombination2 : abstractGraph2)
			{
				final ArrayList<Pair<List<AbstractNode>, List<AbstractNode>>> combination;
				
				if (i == 0)
				{
					combination = currentCombination;
					i = 1;
				}
				else
				{
					combination = new ArrayList<>(stableCombination);
					allCombinations.add(combination);
				}

				final Pair<List<AbstractNode>, List<AbstractNode>> pair = new Pair<>(abstractNodeCombination1, abstractNodeCombination2);
				final List<List<AbstractNode>> copiedAbstractNodes1 = new ArrayList<>(abstractGraph1);
				final List<List<AbstractNode>> copiedAbstractNodes2 = new ArrayList<>(abstractGraph2);
				combination.add(pair);

				this.removeIfContainsAbstractNode(copiedAbstractNodes1, abstractNodeCombination1);
				this.removeIfContainsAbstractNode(copiedAbstractNodes2, abstractNodeCombination2);
				this.computeAndRemoveAllBefore(abstractNodeCombination1.get(abstractNodeCombination1.size() - 1), copiedAbstractNodes1, graph1Nodes);
				this.computeAndRemoveAllBefore(abstractNodeCombination2.get(abstractNodeCombination2.size() - 1), copiedAbstractNodes2, graph2Nodes);

				this.computeCombinationsOf(copiedAbstractNodes1, copiedAbstractNodes2, graph1Nodes, graph2Nodes, combination, allCombinations);
			}
		}
	}

	private void computeAndRemoveAllBefore(final AbstractNode lastNode,
										   final List<List<AbstractNode>> listNodes,
										   final ArrayList<AbstractNode> orderedNodes)
	{
		final ArrayList<AbstractNode> nodesToRemove = new ArrayList<>();

		for (AbstractNode node : orderedNodes)
		{
			nodesToRemove.add(node);

			if (node.equals(lastNode))
			{
				break;
			}
		}

		for (Iterator<List<AbstractNode>> iterator = listNodes.iterator(); iterator.hasNext(); )
		{
			final List<AbstractNode> currentList = iterator.next();
			boolean shouldBreak = false;

			for (AbstractNode nodeToRemove : nodesToRemove)
			{
				for (AbstractNode currentNode : currentList)
				{
					if (nodeToRemove.id().equals(currentNode.id()))
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
	}

	private ArrayList<ArrayList<AbstractNode>> splitInParallelizableBlocks(final ArrayList<AbstractNode> nodes)
	{
		final ArrayList<ArrayList<AbstractNode>> blocks = new ArrayList<>();
		final ArrayList<AbstractNode> currentBlock = new ArrayList<>();

		for (AbstractNode node : nodes)
		{
			if (node.isProblematic())
			{
				if (!currentBlock.isEmpty())
				{
					blocks.add(new ArrayList<>(currentBlock));
					currentBlock.clear();
				}
			}
			else
			{
				currentBlock.add(node);
			}
		}

		if (!currentBlock.isEmpty())
		{
			blocks.add(currentBlock);
		}

		return blocks;
	}

	private ArrayList<Pair<AbstractNode, AbstractNode>> mergeFreeNodesAndSubGraph(final HashSet<Node> parallelizableNodes,
																				  final AbstractGraph graph,
																				  final Cluster cluster)
	{
		if (parallelizableNodes.isEmpty())
		{
			final ArrayList<Pair<AbstractNode, AbstractNode>> list = new ArrayList<>();
			list.add(new Pair<>(graph.startNode(), graph.lastNode()));
			return list;
		}

		final ArrayList<Pair<AbstractNode, AbstractNode>> finalCombinations = new ArrayList<>();

		if (HEURISTIC == Heuristic.COMBINATION)
		{
			final Collection<Collection<Node>> parallelizableNodesCombinations = Utils.getCombinationsOf(parallelizableNodes);
			final ArrayList<AbstractNode> graphNodes = graph.extractNodes();
			final List<List<AbstractNode>> abstractNodesCombinations = new ArrayList<>();
			final ArrayList<ArrayList<AbstractNode>> parallelizableBlocks = this.splitInParallelizableBlocks(graphNodes);

			for (ArrayList<AbstractNode> parallelizableBlock : parallelizableBlocks)
			{
				abstractNodesCombinations.addAll(Utils.getOrderedCombinationsOf(parallelizableBlock));
			}

			final ArrayList<Pair<ArrayList<Pair<Collection<Node>, List<AbstractNode>>>, Collection<Collection<Node>>>> combinations = new ArrayList<>();
			final Pair<ArrayList<Pair<Collection<Node>, List<AbstractNode>>>, Collection<Collection<Node>>> firstPair = new Pair<>(new ArrayList<>(), null);
			combinations.add(firstPair);

			this.combineFreeNodesAndFreeSubgraph(parallelizableNodesCombinations, abstractNodesCombinations, firstPair, combinations, graphNodes);

			/*
				Now, each element of 'combinations' contains a (different) set of combinations of the parallelizable
				nodes and the abstract nodes of the graph.
				Thus, we need to generate the corresponding nodes for each combination.
			 */

			for (Pair<ArrayList<Pair<Collection<Node>, List<AbstractNode>>>, Collection<Collection<Node>>> combinationPair : combinations)
			{
				final Collection<Collection<Node>> remainingNodes = combinationPair.second();
				final ArrayList<Pair<Collection<Node>, List<AbstractNode>>> combination = combinationPair.first();
				final ArrayList<Pair<AbstractNode, List<AbstractNode>>> combinationNodes = new ArrayList<>();

				for (Pair<Collection<Node>, List<AbstractNode>> combinationElement : combination)
				{
					final AbstractNode newNode;

					if (combinationElement.second().size() == 1)
					{
						newNode = combinationElement.second().get(0).copy();
					}
					else
					{
						final AbstractGraph abstractGraph = new AbstractGraph();
						newNode = new AbstractNode();
						newNode.addSubgraph(abstractGraph);

						AbstractNode previousNewNode = null;

						for (int i = 0; i < combinationElement.second().size(); i++)
						{
							final AbstractNode currentNewNode = combinationElement.second().get(i).copy();

							if (previousNewNode == null)
							{
								abstractGraph.setStartNode(currentNewNode);
							}
							else
							{
								previousNewNode.addSuccessor(currentNewNode);
							}

							previousNewNode = currentNewNode;
						}
					}

					for (Node node : combinationElement.first())
					{
						newNode.addNode(node);
					}

					combinationNodes.add(new Pair<>(newNode, combinationElement.second()));
				}

				final AbstractNode firstNode = combinationNodes.get(0).second().get(0);
				final AbstractGraph abstractGraph = new AbstractGraph();
				AbstractNode currentNewNode;
				AbstractNode currentOldNode;

				if (!firstNode.id().equals(graphNodes.get(0).id()))
				{
					//We miss some nodes before the first node
					currentNewNode = graphNodes.get(0).copy();
					currentOldNode = graphNodes.get(0);
					abstractGraph.setStartNode(currentNewNode);

					while (!currentOldNode.successors().isEmpty())
					{
						final AbstractNode nextNode = currentOldNode.successors().get(0).copy();

						if (nextNode.id().equals(firstNode.id()))
						{
							break;
						}

						currentNewNode.addSuccessor(nextNode);
						currentNewNode = nextNode;
						currentOldNode = currentOldNode.successors().get(0);
					}

					currentNewNode.addSuccessor(combinationNodes.get(0).first());
				}
				else
				{
					abstractGraph.setStartNode(combinationNodes.get(0).first());
				}

				currentNewNode = combinationNodes.get(0).first();

				if (combinationNodes.size() > 1)
				{
					for (int i = 0; i < combinationNodes.size() - 1; i++)
					{
						final List<AbstractNode> currentList = combinationNodes.get(i).second();
						final List<AbstractNode> nextList = combinationNodes.get(i + 1).second();
						final AbstractNode currentMergedNode = combinationNodes.get(i).first();
						final AbstractNode nextMergedNode = combinationNodes.get(i + 1).first();
						final AbstractNode lastCurrent = currentList.get(currentList.size() - 1);
						final AbstractNode firstNext = nextList.get(0);

						if (this.areNotConsecutiveNodes(lastCurrent, firstNext, graphNodes))
						{
							currentNewNode = currentMergedNode;
							//We miss some nodes between last current and first next
							int index = 0;

							for (; index < graphNodes.size(); index++)
							{
								if (lastCurrent.id().equals(graphNodes.get(index).id()))
								{
									index++;
									break;
								}
							}

							for (; index < graphNodes.size(); index++)
							{
								final AbstractNode nodeToCopy = graphNodes.get(index);

								if (firstNext.id().equals(nodeToCopy.id()))
								{
									break;
								}

								final AbstractNode copiedNode = nodeToCopy.copy();
								currentNewNode.addSuccessor(copiedNode);
								currentNewNode = copiedNode;
							}

							currentNewNode.addSuccessor(nextMergedNode);
						}
						else
						{
							currentMergedNode.addSuccessor(nextMergedNode);
						}

						currentNewNode = nextMergedNode;
					}
				}


				//final AbstractNode newLastNode = combinationNodes.get(combinationNodes.size() - 1).first();
				final List<AbstractNode> lastList = combinationNodes.get(combinationNodes.size() - 1).second();
				final AbstractNode lastNode = lastList.get(lastList.size() - 1);

				if (!lastNode.id().equals(graphNodes.get(graphNodes.size() - 1).id()))
				{
					//We miss some nodes after the last new node
					int index = 0;

					for (; index < graphNodes.size(); index++)
					{
						if (graphNodes.get(index).id().equals(lastNode.id()))
						{
							index++;
							break;
						}
					}

					for (; index < graphNodes.size(); index++)
					{
						final AbstractNode copiedNode = graphNodes.get(index).copy();
						currentNewNode.addSuccessor(copiedNode);
						currentNewNode = copiedNode;
					}
				}

				if (remainingNodes != null)
				{
					final AbstractNode veryLastNode = new AbstractNode();

					for (Collection<Node> nodes : remainingNodes)
					{
						for (Node node : nodes)
						{
							veryLastNode.addNode(node);
						}
					}

					currentNewNode.addSuccessor(veryLastNode);
					currentNewNode = veryLastNode;
				}

				finalCombinations.add(new Pair<>(abstractGraph.startNode(), abstractGraph.lastNode()));
			}
		}
		else
		{
			final ArrayList<AbstractNode> nodes = graph.extractNodes();
			final ArrayList<ArrayList<AbstractNode>> parallelizableBlocks = this.splitInParallelizableBlocks(nodes);
			final ArrayList<Pair<AbstractNode, Integer>> nodesWithDuration = new ArrayList<>();

			for (ArrayList<AbstractNode> list : parallelizableBlocks)
			{
				for (AbstractNode node : list)
				{
					final AbstractNode copy = node.copy();
					final EnhancedGraph bpmnGraph = BPMNUtils.generateEntireBPMNFromAbstractGraph(new AbstractGraph(copy), cluster);
					final GraphToList graphToList = new GraphToList(bpmnGraph);
					graphToList.convert();
					int executionTime = new LightOptimizer(graphToList.objectsList(), bpmnGraph).computeProcessExecutionTime();
					nodesWithDuration.add(new Pair<>(node, executionTime));
				}
			}

			for (Node node : parallelizableNodes)
			{
				final EnhancedNode enhancedNode = cluster.findEnhancedNodeFrom(node);
				final int duration;

				if (enhancedNode.type() == EnhancedType.CHOICE)
				{
					final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;
					int maxDuration = -1;

					for (Cluster choiceCluster : enhancedChoice.clusters())
					{
						final BPMNGenerator bpmnGenerator = new BPMNGenerator(choiceCluster);
						final Graph bpmn = bpmnGenerator.generate();
						bpmnGenerator.unprocessClusters();
						final GraphToList graphToList = new GraphToList(bpmn);
						graphToList.convert();
						final int currentDuration = new LightOptimizer(graphToList.objectsList(), bpmn).computeProcessExecutionTime();

						if (currentDuration > maxDuration)
						{
							maxDuration = currentDuration;
						}
					}

					duration = maxDuration;
				}
				else if (enhancedNode.type() == EnhancedType.LOOP)
				{
					final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

					final BPMNGenerator bpmnGenerator = new BPMNGenerator(enhancedLoop.entryToExitCluster());
					final Graph bpmn = bpmnGenerator.generate();
					bpmnGenerator.unprocessClusters();
					final GraphToList graphToList = new GraphToList(bpmn);
					graphToList.convert();
					final int entryToExitDuration = new LightOptimizer(graphToList.objectsList(), bpmn).computeProcessExecutionTime();

					final BPMNGenerator bpmnGenerator2 = new BPMNGenerator(enhancedLoop.exitToEntryCluster());
					final Graph bpmn2 = bpmnGenerator2.generate();
					bpmnGenerator.unprocessClusters();
					final GraphToList graphToList2 = new GraphToList(bpmn2);
					graphToList2.convert();
					final int exitToEntryDuration = new LightOptimizer(graphToList2.objectsList(), bpmn2).computeProcessExecutionTime();

					duration = entryToExitDuration + exitToEntryDuration;
				}
				else
				{
					duration = ((Task) node.bpmnObject()).duration();
				}

				AbstractNode closestNode = null;
				AbstractNode closestNonImpactedNode = null;
				int closestDistance = Integer.MAX_VALUE;
				int closestNonImpactingDistance = Integer.MAX_VALUE;

				for (Pair<AbstractNode, Integer> nodeWithDuration : nodesWithDuration)
				{
					final int currentDistance = nodeWithDuration.second() - duration;

					if (currentDistance >= 0)
					{
						//Abstract node will not be impacted
						if (currentDistance < closestNonImpactingDistance)
						{
							closestNonImpactingDistance = currentDistance;
							closestNonImpactedNode = nodeWithDuration.first();
						}
					}

					if (currentDistance < closestDistance)
					{
						closestDistance = currentDistance;
						closestNode = nodeWithDuration.first();
					}
				}

				if (closestNode == null) throw new IllegalStateException();

				if (closestNonImpactedNode == null)
				{
					closestNode.addNode(node);
				}
				else
				{
					closestNonImpactedNode.addNode(node);
				}
			}

			finalCombinations.add(new Pair<>(graph.startNode(), graph.lastNode()));
		}

		return finalCombinations;
	}

	private void combineFreeNodesAndFreeSubgraph(final Collection<Collection<Node>> parallelizableNodes,
												 final List<List<AbstractNode>> abstractNodes,
												 final Pair<ArrayList<Pair<Collection<Node>, List<AbstractNode>>>, Collection<Collection<Node>>> currentCombination,
												 final ArrayList<Pair<ArrayList<Pair<Collection<Node>, List<AbstractNode>>>, Collection<Collection<Node>>>> allCombinations,
												 final ArrayList<AbstractNode> graphNodes)
	{
		if (parallelizableNodes.isEmpty()
			&& abstractNodes.isEmpty())
		{
			return;
		}
		else if (parallelizableNodes.isEmpty())
		{
			return;
		}
		else if (abstractNodes.isEmpty())
		{
			currentCombination.setSecond(parallelizableNodes);
			return;
		}

		char i = 0;
		final ArrayList<Pair<Collection<Node>, List<AbstractNode>>> stableCombination = new ArrayList<>(currentCombination.first());

		for (List<AbstractNode> abstractNodesCombination : abstractNodes)
		{
			for (Collection<Node> parallelizableNodesCombination : parallelizableNodes)
			{
				final ArrayList<Pair<Collection<Node>, List<AbstractNode>>> combination;
				final Pair<ArrayList<Pair<Collection<Node>, List<AbstractNode>>>, Collection<Collection<Node>>> combinationPair;

				if (i == 0)
				{
					combinationPair = currentCombination;
					combination = currentCombination.first();
					i = 1;
				}
				else
				{
					combination = new ArrayList<>(stableCombination);
					combinationPair = new Pair<>(combination, null);
					allCombinations.add(combinationPair);
				}

				final Pair<Collection<Node>, List<AbstractNode>> pair = new Pair<>(parallelizableNodesCombination, abstractNodesCombination);
				final Collection<Collection<Node>> copiedParallelizableNodes = new ArrayList<>(parallelizableNodes);
				final List<List<AbstractNode>> copiedAbstractNodes = new ArrayList<>(abstractNodes);
				combination.add(pair);

				this.removeIfContains(copiedParallelizableNodes, parallelizableNodesCombination);
				this.removeIfContainsAbstractNode(copiedAbstractNodes, abstractNodesCombination);
				this.computeAndRemoveAllBefore(abstractNodesCombination.get(abstractNodesCombination.size() - 1), copiedAbstractNodes, graphNodes);
				this.combineFreeNodesAndFreeSubgraph(copiedParallelizableNodes, copiedAbstractNodes, combinationPair, allCombinations, graphNodes);
			}
		}
	}

	/**
	 * This method aims at retrieving the most outer abstract graph corresponding to the current
	 * abstract node, that is one of the abstract graphs belonging to cluster "mainCluster".
	 *
	 * @param abstractNode the abstract node to study
	 * @return the most outer abstract graph containing abstractNode
	 */
	private AbstractGraph findReferenceGraph(final AbstractNode abstractNode,
											 final Cluster cluster)
	{
		if (cluster.abstractGraphs().size() == 1)
		{
			return cluster.abstractGraphs().get(0);
		}

		final Node node = this.findAnyNodeOf(abstractNode);

		if (node == null) throw new IllegalStateException();

		for (AbstractGraph abstractGraph : cluster.abstractGraphs())
		{
			if (abstractGraph.allNodes().contains(node))
			{
				return abstractGraph;
			}
		}

		throw new IllegalStateException();
	}

	private Node findAnyNodeOf(AbstractNode abstractNode)
	{
		if (!abstractNode.listNodes().isEmpty())
		{
			return abstractNode.listNodes().iterator().next();
		}

		if (!abstractNode.successors().isEmpty())
		{
			final Node node = this.findAnyNodeOf(abstractNode.successors().get(0));

			if (node != null)
			{
				return node;
			}
		}

		for (AbstractGraph subGraph : abstractNode.subGraphs())
		{
			final Node node = this.findAnyNodeOf(subGraph.startNode());

			if (node != null)
			{
				return node;
			}
		}

		return null;
	}

	/**
	 * This function returns the first abstract graph containing a non-parallelizable
	 * task.
	 *
	 * @param abstractNode the abstract node containing the abstract graphs
	 * @return the first abstract graph containing a non-parallelizable task, null if such graph does not exist
	 */
	private AbstractGraph electPivot(final AbstractNode abstractNode)
	{
		AbstractGraph pivot = null;

		for (AbstractGraph abstractGraph : abstractNode.subGraphs())
		{
			for (Node problematicNode : this.problematicNodes)
			{
				if (abstractGraph.allNodes().contains(problematicNode))
				{
					pivot = abstractGraph;
					break;
				}
			}

			if (pivot != null)
			{
				break;
			}
		}

		if (pivot != null)
		{
			abstractNode.subGraphs().remove(pivot);
			abstractNode.addSubgraphAt(pivot, 0);
		}

		return pivot;
	}

	/**
	 * This function marks as correct all the existing abstract nodes that do not contain
	 * non parallelizable tasks.
	 */
	private void setAlreadyCorrectNodesPerCluster(final Cluster cluster)
	{
		for (AbstractGraph abstractGraph : cluster.abstractGraphs())
		{
			this.setAlreadyCorrectNodesPerGraph(abstractGraph);
		}

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.setAlreadyCorrectNodesPerCluster(choiceCluster);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				this.setAlreadyCorrectNodesPerCluster(enhancedLoop.entryToExitCluster());
				this.setAlreadyCorrectNodesPerCluster(enhancedLoop.exitToEntryCluster());
			}
		}
	}

	private boolean setAlreadyCorrectNodesPerGraph(final AbstractGraph abstractGraph)
	{
		AbstractNode currentNode = abstractGraph.startNode();
		boolean abstractGraphContainsProblematicNodes = false;

		while (true)
		{
			boolean abstractNodeContainsProblematicNodes = false;

			for (Node node : currentNode.listNodes())
			{
				for (Node problematicNode : this.problematicNodes)
				{
					if (node.equals(problematicNode))
					{
						abstractNodeContainsProblematicNodes = true;
						break;
					}
				}

				if (abstractNodeContainsProblematicNodes)
				{
					break;
				}
			}

			for (AbstractGraph subGraph : currentNode.subGraphs())
			{
				if (this.setAlreadyCorrectNodesPerGraph(subGraph))
				{
					abstractNodeContainsProblematicNodes = true;
					//Don't break to visit all subgraphs
				}
			}

			if (abstractNodeContainsProblematicNodes)
			{
				abstractGraphContainsProblematicNodes = true;
				currentNode.setProblematic();
			}
			else
			{
				currentNode.setCorrected();
			}

			if (currentNode.successors().isEmpty())
			{
				break;
			}
			else
			{
				currentNode = currentNode.successors().get(0);
			}
		}

		return abstractGraphContainsProblematicNodes;
	}

	/**
	 * An abstract node is ready to be corrected as long as it contains non parallelizable
	 * tasks and the sub node containing the task (if existing) has already been corrected
	 *
	 * @param cluster the current cluster to analyze
	 */
	private void findAbstractNodesToCorrect(final Cluster cluster,
											final ArrayList<Pair<AbstractNode, AbstractNode>> abstractNodesToCorrect)
	{
		for (AbstractGraph abstractGraph : cluster.abstractGraphs())
		{
			this.findAbstractNodesToCorrectPerSubGraph(abstractGraph, new HashSet<>(), abstractNodesToCorrect);
		}
	}

	/**
	 * An abstract node is ready to be corrected as long it contains non parallelizable
	 * tasks and the sub node containing the task (if existing) has already been corrected
	 *
	 * @param abstractGraphs the list of abstract graphs to verify
	 */
	private void findAbstractNodesToCorrect(final Collection<AbstractGraph> abstractGraphs,
											final ArrayList<Pair<AbstractNode, AbstractNode>> abstractNodesToCorrect)
	{
		for (AbstractGraph abstractGraph : abstractGraphs)
		{
			this.findAbstractNodesToCorrectPerSubGraph(abstractGraph, new HashSet<>(), abstractNodesToCorrect);
		}
	}

	private void findAbstractNodesToCorrectPerSubGraph(final AbstractGraph abstractGraph,
													   final HashSet<AbstractGraph> graphsToModify,
													   final ArrayList<Pair<AbstractNode, AbstractNode>> abstractNodesToCorrect)
	{
		AbstractNode previousNode = null;
		AbstractNode currentNode = abstractGraph.startNode();

		while (true)
		{
			if (!currentNode.hasBeenCorrected())
			{
				ArrayList<AbstractGraph> nonReadyGraphs = new ArrayList<>();

				for (AbstractGraph subGraph : currentNode.subGraphs())
				{
					if (!abstractGraphIsCorrect(subGraph))
					{
						nonReadyGraphs.add(subGraph);
					}
				}

				if (nonReadyGraphs.isEmpty()
					&& !graphsToModify.contains(abstractGraph))
				{
					graphsToModify.add(abstractGraph);
					abstractNodesToCorrect.add(new Pair<>(previousNode, currentNode));
				}
				else
				{
					for (AbstractGraph nonReadyGraph : nonReadyGraphs)
					{
						this.findAbstractNodesToCorrectPerSubGraph(nonReadyGraph, graphsToModify, abstractNodesToCorrect);
					}
				}
			}

			if (currentNode.successors().isEmpty())
			{
				break;
			}
			else
			{
				previousNode = currentNode;
				currentNode = currentNode.successors().get(0);
			}
		}
	}

	/**
	 * An abstract graph is correct if all of its nodes have been corrected
	 * or do not need to be corrected.
	 *
	 * @return true if the abstract graph is correct, false otherwise
	 */
	private boolean abstractGraphIsCorrect(final AbstractGraph abstractGraph)
	{
		AbstractNode currentNode = abstractGraph.startNode();

		while (true)
		{
			if (!currentNode.hasBeenCorrected())
			{
				return false;
			}

			if (currentNode.successors().isEmpty())
			{
				break;
			}
			else
			{
				currentNode = currentNode.successors().get(0);
			}
		}

		return true;
	}

	private void computeSubgraphsTasks(final Cluster cluster)
	{
		for (AbstractGraph graph : cluster.abstractGraphs())
		{
			graph.computeAllNodes();
		}

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.computeSubgraphsTasks(choiceCluster);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				this.computeSubgraphsTasks(enhancedLoop.entryToExitCluster());
				this.computeSubgraphsTasks(enhancedLoop.exitToEntryCluster());
			}
		}
	}

	private <T> void removeIfContains(Collection<Collection<T>> collection,
									  Collection<T> element)
	{
		for (Iterator<Collection<T>> iterator = collection.iterator(); iterator.hasNext(); )
		{
			final Collection<T> currentCollection = iterator.next();

			for (T t : element)
			{
				if (currentCollection.contains(t))
				{
					iterator.remove();
					break;
				}
			}
		}
	}

	private void removeIfContainsAbstractNode(List<List<AbstractNode>> collection,
											  Collection<AbstractNode> element)
	{
		for (Iterator<List<AbstractNode>> iterator = collection.iterator(); iterator.hasNext(); )
		{
			final Collection<AbstractNode> currentCollection = iterator.next();
			boolean shouldBreak = false;

			for (AbstractNode thisNode : currentCollection)
			{
				for (AbstractNode thatNode : element)
				{
					if (thisNode.id().equals(thatNode.id()))
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
	}
}
