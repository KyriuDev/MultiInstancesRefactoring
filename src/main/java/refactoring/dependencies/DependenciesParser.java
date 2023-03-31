package refactoring.dependencies;

import bpmn.types.process.SequenceFlow;
import other.Utils;
import bpmn.graph.Graph;
import bpmn.graph.GraphUtils;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import bpmn.types.process.Task;
import loops_management.Loop;
import loops_management.LoopFinder;
import refactoring.Cluster;
import refactoring.exceptions.BadDependencyException;
import other.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * In this class, choices (exclusive split gateways) and loops are considered as unbreakable and immutable.
 */

public class DependenciesParser
{
	public static final Node DUMMY_NODE = new Node(new Task("//?.DUMMY.;//", BpmnProcessType.TASK, -1));
	private final File dependenciesFile;
	private final Graph graph;
	private final LoopFinder loopFinder;

	private final Cluster mainCluster;
	private final HashMap<Node, EnhancedNode> tempNodes;
	private final HashSet<Dependency> dependencies;

	public DependenciesParser(final File dependenciesFile,
							  final Graph graph,
							  final LoopFinder loopFinder)
	{
		this.dependenciesFile = dependenciesFile;
		this.graph = graph;
		this.loopFinder = loopFinder;
		this.mainCluster = new Cluster();
		this.tempNodes = new HashMap<>();
		this.dependencies = null;
	}

	public DependenciesParser(final HashSet<Dependency> dependencies,
							  final Graph graph,
							  final LoopFinder loopFinder)
	{
		this.dependenciesFile = null;
		this.dependencies = dependencies;
		this.graph = graph;
		this.loopFinder = loopFinder;
		this.mainCluster = new Cluster();
		this.tempNodes = new HashMap<>();
	}

	public Cluster parseFromFile() throws BadDependencyException, FileNotFoundException
	{
		this.readAndStoreDependenciesFromFile();
		//System.out.println("Après read and store:\n" + this.mainCluster.showDependencyGraphs(0));
		this.finalizeClusters(this.mainCluster, this.graph.initialNode(), new HashSet<>());
		//System.out.println("Après finalize:\n" + this.mainCluster.showDependencyGraphs(0));
		this.tempNodes.clear();
		this.buildDependencyGraphs(this.mainCluster);
		//System.out.println("Après build:\n" + this.mainCluster.showDependencyGraphs(0));
		this.correctDependencies(this.mainCluster);
		System.out.println(this.mainCluster.showDependencyGraphs(0));
		System.out.println("Dependency graph to dependencies:\n\n");

		for (Dependency dependency : this.mainCluster.dependencyGraphs().get(0).toDependencySet())
		{
			System.out.println("(" + dependency.firstNode().bpmnObject().id() + "," + dependency.secondNode().bpmnObject().id() + ")");
		}

		return this.mainCluster;
	}

	public Cluster parseFromSet() throws BadDependencyException
	{
		for (Dependency dependency : this.dependencies)
		{
			this.validateAndStoreDependency(dependency.firstNode().bpmnObject().id(), dependency.secondNode().bpmnObject().id());
		}

		this.finalizeClusters(this.mainCluster, this.graph.initialNode(), new HashSet<>());
		//System.out.println("Après finalize:\n" + this.mainCluster.showDependencyGraphs(0));
		this.tempNodes.clear();
		this.buildDependencyGraphs(this.mainCluster);
		//System.out.println("Après build:\n" + this.mainCluster.showDependencyGraphs(0));
		this.correctDependencies(this.mainCluster);
		System.out.println(this.mainCluster.showDependencyGraphs(0));

		return this.mainCluster;
	}

	/**
	 * Should be called only after a call to 'parseDependencies()'
	 * @return the main cluster of the process
	 */
	public Cluster mainCluster()
	{
		return this.mainCluster;
	}

	//Private methods

	private void readAndStoreDependenciesFromFile() throws FileNotFoundException, BadDependencyException
	{
		final Scanner scanner = new Scanner(this.dependenciesFile);

		while (scanner.hasNextLine())
		{
			final String dependency = scanner.nextLine().trim();

			if (dependency.startsWith("("))
			{
				//Parse dependency
				try
				{
					this.parseDependency(dependency);
				}
				catch (BadDependencyException e)
				{
					scanner.close();
					throw e;
				}
			}
		}

		scanner.close();
	}

	//TODO If needed, modify to handle activity IDs containing parenthesis/comas
	private void parseDependency(final String dependency) throws BadDependencyException
	{
		final int leftParenthesis = dependency.indexOf('(');
		final int rightParenthesis = dependency.indexOf(')');
		final int coma = dependency.indexOf(',');

		if (rightParenthesis == -1
			|| coma == -1)
		{
			throw new IllegalStateException("Dependency |" + dependency + "| has wrong format.");
		}

		final String activity1 = dependency.substring(leftParenthesis + 1, coma).trim();
		final String activity2 = dependency.substring(coma + 1, rightParenthesis).trim();

		if (activity1.equals(activity2))
		{
			//TODO Maybe just ignore them?
			System.out.println("Dependency (" + activity1 + "," + activity2 + ") was not validated!");
			throw new BadDependencyException("Task |" + activity1 + "| and |" + activity2 + "| can not be " +
					"constrained to each other because they are identical.");
		}

		this.validateAndStoreDependency(activity1, activity2);
	}

