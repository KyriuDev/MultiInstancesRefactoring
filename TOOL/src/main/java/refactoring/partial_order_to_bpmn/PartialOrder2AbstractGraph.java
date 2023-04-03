package refactoring.partial_order_to_bpmn;

import bpmn.graph.Node;
import other.Utils;
import refactoring.dependencies.DependencyGraph;
import refactoring.dependencies.Paths2DependencyGraph;
import refactoring.exceptions.BadDependencyException;

import java.util.*;

public class PartialOrder2AbstractGraph
{
	private final DependencyGraph dependencyGraph;

	private AbstractGraph abstractGraph;

	public PartialOrder2AbstractGraph(final DependencyGraph dependencyGraph)
	{
		this.dependencyGraph = dependencyGraph;
	}

	public AbstractGraph generateAbstractGraph() throws BadDependencyException
	{
		final AbstractGraph mainGraph = new AbstractGraph();
		this.generateAbstractGraphRec(this.dependencyGraph, mainGraph);
		return this.abstractGraph = mainGraph;
	}

	public AbstractGraph abstractGraph()
	{
		return this.abstractGraph;
	}

	//Private void

	private void generateAbstractGraphRec(final DependencyGraph dependencyGraph,
										  final AbstractGraph abstractSubGraph) throws BadDependencyException
	{
		final ArrayList<ArrayList<Node>> paths = new ArrayList<>();

		for (Node initialNode : dependencyGraph.initialNodes())
		{
			final ArrayList<Node> path = new ArrayList<>();
			paths.add(path);
			this.computeAllPaths(initialNode, path, paths);
		}

		final ArrayList<Node> sharedNodes = this.computePathsIntersection(paths);

		if (sharedNodes.isEmpty())
		{
			//Find all nodes that synchronize paths
			final HashSet<Node> synchronizationPoints = new HashSet<>();
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node startNode : dependencyGraph.initialNodes())
			{
				this.findSynchronizationPoints(startNode, synchronizationPoints, visitedNodes);
			}

			//For each synchronization node, find all the paths it synchronizes
			final HashMap<Node, ArrayList<ArrayList<Node>>> pathsSynchronizedPerNode = new HashMap<>();

			for (Node synchronizationPoint : synchronizationPoints)
			{
				final ArrayList<ArrayList<Node>> pathsSynchronized = this.findPathsSynchronizedBy(synchronizationPoint, paths);
				pathsSynchronizedPerNode.put(synchronizationPoint, pathsSynchronized);
			}

			//Group synchronization nodes together if they synchronize the same initial portion of path
			//(i.e., up to the synchronization node (excluded))
			final ArrayList<ArrayList<Node>> groupedNodes = new ArrayList<>();

			for (Node synchronizationPoint : pathsSynchronizedPerNode.keySet())
			{
				boolean synchronizationPointGrouped = false;

				for (ArrayList<Node> group : groupedNodes)
				{
					final ArrayList<ArrayList<Node>> synchronizedPathsOfGroup = pathsSynchronizedPerNode.get(group.get(0));

					if (synchronizedPathsOfGroup.equals(pathsSynchronizedPerNode.get(synchronizationPoint)))
					{
						synchronizationPointGrouped = true;
						group.add(synchronizationPoint);
						break;
					}
				}

				if (!synchronizationPointGrouped)
				{
					final ArrayList<Node> newGroup = new ArrayList<>();
					newGroup.add(synchronizationPoint);
					groupedNodes.add(newGroup);
				}
			}

			//Keep the group of nodes that synchronizes all the paths (theoretically, there should be only one)
			ArrayList<Node> finalGroup = null;

			for (ArrayList<Node> group : groupedNodes)
			{
				int nbPathsSynchronized = 0;

				for (Node synchronizationNode : group)
				{
					nbPathsSynchronized += pathsSynchronizedPerNode.get(synchronizationNode).size();
				}

				if (nbPathsSynchronized == paths.size())
				{
					//All paths are synchronized on this group
					if (finalGroup != null)
					{
						throw new IllegalStateException("An eligible group of synchronized nodes has already been found.");
					}

					finalGroup = group;
				}
			}

			if (finalGroup == null)
			{
				/*
					This will happen in the case not covered by SCC, namely:
						- Tasks: [A,B,C,D,E]
						- Partial orders: [[A,B],[A,D],[B,E],[C,E]]
				 */

				throw new BadDependencyException("No group of synchronization nodes found.");
			}

			//Separate paths in two: a first part up to the synchronization point (excluded), and the other to the end
			final ArrayList<ArrayList<Node>> pathsBefore = this.computePathsBefore(finalGroup, paths);
			final ArrayList<ArrayList<Node>> pathsAfter = this.computePathsAfter(finalGroup, paths);

			//Generate the two sets of dependency graphs
			final ArrayList<DependencyGraph> dependencyGraphsBefore = this.separatePathsAndGenerateDependencyGraphs(pathsBefore);
			final ArrayList<DependencyGraph> dependencyGraphsAfter = this.separatePathsAndGenerateDependencyGraphs(pathsAfter);

			//Generate the two nodes corresponding to the split
			final AbstractNode beforeNode = new AbstractNode();
			final AbstractNode afterNode = new AbstractNode();
			beforeNode.addSuccessor(afterNode);
			abstractSubGraph.setStartNode(beforeNode);

			//Call the method recursively on all the dependency graphs built and the corresponding abstract graphs
			for (DependencyGraph dependencyGraphBefore : dependencyGraphsBefore)
			{
				final AbstractGraph subGraph = new AbstractGraph();
				beforeNode.addSubgraph(subGraph);
				this.generateAbstractGraphRec(dependencyGraphBefore, subGraph);
			}

			for (DependencyGraph dependencyGraphAfter : dependencyGraphsAfter)
			{
				final AbstractGraph subGraph = new AbstractGraph();
				afterNode.addSubgraph(subGraph);
				this.generateAbstractGraphRec(dependencyGraphAfter, subGraph);
			}
		}
		else
		{
			final ArrayList<ArrayList<Node>> isolatedSharedNodes = this.isolateSharedNodes(sharedNodes);
			final Node firstSharedNode = isolatedSharedNodes.get(0).get(0);
			AbstractNode firstAbstractNode = null;

			//If the first shared nodes has parents, check whether they are connected or not.
			//If not, put them in separated abstract graphs
			if (firstSharedNode.hasParents())
			{
				final ArrayList<HashSet<ArrayList<Node>>> pathsPerParent = new ArrayList<>();

				for (Node parent : firstSharedNode.parentNodes())
				{
					pathsPerParent.add(this.cutAllPathsAfter(paths, parent));
				}

				this.mergePathsIfConnected(pathsPerParent);

				firstAbstractNode = new AbstractNode();
				abstractSubGraph.setStartNode(firstAbstractNode);

				for (HashSet<ArrayList<Node>> isolatedPaths : pathsPerParent)
				{
					final AbstractGraph subGraph = new AbstractGraph();
					firstAbstractNode.addSubgraph(subGraph);
					final DependencyGraph dependencySubGraph = Paths2DependencyGraph.transform(isolatedPaths);
					this.generateAbstractGraphRec(dependencySubGraph, subGraph);
				}
			}

			AbstractNode lastSharedAbstractNode = null;

			for (int i = 0; i < isolatedSharedNodes.size() - 1; i++)
			{
				final ArrayList<Node> firstIsolatedNodes = isolatedSharedNodes.get(i);
				final ArrayList<Node> secondIsolatedNodes = isolatedSharedNodes.get(i + 1);

				final AbstractNode firstFirstIsolatedNode = new AbstractNode();
				final AbstractNode lastFirstIsolatedNode = firstIsolatedNodes.size() == 1 ? firstFirstIsolatedNode : new AbstractNode();
				final AbstractNode firstSecondIsolatedNode = new AbstractNode();
				final AbstractNode lastSecondIsolatedNode = secondIsolatedNodes.size() == 1 ? firstSecondIsolatedNode : new AbstractNode();
				lastSharedAbstractNode = lastSecondIsolatedNode;
				AbstractNode currentFirstIsolatedNode = firstFirstIsolatedNode;
				AbstractNode currentSecondIsolatedNode = firstSecondIsolatedNode;

				if (i == 0)
				{
					if (firstAbstractNode == null)
					{
						abstractSubGraph.setStartNode(firstFirstIsolatedNode);
					}
					else
					{
						firstAbstractNode.addSuccessor(firstFirstIsolatedNode);
					}
				}

				for (int j = 0; j < firstIsolatedNodes.size(); j++)
				{
					final Node firstIsolatedNode = firstIsolatedNodes.get(j);

					if (j == 0)
					{
						firstFirstIsolatedNode.addNode(firstIsolatedNode);
					}
					else if (j == firstIsolatedNodes.size() - 1)
					{
						lastFirstIsolatedNode.addNode(firstIsolatedNode);
						currentFirstIsolatedNode.addSuccessor(lastFirstIsolatedNode);
					}
					else
					{
						final AbstractNode nextNode = new AbstractNode();
						nextNode.addNode(firstIsolatedNode);
						currentFirstIsolatedNode.addSuccessor(nextNode);
						currentFirstIsolatedNode = nextNode;
						//abstractSubGraph.addNode(nextNode);
					}
				}

				for (int j = 0; j < secondIsolatedNodes.size(); j++)
				{
					final Node secondIsolatedNode = secondIsolatedNodes.get(j);

					if (j == 0)
					{
						firstSecondIsolatedNode.addNode(secondIsolatedNode);
					}
					else if (j == secondIsolatedNodes.size() - 1)
					{
						lastSecondIsolatedNode.addNode(secondIsolatedNode);
						currentSecondIsolatedNode.addSuccessor(lastSecondIsolatedNode);
					}
					else
					{
						final AbstractNode nextNode = new AbstractNode();
						nextNode.addNode(secondIsolatedNode);
						currentSecondIsolatedNode.addSuccessor(nextNode);
						currentSecondIsolatedNode = nextNode;
						//abstractSubGraph.addNode(nextNode);
					}
				}

				final AbstractNode midNode = new AbstractNode();
				//abstractSubGraph.addNode(midNode);

				lastFirstIsolatedNode.addSuccessor(midNode);
				midNode.addSuccessor(firstSecondIsolatedNode);

				for (final Node child : lastFirstIsolatedNode.listNodes().iterator().next().childNodes())
				{
					final AbstractGraph subGraph = new AbstractGraph();
					midNode.addSubgraph(subGraph);

					final ArrayList<ArrayList<Node>> allPaths = new ArrayList<>();
					final ArrayList<Node> initialPath = new ArrayList<>();
					allPaths.add(initialPath);
					dependencyGraph.findPathsBetween(child, secondIsolatedNodes.get(0), initialPath, allPaths);

					this.generateAbstractGraphRec(Paths2DependencyGraph.transform(allPaths), subGraph);
				}
 			}

			if (lastSharedAbstractNode == null)
			{
				AbstractNode previousNode = null;

				for (Node node : isolatedSharedNodes.get(0))
				{
					if (previousNode == null)
					{
						previousNode = new AbstractNode();
						previousNode.addNode(node);

						if (firstAbstractNode == null)
						{
							abstractSubGraph.setStartNode(previousNode);
						}
						else
						{
							firstAbstractNode.addSuccessor(previousNode);
						}
					}
					else
					{
						final AbstractNode currentNode = new AbstractNode();
						currentNode.addNode(node);
						previousNode.addSuccessor(currentNode);
						//abstractSubGraph.addNode(currentNode);
						previousNode = currentNode;
					}
				}

				lastSharedAbstractNode = previousNode;
			}

			final Node lastSharedNodeNode = lastSharedAbstractNode.listNodes().iterator().next();

			//The last shared node has children -> Verify whether they are completely disconnected or not.
			if (lastSharedNodeNode.hasChilds())
			{
				final ArrayList<HashSet<ArrayList<Node>>> pathsPerChild = new ArrayList<>();

				for (Node child : lastSharedNodeNode.childNodes())
				{
					pathsPerChild.add(this.cutAllPathsBefore(paths, child));
				}

				this.mergePathsIfConnected(pathsPerChild);

				final AbstractNode lastAbstractNode = new AbstractNode();
				lastSharedAbstractNode.addSuccessor(lastAbstractNode);

				for (HashSet<ArrayList<Node>> isolatedPaths : pathsPerChild)
				{
					final AbstractGraph subGraph = new AbstractGraph();
					lastAbstractNode.addSubgraph(subGraph);
					final DependencyGraph dependencySubGraph = Paths2DependencyGraph.transform(isolatedPaths);
					this.generateAbstractGraphRec(dependencySubGraph, subGraph);
				}
			}
		}
	}

	/**
	 * This function takes as input all the paths starting with a child of shared node.
	 * It merges two sets of paths starting with a different child if they share some of their nodes.
	 * In other words, two sets of paths are not merged if they are completely separated from each other.
	 * This function runs iteratively until reaching a fixed point.
	 *
	 * @param pathsPerChild the set of paths to merge (side effect)
	 */
	private void mergePathsIfConnected(final ArrayList<HashSet<ArrayList<Node>>> pathsPerChild)
	{
		final ArrayList<HashSet<ArrayList<Node>>> previousMergedPaths = new ArrayList<>();
		final ArrayList<HashSet<ArrayList<Node>>> currentMergedPaths = new ArrayList<>(pathsPerChild);
		boolean modified = true;

		while (modified)
		{
			modified = false;

			for (HashSet<ArrayList<Node>> pathsToMerge : currentMergedPaths)
			{
				boolean pathMerged = false;

				for (HashSet<ArrayList<Node>> targetPaths : previousMergedPaths)
				{
					if (this.pathsShouldBeMerged(pathsToMerge, targetPaths))
					{
						modified = true;
						pathMerged = true;
						targetPaths.addAll(pathsToMerge);
						break;
					}
				}

				if (!pathMerged)
				{
					previousMergedPaths.add(pathsToMerge);
				}
			}

			currentMergedPaths.clear();
			currentMergedPaths.addAll(previousMergedPaths);
			previousMergedPaths.clear();
		}

		pathsPerChild.clear();
		pathsPerChild.addAll(currentMergedPaths);
	}

	private boolean pathsShouldBeMerged(final HashSet<ArrayList<Node>> paths1,
										final HashSet<ArrayList<Node>> paths2)
	{
		for (ArrayList<Node> path1 : paths1)
		{
			for (Node currentNodePath1 : path1)
			{
				for (ArrayList<Node> path2 : paths2)
				{
					if (path2.contains(currentNodePath1))
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	private ArrayList<DependencyGraph> separatePathsAndGenerateDependencyGraphs(final ArrayList<ArrayList<Node>> paths)
	{
		final ArrayList<DependencyGraph> dependencyGraphs = new ArrayList<>();
		final ArrayList<ArrayList<ArrayList<Node>>> pathsClusters = new ArrayList<>();

		for (ArrayList<Node> path : paths)
		{
			boolean pathAdded = false;

			for (ArrayList<ArrayList<Node>> clusterOfPaths : pathsClusters)
			{
				for (ArrayList<Node> clusterPath : clusterOfPaths)
				{
					for (Node node : path)
					{
						if (clusterPath.contains(node))
						{
							pathAdded = true;
							clusterOfPaths.add(path);
							break;
						}
					}

					if (pathAdded)
					{
						break;
					}
				}

				if (pathAdded)
				{
					break;
				}
			}

			if (!pathAdded)
			{
				final ArrayList<ArrayList<Node>> newCluster = new ArrayList<>();
				newCluster.add(path);
				pathsClusters.add(newCluster);
			}
		}

		for (ArrayList<ArrayList<Node>> cluster : pathsClusters)
		{
			final DependencyGraph dependencyGraph = Paths2DependencyGraph.transform(cluster);
			dependencyGraphs.add(dependencyGraph);
		}

		return dependencyGraphs;
	}

	private ArrayList<ArrayList<Node>> computePathsBefore(final ArrayList<Node> group,
														  final ArrayList<ArrayList<Node>> paths)
	{
		final ArrayList<ArrayList<Node>> pathsBefore = new ArrayList<>();

		for (ArrayList<Node> path : paths)
		{
			for (Node node : group)
			{
				final int index = path.indexOf(node);

				if (index != -1)
				{
					final ArrayList<Node> cutPath = this.cutPathAfter(path, node);
					cutPath.remove(cutPath.size() - 1);
					pathsBefore.add(cutPath);
				}
			}
		}

		return pathsBefore;
	}

	private ArrayList<ArrayList<Node>> computePathsAfter(final ArrayList<Node> group,
														 final ArrayList<ArrayList<Node>> paths)
	{
		final ArrayList<ArrayList<Node>> pathsAfter = new ArrayList<>();

		for (ArrayList<Node> path : paths)
		{
			for (Node node : group)
			{
				final int index = path.indexOf(node);

				if (index != -1)
				{
					final ArrayList<Node> cutPath = this.cutPathBefore(path, node);
					pathsAfter.add(cutPath);
				}
			}
		}

		return pathsAfter;
	}

	private ArrayList<ArrayList<Node>> findPathsSynchronizedBy(final Node syncPoint,
															   final ArrayList<ArrayList<Node>> allPaths)
	{
		final ArrayList<ArrayList<Node>> pathsSynchronized = new ArrayList<>();

		for (ArrayList<Node> path : allPaths)
		{
			if (path.contains(syncPoint))
			{
				final ArrayList<Node> cutPath = this.cutPathAfter(path, syncPoint);
				cutPath.remove(cutPath.size() - 1);
				pathsSynchronized.add(cutPath);
			}
		}

		return pathsSynchronized;
	}

	private ArrayList<ArrayList<Node>> isolateSharedNodes(final ArrayList<Node> sharedNodes)
	{
		final ArrayList<ArrayList<Node>> isolatedNodes = new ArrayList<>();
		ArrayList<Node> currentList = new ArrayList<>();
		currentList.add(sharedNodes.get(0));

		for (int i = 1; i < sharedNodes.size(); i++)
		{
			final Node lastNode = currentList.get(currentList.size() - 1);
			final Node currentNode = sharedNodes.get(i);

			if (!lastNode.hasChild(currentNode))
			{
				isolatedNodes.add(new ArrayList<>(currentList));
				currentList.clear();
			}

			currentList.add(currentNode);
		}

		if (!currentList.isEmpty())
		{
			isolatedNodes.add(currentList);
		}

		return isolatedNodes;
	}

	private void computeAllPaths(final Node currentNode,
								 final ArrayList<Node> currentPath,
								 final ArrayList<ArrayList<Node>> allPaths)
	{
		currentPath.add(currentNode);

		final ArrayList<ArrayList<Node>> childPaths = new ArrayList<>();

		for (int i = 0; i < currentNode.childNodes().size(); i++)
		{
			if (i == 0)
			{
				childPaths.add(currentPath);
			}
			else
			{
				final ArrayList<Node> childPath = new ArrayList<>(currentPath);
				childPaths.add(childPath);
				allPaths.add(childPath);
			}
		}

		int i = 0;

		for (final Node child : currentNode.childNodes())
		{
			this.computeAllPaths(child, childPaths.get(i++), allPaths);
		}
	}

	private void findSynchronizationPoints(final Node currentNode,
										   final HashSet<Node> synchronizationPoints,
										   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		//A node is a synchronization node if it has at least two parents
		if (currentNode.parentNodes().size() >= 2)
		{
			synchronizationPoints.add(currentNode);
		}

		for (Node child : currentNode.childNodes())
		{
			this.findSynchronizationPoints(child, synchronizationPoints, visitedNodes);
		}
	}

	/**
	 * This function computes the intersection of all the lists given as argument.
	 *
	 * @param paths all the paths to intersect
	 * @return the intersection of all the paths
	 */
	private ArrayList<Node> computePathsIntersection(final ArrayList<ArrayList<Node>> paths)
	{
		final ArrayList<Node> intersection = new ArrayList<>();
		ArrayList<Node> previousList = new ArrayList<>(paths.get(0));

		for (int i = 1; i < paths.size(); i++)
		{
			final ArrayList<Node> currentList = paths.get(i);

			for (Node node : previousList)
			{
				if (currentList.contains(node))
				{
					intersection.add(node);
				}
			}

			previousList = new ArrayList<>(intersection);
			intersection.clear();
		}

		return previousList;
	}

	private HashSet<ArrayList<Node>> cutAllPathsAfter(final ArrayList<ArrayList<Node>> allPaths,
													  final Node node)
	{
		final HashSet<ArrayList<Node>> cutPaths = new HashSet<>();

		for (ArrayList<Node> path : allPaths)
		{
			final int index = path.indexOf(node);

			if (index != -1)
			{
				cutPaths.add(cutPathAfter(path, node));
			}
		}

		return cutPaths;
	}

	private HashSet<ArrayList<Node>> cutAllPathsBefore(final ArrayList<ArrayList<Node>> allPaths,
													   final Node node)
	{
		final HashSet<ArrayList<Node>> cutPaths = new HashSet<>();

		for (ArrayList<Node> path : allPaths)
		{
			final int index = path.indexOf(node);

			if (index != -1)
			{
				cutPaths.add(cutPathBefore(path, node));
			}
		}

		return cutPaths;
	}

	private ArrayList<Node> cutPathAfter(final ArrayList<Node> path,
										 final Node node)
	{
		final int index = path.indexOf(node);
		final ArrayList<Node> newPath = new ArrayList<>(path);

		int i = index + 1;

		while (i++ < path.size())
		{
			newPath.remove(index + 1);
		}

		return newPath;
	}

	private ArrayList<Node> cutPathBefore(final ArrayList<Node> path,
										  final Node node)
	{
		final int index = path.indexOf(node);
		final ArrayList<Node> newPath = new ArrayList<>(path);

		int i = 0;

		while (i++ < index)
		{
			newPath.remove(0);
		}

		return newPath;
	}
}