	/**
	 * A dependency between two tasks is validated if and only if both tasks are found
	 * in the current process (trivial), and if both tasks belong to the same executable
	 * portion of the process.
	 * By this, we mean that:
	 * 		- A task belonging to a choice can not depend on/be dependent of a task not belonging to this choice
	 * 		- A task belonging to the non-necessarily executed part of a loop can not depend on/be dependent of a task
	 * 				not belonging to his part of the loop.
	 * Simply, we refuse dependencies between necessarily executed tasks and non-necessarily executed tasks
	 *
	 * @param activity1 the task that should be executed before activity2
	 * @param activity2 the task that should be executed after activity1
	 */
	private void validateAndStoreDependency(final String activity1,
											final String activity2) throws BadDependencyException
	{
		final ArrayList<ArrayList<Node>> activity1RestrictiveNodes = new ArrayList<>();
		final ArrayList<ArrayList<Node>> activity2RestrictiveNodes = new ArrayList<>();
		final ArrayList<Node> initialRestrictiveNodes1 = new ArrayList<>();
		final ArrayList<Node> initialRestrictiveNodes2 = new ArrayList<>();
		activity1RestrictiveNodes.add(initialRestrictiveNodes1);
		activity2RestrictiveNodes.add(initialRestrictiveNodes2);
		this.findRestrictiveNodesOf(activity1, this.graph.initialNode(), initialRestrictiveNodes1, activity1RestrictiveNodes, new HashSet<>());
		this.findRestrictiveNodesOf(activity2, this.graph.initialNode(), initialRestrictiveNodes2, activity2RestrictiveNodes, new HashSet<>());
		final ArrayList<Node> finalActivity1RestrictiveNodes = this.finalizeRestrictiveNodes(activity1RestrictiveNodes);
		final ArrayList<Node> finalActivity2RestrictiveNodes = this.finalizeRestrictiveNodes(activity2RestrictiveNodes);

		/*if (!this.validateRestrictiveNodes(activity1RestrictiveNode, activity2RestrictiveNode))
		{
			System.out.println("Dependency (" + activity1 + "," + activity2 + ") was not validated!");
			throw new BadDependencyException("Task |" + activity1 + "| and |" + activity2 + "| can not be " +
					"constrained to each other because they do not belong to the same non-necessarily executed part" +
					" of the process.");
		}
		else
		{
			System.out.println("Dependency (" + activity1 + "," + activity2 + ") validated.");
		}*/

		this.storeDependencies(activity1, activity2, finalActivity1RestrictiveNodes, finalActivity2RestrictiveNodes);
	}

	private ArrayList<Node> finalizeRestrictiveNodes(final ArrayList<ArrayList<Node>> restrictiveNodes)
	{
		ArrayList<Node> finalRestrictiveNodes = null;

		for (ArrayList<Node> currentRestrictiveNodes : restrictiveNodes)
		{
			if (!currentRestrictiveNodes.isEmpty())
			{
				if (finalRestrictiveNodes != null)
				{
					throw new IllegalStateException();
				}

				finalRestrictiveNodes = currentRestrictiveNodes;
			}
		}

		return finalRestrictiveNodes;
	}

	/**
	 * This method is used to find the restrictive node (if appliable) of a given task.
	 *
	 * @param activity the activity for which we want to find the restrictive node
	 * @param currentRestrictiveNodes the list of restrictive nodes already found
	 * @param currentNode the current node being processed
	 * @param visitedNodes the set of already visited nodes (recursion breaker)
	 * @return null if the given task was not found in the process, DUMMY_NODE if the task is not constrained,
	 * 			or the latest restrictive node found otherwise.
	 */
	private Node findRestrictiveNodeOf(final String activity,
									   final ArrayList<Node> currentRestrictiveNodes,
									   final Node currentNode,
									   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		final ArrayList<Pair<Node, ArrayList<Node>>> nextNodesAndRestrictiveNodes = new ArrayList<>();

		if (currentNode.bpmnObject().id().equals(activity))
		{
			//Current node is our activity
			return currentRestrictiveNodes.isEmpty() ? DUMMY_NODE : currentRestrictiveNodes.get(currentRestrictiveNodes.size() - 1);
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
						//Exit point of a loop: all paths returning to the loop have a new constrained node: the loop
						for (Node child : currentNode.childNodes())
						{
							final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);

							if (innerLoop.hasNode(child))
							{
								//Child flow goes back to the loop --> current part is constrained
								newRestrictiveNodes.add(innerLoop.entryPoint());
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

					for (Node child : currentNode.childNodes())
					{
						final ArrayList<Node> newRestrictiveNodes = new ArrayList<>(currentRestrictiveNodes);

						if (!innerLoop.entryPoint().equals(currentNode))
						{
							//End of choice inside the loop
							newRestrictiveNodes.remove(newRestrictiveNodes.size() - 1);
						}

						nextNodesAndRestrictiveNodes.add(new Pair<>(child, newRestrictiveNodes));
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

		for (Pair<Node, ArrayList<Node>> nextNodeAndRestrictiveNode : nextNodesAndRestrictiveNodes)
		{
			final Node restrictiveNode = this.findRestrictiveNodeOf(activity, nextNodeAndRestrictiveNode.second(), nextNodeAndRestrictiveNode.first(), visitedNodes);

			if (restrictiveNode != null)
			{
				return restrictiveNode;
			}
		}

		return null;
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
			return;
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

	/**
	 * This method verifies that two tasks can effectively be dependent.
	 * This is the case if both are not constrained, or if both are constrained to the same node.
	 *
	 * @param firstRestrictiveNode the restrictive node of the first task
	 * @param secondRestrictiveNode the restrictive node of the second task
	 * @return true if the dependency is valid, false otherwise
	 */
	private boolean validateRestrictiveNodes(final Node firstRestrictiveNode,
											 final Node secondRestrictiveNode)
	{
		if (firstRestrictiveNode.equals(DUMMY_NODE)
			&& secondRestrictiveNode.equals(DUMMY_NODE))
		{
			//Both tasks are not constrained: good
			return true;
		}
		else if (firstRestrictiveNode.equals(DUMMY_NODE)
			|| secondRestrictiveNode.equals(DUMMY_NODE))
		{
			//One task is constrained but not the other one: bad
			return false;
		}
		else
		{
			//Both tasks are constrained: restrictive nodes should be the same
			return firstRestrictiveNode.equals(secondRestrictiveNode);
		}
	}

	/**
	 * This method aims at storing all the dependencies into their corresponding cluster.
	 * As we may have nested constructions (loop/choice), dependencies belonging to this kind
	 * of constructs are stored in the global 'tempNodes' map, which will be processed in a second time.
	 *
	 * @param activity1 the task on which depends task2
	 * @param activity2 the task depending on task1
	 * @param activity1RestrictiveNodes the list of restrictive nodes of activity1
	 * @param activity2RestrictiveNodes the list of restrictive nodes of activity2
	 */
	private void storeDependencies(final String activity1,
								   final String activity2,
								   final ArrayList<Node> activity1RestrictiveNodes,
								   final ArrayList<Node> activity2RestrictiveNodes) throws BadDependencyException
	{
		final Node activity1InGraph = this.graph.getNodeFromID(activity1);
		final Node activity2InGraph = this.graph.getNodeFromID(activity2);

		if (activity1InGraph == null || activity2InGraph == null)
		{
			throw new IllegalStateException("Either |" + activity1 + "| or |" + activity2 + "| was not found in the" +
					"BPMN process.");
		}

		if (activity1RestrictiveNodes != null
			&& activity2RestrictiveNodes != null)
		{
			//Both tasks have restrictive nodes
			if (activity1RestrictiveNodes.get(0).equals(activity2RestrictiveNodes.get(0)))
			{
				//One restrictive node is inside the other
				if (activity1RestrictiveNodes.size() == activity2RestrictiveNodes.size()
					&& activity1RestrictiveNodes.get(0).equals(activity2RestrictiveNodes.get(0)))
				{
					//Both tasks have exactly the same restrictive nodes -> check whether it makes sense or not
					final Node restrictiveNode = activity1RestrictiveNodes.get(activity1RestrictiveNodes.size() - 1);

					if (this.loopFinder.nodeIsInLoop(restrictiveNode))
					{
						final Loop innerLoop = this.loopFinder.findInnerLoopOf(restrictiveNode);

						if (innerLoop.entryPoint().equals(restrictiveNode))
						{
							final Loop copiedLoop = innerLoop.copy();
							final Node entryNode = copiedLoop.entryPoint();
							entryNode.parentNodes().iterator().next().removeChildren();
							entryNode.removeParents();
							final Node exitNode = copiedLoop.exitPoint().childNodes().iterator().next();
							exitNode.removeParents();
							copiedLoop.exitPoint().removeChildren();

							final boolean goContainsTask1 = entryNode.hasSuccessor(activity1InGraph);
							final boolean goContainsTask2 = entryNode.hasSuccessor(activity2InGraph);

							if ((goContainsTask1 && goContainsTask2)
								|| (!goContainsTask1 && !goContainsTask2))
							{
								final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
								final EnhancedLoop loopNode = (EnhancedLoop) this.tempNodes.computeIfAbsent(innerLoop.entryPoint(), e -> new EnhancedLoop(innerLoop.entryPoint()));

								//Add elements to loop
								if (goContainsTask1)
								{
									loopNode.entryToExitCluster().addDependency(dependency);
								}
								else
								{
									loopNode.exitToEntryCluster().addDependency(dependency);
								}
							}
							else if (goContainsTask2)
							{
								throw new BadDependencyException("Task |" + activity2 + "| can never be executed before task |" + activity1 + "|.");
							}
							/*else
							{
								//The dependency is necessarily respected
							}*/
						}
						else
						{
							final Node choice = restrictiveNode.parentNodes().iterator().next();
							final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(choice, e -> new EnhancedChoice(choice));
							final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
							enhancedChoice.addDependencyToClusterWithKey(restrictiveNode, dependency);
						}
					}
					else
					{
						final Node choice = restrictiveNode.parentNodes().iterator().next();
						final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(choice, e -> new EnhancedChoice(choice));
						final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
						enhancedChoice.addDependencyToClusterWithKey(restrictiveNode, dependency);
					}
				}
				else
				{
					final Node lastRestrictiveNode1 = activity1RestrictiveNodes.get(activity1RestrictiveNodes.size() - 1);
					final Node lastRestrictiveNode2 = activity2RestrictiveNodes.get(activity2RestrictiveNodes.size() - 1);

					if (lastRestrictiveNode1.parentNodes().iterator().next().equals(lastRestrictiveNode2.parentNodes().iterator().next()))
					{
						throw new BadDependencyException("Task |" + activity1 + "| and task |" + activity2 + "| are in two different paths of the same choice.");
					}

					int firstNonCommonRestrictiveNodeIndex = 1;

					for (int i = 1; i < Math.min(activity1RestrictiveNodes.size(), activity2RestrictiveNodes.size()); i++)
					{
						final Node currentRestrictiveNode1 = activity1RestrictiveNodes.get(i);
						final Node currentRestrictiveNode2 = activity2RestrictiveNodes.get(i);

						if (currentRestrictiveNode1.equals(currentRestrictiveNode2))
						{
							firstNonCommonRestrictiveNodeIndex = i;
						}
						else
						{
							firstNonCommonRestrictiveNodeIndex += 1;
							break;
						}
					}

					final Node lastCommonRestrictiveNode = activity1RestrictiveNodes.get(firstNonCommonRestrictiveNodeIndex - 1);
					final Node nodeToAnalyze1;
					final Node nodeToAnalyze2;

					if (firstNonCommonRestrictiveNodeIndex >= activity1RestrictiveNodes.size())
					{
						nodeToAnalyze1 = activity1InGraph;
					}
					else
					{
						nodeToAnalyze1 = activity1RestrictiveNodes.get(firstNonCommonRestrictiveNodeIndex);
					}

					if (firstNonCommonRestrictiveNodeIndex >= activity2RestrictiveNodes.size())
					{
						nodeToAnalyze2 = activity2InGraph;
					}
					else
					{
						nodeToAnalyze2 = activity2RestrictiveNodes.get(firstNonCommonRestrictiveNodeIndex);
					}

					if (this.loopFinder.nodeIsInLoop(lastCommonRestrictiveNode))
					{
						final Loop innerLoop = this.loopFinder.findInnerLoopOf(lastCommonRestrictiveNode);

						if (innerLoop.entryPoint().equals(lastCommonRestrictiveNode))
						{
							final Loop copiedLoop = innerLoop.copy();
							final Node entryNode = copiedLoop.entryPoint();
							entryNode.parentNodes().iterator().next().removeChildren();
							entryNode.removeParents();
							final Node exitNode = copiedLoop.exitPoint().childNodes().iterator().next();
							exitNode.removeParents();
							copiedLoop.exitPoint().removeChildren();

							final boolean goContainsTask1 = entryNode.hasSuccessor(nodeToAnalyze1);
							final boolean goContainsTask2 = entryNode.hasSuccessor(nodeToAnalyze2);

							if ((goContainsTask1 && goContainsTask2)
									|| (!goContainsTask1 && !goContainsTask2))
							{
								final Dependency dependency = new Dependency(nodeToAnalyze1, nodeToAnalyze2);
								final EnhancedLoop loopNode = (EnhancedLoop) this.tempNodes.computeIfAbsent(innerLoop.entryPoint(), e -> new EnhancedLoop(innerLoop.entryPoint()));

								//Add elements to loop
								if (goContainsTask1)
								{
									loopNode.entryToExitCluster().addDependency(dependency);
								}
								else
								{
									loopNode.exitToEntryCluster().addDependency(dependency);
								}
							}
							else if (goContainsTask2)
							{
								throw new BadDependencyException("Task |" + activity2 + "| can never be executed before task |" + activity1 + "|.");
							}
							/*else
							{
								//The dependency is necessarily respected
							}*/
						}
						else
						{
							final Node finalNode1 = (nodeToAnalyze1.bpmnObject() instanceof SequenceFlow) ? nodeToAnalyze1.parentNodes().iterator().next() : nodeToAnalyze1;
							final Node finalNode2 = (nodeToAnalyze2.bpmnObject() instanceof SequenceFlow) ? nodeToAnalyze2.parentNodes().iterator().next() : nodeToAnalyze2;

							final Node choice = lastCommonRestrictiveNode.parentNodes().iterator().next();
							final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(choice, e -> new EnhancedChoice(choice));
							final Dependency dependency = new Dependency(finalNode1, finalNode2);
							enhancedChoice.addDependencyToClusterWithKey(lastCommonRestrictiveNode, dependency);
						}
					}
					else
					{
						final Node finalNode1 = (nodeToAnalyze1.bpmnObject() instanceof SequenceFlow) ? nodeToAnalyze1.parentNodes().iterator().next() : nodeToAnalyze1;
						final Node finalNode2 = (nodeToAnalyze2.bpmnObject() instanceof SequenceFlow) ? nodeToAnalyze2.parentNodes().iterator().next() : nodeToAnalyze2;

						final Node choice = lastCommonRestrictiveNode.parentNodes().iterator().next();
						final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(choice, e -> new EnhancedChoice(choice));
						final Dependency dependency = new Dependency(finalNode1, finalNode2);
						enhancedChoice.addDependencyToClusterWithKey(lastCommonRestrictiveNode, dependency);
					}
				}
			}
			else
			{
				final Node finalNode1 = (activity1RestrictiveNodes.get(0).bpmnObject() instanceof SequenceFlow) ? activity1RestrictiveNodes.get(0).parentNodes().iterator().next() : activity1RestrictiveNodes.get(0);
				final Node finalNode2 = (activity2RestrictiveNodes.get(0).bpmnObject() instanceof SequenceFlow) ? activity2RestrictiveNodes.get(0).parentNodes().iterator().next() : activity2RestrictiveNodes.get(0);

				//Both restrictive nodes are separated
				final Dependency dependency = new Dependency(finalNode1, finalNode2);
				this.mainCluster.addDependency(dependency);
			}
		}
		else if (activity1RestrictiveNodes != null)
		{
			//Task1 has a restrictive node
			final Node finalNode1 = (activity1RestrictiveNodes.get(0).bpmnObject() instanceof SequenceFlow) ? activity1RestrictiveNodes.get(0).parentNodes().iterator().next() : activity1RestrictiveNodes.get(0);
			final Dependency dependency = new Dependency(finalNode1, activity2InGraph);
			this.mainCluster.addDependency(dependency);
		}
		else if (activity2RestrictiveNodes != null)
		{
			//Task2 has a restrictive node
			final Node finalNode2 = (activity2RestrictiveNodes.get(0).bpmnObject() instanceof SequenceFlow) ? activity2RestrictiveNodes.get(0).parentNodes().iterator().next() : activity2RestrictiveNodes.get(0);
			final Dependency dependency = new Dependency(activity1InGraph, finalNode2);
			this.mainCluster.addDependency(dependency);
		}
		else
		{
			//Both tasks have no restrictive nodes
			final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
			this.mainCluster.addDependency(dependency);
		}
	}

	private void storeDependenciesV2(final String activity1,
									 final String activity2,
								   	 final Node activity1RestrictiveNode,
									 final Node activity2RestrictiveNode)
	{
		final Node activity1InGraph = this.graph.getNodeFromID(activity1);
		final Node activity2InGraph = this.graph.getNodeFromID(activity2);

		if (activity1RestrictiveNode.equals(DUMMY_NODE))
		{
			//Current tasks have no restrictive nodes. Still, they can be in the necessarily executed part of a loop.
			final boolean activity1IsInLoop = this.loopFinder.nodeIsInLoop(activity1InGraph);
			final boolean activity2IsInLoop = this.loopFinder.nodeIsInLoop(activity2InGraph);

			if (activity1IsInLoop
					&& activity2IsInLoop)
			{
				//Both tasks are in a loop
				final Loop activity1Loop = this.loopFinder.findOuterLoopOf(activity1);
				final Loop activity2Loop = this.loopFinder.findOuterLoopOf(activity2);

				if (activity1Loop.equals(activity2Loop))
				{
					//Both tasks are in the same loop
					//Create dependency
					final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);

					//Check if loop already exists. If not, create it
					final EnhancedLoop loopNode = (EnhancedLoop) this.tempNodes.computeIfAbsent(activity1Loop.entryPoint(), e -> new EnhancedLoop(activity1Loop.entryPoint()));

					//Add elements to loop
					loopNode.entryToExitCluster().addDependency(dependency);
				}
				else
				{
					//Tasks are in two different loops --> both loops are now dependent
					final Dependency dependency = new Dependency(activity1Loop.entryPoint(), activity2Loop.entryPoint());
					this.mainCluster.addDependency(dependency);
				}
			}
			else if (activity1IsInLoop)
			{
				//Task 1 is in loop
				final Loop activityLoop = this.loopFinder.findOuterLoopOf(activity1);
				final Dependency dependency = new Dependency(activityLoop.entryPoint(), activity2InGraph);
				this.mainCluster.addDependency(dependency);
			}
			else if (activity2IsInLoop)
			{
				//Task 2 is in loop
				final Loop activityLoop = this.loopFinder.findOuterLoopOf(activity2);
				final Dependency dependency = new Dependency(activity1InGraph, activityLoop.entryPoint());
				this.mainCluster.addDependency(dependency);
			}
			else
			{
				//Current tasks are not in loops
				final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
				this.mainCluster.addDependency(dependency);
			}
		}
		else
		{
			//Current tasks have restrictive nodes (same for both)
			if (this.loopFinder.nodeIsInLoop(activity1))
			{
				//Both are in the same portion of loop, but they can be in the necessarily executed part of a loop
				//that is inside a choice.
				if (this.loopFinder.nodeIsInLoop(activity1RestrictiveNode))
				{
					//They are in the non-necessarily executed part of a loop
					final Loop innerLoop = this.loopFinder.findInnerLoopOf(activity1RestrictiveNode);
					final EnhancedLoop enhancedLoop = (EnhancedLoop) this.tempNodes.computeIfAbsent(innerLoop.entryPoint(), e -> new EnhancedLoop(innerLoop.entryPoint()));
					final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
					enhancedLoop.exitToEntryCluster().addDependency(dependency);
				}
				else
				{
					//They are in the necessarily executed part of a loop that is inside a choice
					final Loop innerLoop = this.loopFinder.findInnerLoopOf(activity1);
					final EnhancedLoop enhancedLoop = (EnhancedLoop) this.tempNodes.computeIfAbsent(innerLoop.entryPoint(), e -> new EnhancedLoop(innerLoop.entryPoint()));
					final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
					enhancedLoop.entryToExitCluster().addDependency(dependency);
				}
			}
			else
			{
				//Both are in the same path of a choice
				final Node choice = activity1RestrictiveNode.parentNodes().iterator().next();
				final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(choice, e -> new EnhancedChoice(choice));
				final Dependency dependency = new Dependency(activity1InGraph, activity2InGraph);
				enhancedChoice.addDependencyToClusterWithKey(activity1RestrictiveNode, dependency);
			}
		}
	}

	/**
	 * This method is used to add all the tasks, choices and loops to the clusters
	 * and store properly the dependencies in the clusters
	 *
	 */
	private void finalizeClusters(final Cluster currentCluster,
								  final Node currentNode,
								  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		final ArrayList<Pair<Cluster, Node>> nextNodes = new ArrayList<>();

		if (currentNode.bpmnObject() instanceof Task)
		{
			//The current node is a task, add it to the current cluster
			currentCluster.addElement(new EnhancedNode(currentNode));
			nextNodes.add(new Pair<>(currentCluster, currentNode.childNodes().iterator().next()));
		}
		else if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			//The current node is an exclusive gateway
			if (((Gateway) currentNode.bpmnObject()).isSplitGateway())
			{
				if (this.loopFinder.nodeIsInLoop(currentNode))
				{
					//The current node is an exclusive split gateway in a loop
					final Loop loop = this.loopFinder.findInnerLoopOf(currentNode);

					if (loop.exitPoint().equals(currentNode))
					{
						//Exit node of the loop: pursue recursion on the path returning to the beginning of the loop
						final EnhancedLoop enhancedLoop = (EnhancedLoop) this.tempNodes.get(loop.entryPoint());

						if (enhancedLoop == null)
						{
							throw new IllegalStateException("Exit point of loop |" + currentNode.bpmnObject().id() +
									"| has no corresponding enhanced loop.");
						}

						if (currentNode.childNodes().size() != 2)
						{
							throw new IllegalStateException("Exit point of loop |" + currentNode.bpmnObject().id() +
									"| should have exactly 2 children.");
						}

						final double[] probas = new double[2];

						for (Node child : currentNode.childNodes())
						{
							if (loop.hasNode(child))
							{
								nextNodes.add(new Pair<>(enhancedLoop.exitToEntryCluster(), child));
								probas[0] = child.bpmnObject().probability();
							}
							else
							{
								probas[1] = child.bpmnObject().probability();
							}
						}

						//Set probability for looping cluster of the loop
						if (probas[0] > 1d
							|| probas[0] < 0d
							|| probas[1] > 1d
							|| probas[1] < 0d)
						{
							throw new IllegalStateException("Probability in loop |" + currentNode.bpmnObject().id() +
									"| is either higher than 1 or lower than 0.");
						}

						if (probas[0] != 1d)
						{
							enhancedLoop.exitToEntryCluster().setProbability(probas[0]);
						}
						else
						{
							if (probas[1] != 1d)
							{
								enhancedLoop.exitToEntryCluster().setProbability(1d - probas[1]);
							}
							else
							{
								enhancedLoop.exitToEntryCluster().setProbability(0.5d);
							}
						}

						if (nextNodes.size() != 1)
						{
							throw new IllegalStateException("Path returning to the initial node of the loop should be unique.");
						}
					}
					else
					{
						//Choice inside the loop: start recursion inside the choice + on merge gateway sequence flow child
						final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(currentNode, e -> new EnhancedChoice(currentNode));
						currentCluster.addElement(enhancedChoice);

						final Map<Cluster, Double> probas = new HashMap<>();

						//Add recursion inside the choice
						for (Node child : currentNode.childNodes())
						{
							final Cluster childCluster = enhancedChoice.getClusterFromKey(child);
							probas.put(childCluster, child.bpmnObject().probability());
							nextNodes.add(new Pair<>(childCluster, child));
						}

						//Manage probabilities
						final double sum = Utils.sumDoubles(probas.values());

						if (sum < 1d)
						{
							throw new IllegalStateException("Sum of probabilities of choice |" + currentNode.bpmnObject().id() + "| is lower than 1.");
						}
						else
						{
							if (sum != 1)
							{
								final Map<Cluster, Double> weightedPaths = new HashMap<>();
								final Map<Cluster, Double> nonWeightedPaths = new HashMap<>();

								for (Cluster cluster : probas.keySet())
								{
									final Double proba = probas.get(cluster);

									if (proba != 1d)
									{
										weightedPaths.put(cluster, proba);
									}
									else
									{
										nonWeightedPaths.put(cluster, 1d);
									}
								}

								if (Utils.sumDoubles(weightedPaths.values()) > 1d)
								{
									throw new IllegalStateException("Sum of already weighted paths of choice |" + currentNode.bpmnObject().id() + "| is greater than 1.");
								}

								final double leftProba = 1d - Utils.sumDoubles(weightedPaths.values());
								final double leftProbaPerPath = leftProba / (double) nonWeightedPaths.size();

								for (Cluster cluster : nonWeightedPaths.keySet())
								{
									probas.put(cluster, leftProbaPerPath);
								}
							}

							for (Cluster cluster : probas.keySet())
							{
								cluster.setProbability(probas.get(cluster));
							}
						}

						//Continue recursion after choice
						final Node correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(currentNode);
						nextNodes.add(new Pair<>(currentCluster, correspondingMergeGateway.childNodes().iterator().next()));
					}
				}
				else
				{
					//The current node is an exclusive split gateway outside a loop
					final EnhancedChoice enhancedChoice = (EnhancedChoice) this.tempNodes.computeIfAbsent(currentNode, e -> new EnhancedChoice(currentNode));
					currentCluster.addElement(enhancedChoice);

					final Map<Cluster, Double> probas = new HashMap<>();

					//Add recursion inside the choice
					for (Node child : currentNode.childNodes())
					{
						final Cluster childCluster = enhancedChoice.getClusterFromKey(child);
						probas.put(childCluster, child.bpmnObject().probability());
						nextNodes.add(new Pair<>(childCluster, child));
					}

					//Manage probabilities
					final double sum = Utils.sumDoubles(probas.values());

					if (sum < 1d)
					{
						throw new IllegalStateException("Sum of probabilities of choice |" + currentNode.bpmnObject().id() + "| is lower than 1.");
					}
					else
					{
						if (sum != 1)
						{
							final Map<Cluster, Double> weightedPaths = new HashMap<>();
							final Map<Cluster, Double> nonWeightedPaths = new HashMap<>();

							for (Cluster cluster : probas.keySet())
							{
								final Double proba = probas.get(cluster);

								if (proba != 1d)
								{
									weightedPaths.put(cluster, proba);
								}
								else
								{
									nonWeightedPaths.put(cluster, 1d);
								}
							}

							if (Utils.sumDoubles(weightedPaths.values()) > 1d)
							{
								throw new IllegalStateException("Sum of already weighted paths of choice |" + currentNode.bpmnObject().id() + "| is greater than 1.");
							}

							final double leftProba = 1d - Utils.sumDoubles(weightedPaths.values());
							final double leftProbaPerPath = leftProba / (double) nonWeightedPaths.size();

							for (Cluster cluster : nonWeightedPaths.keySet())
							{
								probas.put(cluster, leftProbaPerPath);
							}
						}

						for (Cluster cluster : probas.keySet())
						{
							cluster.setProbability(probas.get(cluster));
						}
					}

					//Continue recursion after choice
					final Node correspondingMergeGateway = GraphUtils.findCorrespondingMergeGateway(currentNode);
					nextNodes.add(new Pair<>(currentCluster, correspondingMergeGateway.childNodes().iterator().next()));
				}
			}
			else
			{
				if (this.loopFinder.nodeIsInLoop(currentNode))
				{
					//The current node is an exclusive merge gateway in a loop
					final Loop loop = this.loopFinder.findInnerLoopOf(currentNode);

					if (currentNode.equals(loop.entryPoint()))
					{
						//Starting point of a loop: add loop to current cluster + start recursion inside loop
						final EnhancedLoop enhancedLoop = (EnhancedLoop) this.tempNodes.computeIfAbsent(currentNode, e -> new EnhancedLoop(currentNode));
						currentCluster.addElement(enhancedLoop);

						//Recursion inside loop
						nextNodes.add(new Pair<>(enhancedLoop.entryToExitCluster(), currentNode.childNodes().iterator().next()));

						//Pursue recursion after loop
						final Node loopExitPointInGraph = this.graph.getNodeFromID(loop.exitPoint().bpmnObject().id());

						for (Node child : loopExitPointInGraph.childNodes())
						{
							if (!loop.hasNode(child))
							{
								nextNodes.add(new Pair<>(currentCluster, child));
							}
						}
					}
					else
					{
						//End of choice inside a loop: break recursion
						return;
					}
				}
				else
				{
					//The current node is an exclusive merge gateway outside a loop, i.e., the end of a choice: return
					return;
				}
			}
		}
		else
		{
			//The current node is not a task nor an exclusive gateway
			for (Node child : currentNode.childNodes())
			{
				nextNodes.add(new Pair<>(currentCluster, child));
			}
		}

		for (Pair<Cluster, Node> nodeAndCluster : nextNodes)
		{
			this.finalizeClusters(nodeAndCluster.first(), nodeAndCluster.second(), visitedNodes);
		}
	}

	/**
	 * This function generates the graphs of dependencies for each existing cluster and sub-cluster.
	 * For each dependency, we check whether it belongs to an already existing graph.
	 * If yes, we connect it. Otherwise, we create a new graph with this dependency.
	 *
	 * @throws BadDependencyException if adding the current dependency generates a loop in the dependency graph
	 */
	private void buildDependencyGraphs(final Cluster cluster) throws BadDependencyException
	{
		//System.out.println("Cluster contient " + cluster.dependencies().size() + " dependences.");

		if (!cluster.dependencies().isEmpty())
		{
			final ArrayList<DependencyGraph> dependencyGraphs = cluster.dependencyGraphs();
			final ArrayList<Dependency> dependencies = new ArrayList<>(cluster.dependencies());
			DependencyGraph dependencyGraph = new DependencyGraph();
			boolean modified = true;

			while (!dependencies.isEmpty())
			{
				if (!modified)
				{
					dependencyGraphs.add(dependencyGraph);
					dependencyGraph = new DependencyGraph();
				}

				modified = false;

				for (Iterator<Dependency> iterator = dependencies.iterator(); iterator.hasNext(); )
				{
					final Dependency dependency = iterator.next();
					final Node firstNode = new Node(dependency.firstNode().bpmnObject());
					final Node secondNode = new Node(dependency.secondNode().bpmnObject());

					if (dependencyGraph.hasNode(firstNode)
						&& dependencyGraph.hasNode(secondNode))
					{
						//Both nodes are already in the graph: connect them together and remove the second from the initial nodes
						final Node firstNodeInGraph = dependencyGraph.getNodeFromID(firstNode.bpmnObject().id());
						final Node secondNodeInGraph = dependencyGraph.getNodeFromID(secondNode.bpmnObject().id());
						firstNodeInGraph.addChild(secondNodeInGraph);
						secondNodeInGraph.addParent(firstNodeInGraph);

						if (secondNodeInGraph.isInLoop())
						{
							System.out.println("Dependency (" + firstNode.bpmnObject().id() + "," +
									secondNode.bpmnObject().id() + ") generates a loop in the dependency graph!");
							throw new BadDependencyException("Dependency (" + firstNode.bpmnObject().id() + "," +
									secondNode.bpmnObject().id() + ") generates a loop in the dependency graph!");
						}
						else
						{
							dependencyGraph.removeInitialNode(secondNodeInGraph);
						}

						modified = true;
						iterator.remove();
					}
					else if (dependencyGraph.hasNode(firstNode))
					{
						//First node is already in graph: connect the second one to it
						final Node firstNodeInGraph = dependencyGraph.getNodeFromID(firstNode.bpmnObject().id());
						firstNodeInGraph.addChild(secondNode);
						secondNode.addParent(firstNodeInGraph);

						modified = true;
						iterator.remove();
					}
					else if (dependencyGraph.hasNode(secondNode))
					{
						//Second node is already in graph: connect the first node to it, remove it from the initial nodes, and mark the first node as initial node
						final Node secondNodeInGraph = dependencyGraph.getNodeFromID(secondNode.bpmnObject().id());
						secondNodeInGraph.addParent(firstNode);
						firstNode.addChild(secondNodeInGraph);
						dependencyGraph.removeInitialNode(secondNodeInGraph);
						dependencyGraph.addInitialNode(firstNode);

						modified = true;
						iterator.remove();
					}
					else
					{
						if (dependencyGraph.isEmpty())
						{
							dependencyGraph.addInitialNode(firstNode);
							firstNode.addChild(secondNode);
							secondNode.addParent(firstNode);

							modified = true;
							iterator.remove();
						}
					}
				}
			}

			if (!dependencyGraph.isEmpty())
			{
				dependencyGraphs.add(dependencyGraph);
			}
		}

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.buildDependencyGraphs(choiceCluster);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				this.buildDependencyGraphs(enhancedLoop.entryToExitCluster());
				this.buildDependencyGraphs(enhancedLoop.exitToEntryCluster());
			}
		}
	}

	/**
	 * This function is used to remove from the list of dependencies all the unnecessary dependencies.
	 * By unnecessary, we mean the ones that are not part of the longest path between two dependencies.
	 * For example, dependencies [(A,B), (B,C), (A,C)] will become [(A,B), (B,C)] after the call to
	 * this function because path A -> C is shorter than path A -> B -> C.
	 * 1) Compute all unnecessary dependencies
	 * 2) Remove them from the list of dependencies and clear dependency graphs
	 * 3) Build dependency graphs from cleaned dependencies
	 *
	 * @param cluster the cluster to consider
	 */
	private void correctDependencies(final Cluster cluster) throws BadDependencyException
	{
		this.correctDependenciesRec(cluster);
		this.buildDependencyGraphs(cluster);
		this.finalizeDependencies(cluster);
	}

	private void correctDependenciesRec(final Cluster cluster)
	{
		final ArrayList<Dependency> dependenciesToRemove = new ArrayList<>();

		for (DependencyGraph dependencyGraph : cluster.dependencyGraphs())
		{
			final HashSet<Node> visitedNodes = new HashSet<>();

			for (Node startNode : dependencyGraph.initialNodes())
			{
				this.computeUselessDependencies(startNode, visitedNodes, dependenciesToRemove);
			}
		}

		dependenciesToRemove.forEach(cluster.dependencies()::remove);
		cluster.dependencyGraphs().clear();

		for (EnhancedNode enhancedNode : cluster.elements())
		{
			if (enhancedNode.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

				for (Cluster choiceCluster : enhancedChoice.clusters())
				{
					this.correctDependenciesRec(choiceCluster);
				}
			}
			else if (enhancedNode.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;

				this.correctDependenciesRec(enhancedLoop.entryToExitCluster());
				this.correctDependenciesRec(enhancedLoop.exitToEntryCluster());
			}
		}
	}

	private void computeUselessDependencies(final Node currentNode,
											final HashSet<Node> visitedNodes,
											final ArrayList<Dependency> dependenciesToRemove)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		for (Node pivotChild : currentNode.childNodes())
		{
			for (Node otherChild : currentNode.childNodes())
			{
				if (!pivotChild.equals(otherChild))
				{
					if (otherChild.hasSuccessor(pivotChild))
					{
						dependenciesToRemove.add(new Dependency(currentNode, pivotChild));
					}
				}
			}
		}

		for (Node child : currentNode.childNodes())
		{
			this.computeUselessDependencies(child, visitedNodes, dependenciesToRemove);
		}
	}

	private void finalizeDependencies(final Cluster cluster)
	{
		for (Dependency dependency : cluster.dependencies())
		{
			for (DependencyGraph dependencyGraph : cluster.dependencyGraphs())
			{
				if (dependencyGraph.hasNode(dependency.firstNode())
					|| dependencyGraph.hasNode(dependency.secondNode()))
				{
					final HashSet<Dependency> dependencySet = cluster.graphsWithDependencies().computeIfAbsent(dependencyGraph, h -> new HashSet<>());
					dependencySet.add(dependency);
				}
			}
		}

		//cluster.dependencies().clear();
		//cluster.dependencyGraphs().clear();

		for (EnhancedNode node : cluster.elements())
		{
			if (node.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice choice = (EnhancedChoice) node;

				for (Cluster subCluster : choice.clusters())
				{
					this.finalizeDependencies(subCluster);
				}
			}
			else if (node.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop loop = (EnhancedLoop) node;
				this.finalizeDependencies(loop.entryToExitCluster());
				this.finalizeDependencies(loop.exitToEntryCluster());
			}
		}
	}
}
