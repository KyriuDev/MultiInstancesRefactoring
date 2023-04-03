package refactoring.dependencies;

import other.Utils;
import bpmn.graph.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DependencyGraph
{
	private final HashSet<Node> initialNodes;
	private final String id;

	public DependencyGraph()
	{
		this.initialNodes = new HashSet<>();
		this.id = Utils.generateRandomIdentifier(30);
	}

	//Overrides

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof DependencyGraph))
		{
			return false;
		}

		return ((DependencyGraph) o).id.equals(this.id);
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

	//Public methods

	public String stringify(final int depth)
	{
		final StringBuilder builder = new StringBuilder();

		for (Node initialNode : this.initialNodes())
		{
			builder.append(initialNode.stringify(depth, new ArrayList<>()))
					.append("\n\n");
		}

		return builder.toString();
	}

	public void addInitialNode(final Node node)
	{
		this.initialNodes.add(node);
	}

	public void removeInitialNode(final Node node)
	{
		this.initialNodes.remove(node);
	}

	public Node getNodeFromID(final String id)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			final Node node = this.getNodeFromID(id, initialNode, visitedNodes);

			if (node != null)
			{
				return node;
			}
		}

		return null;
	}

	public boolean hasNode(final Node node)
	{
		return this.hasNode(node.bpmnObject().id());
	}

	public boolean hasNode(final String id)
	{
		return this.getNodeFromID(id) != null;
	}

	public boolean isEmpty()
	{
		return this.initialNodes.isEmpty();
	}

	public HashSet<Node> initialNodes()
	{
		return this.initialNodes;
	}

	public HashSet<Dependency> toDependencySet()
	{
		final HashSet<Dependency> dependencies = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			this.transformIntoDependencySet(initialNode, dependencies);
		}

		return dependencies;
	}

	public HashSet<Node> toSet()
	{
		final HashSet<Node> nodes = new HashSet<>();

		for (Node initialNode : initialNodes)
		{
			this.transformIntoSet(initialNode, nodes);
		}

		return nodes;
	}

	public DependencyGraph copy()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final HashSet<Node> visitedNodes = new HashSet<>();
		final HashMap<Node, Node> correspondences = new HashMap<>();

		for (Node initialNode : this.initialNodes)
		{
			final Node newInitialNode = initialNode.weakCopy();
			dependencyGraph.addInitialNode(newInitialNode);
			this.copy(initialNode, newInitialNode, visitedNodes, correspondences);
		}

		return dependencyGraph;
	}

	public DependencyGraph cutAfter(final Node node)
	{
		final Node correspondingNode = this.getNodeFromID(node.bpmnObject().id());

		for (Node child : correspondingNode.childNodes())
		{
			child.removeParent(correspondingNode);
		}

		correspondingNode.removeChildren();

		return this;
	}

	public DependencyGraph cutBefore(final Node node)
	{
		final Node correspondingNode = this.getNodeFromID(node.bpmnObject().id());

		for (Node parent : correspondingNode.parentNodes())
		{
			parent.removeChildren(correspondingNode);
		}

		correspondingNode.removeParents();

		return this;
	}

	public DependencyGraph cutBetween(final Node startNode,
									  final Node endNode)
	{
		final HashSet<Node> visitedNodes = new HashSet<>();

		for (Node initialNode : this.initialNodes)
		{
			this.cutBetween(startNode, endNode, initialNode, false, visitedNodes);
		}

		return this;
	}

	public void findPathsBetween(final Node currentNode,
								 final Node endNode,
								 final ArrayList<Node> currentPath,
								 final ArrayList<ArrayList<Node>> allPaths)
	{

		if (currentNode.equals(endNode))
		{
			return;
		}

		currentPath.add(currentNode);

		ArrayList<ArrayList<Node>> tempPaths = new ArrayList<>();
		tempPaths.add(currentPath);

		for (int i = 1; i < currentNode.childNodes().size(); i++)
		{
			final ArrayList<Node> tempPath = new ArrayList<>(currentPath);
			allPaths.add(tempPath);
			tempPaths.add(tempPath);
		}

		int i = 0;

		for (Node child : currentNode.childNodes())
		{
			this.findPathsBetween(child, endNode, tempPaths.get(i++), allPaths);
		}
	}

	//Private methods

	private void cutBetween(final Node startNode,
							final Node endNode,
							final Node currentNode,
							final boolean nodeCut,
							final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);
		boolean newNodeCut = false;

		if (startNode.equals(currentNode))
		{
			for (Node parent : currentNode.parentNodes())
			{
				parent.removeChildren(currentNode);
			}

			currentNode.removeParents();
			newNodeCut = true;

			if (nodeCut)
			{
				return;
			}
		}
		if (endNode.equals(currentNode))
		{
			for (Node child : currentNode.childNodes())
			{
				child.removeParent(endNode);
			}

			currentNode.removeChildren();

			if (nodeCut
				|| newNodeCut)
			{
				return;
			}

			newNodeCut = true;
		}

		for (Node child : currentNode.childNodes())
		{
			this.cutBetween(startNode, endNode, child, newNodeCut, visitedNodes);
		}
	}

	private void cutBefore(final Node nodeToReach,
						   final Node currentNode,
						   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(nodeToReach))
		{
			for (Node parent : currentNode.parentNodes())
			{
				parent.removeChildren(currentNode);
			}

			currentNode.removeParents();
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.cutBefore(nodeToReach, child, visitedNodes);
		}
	}

	private void cutAfter(final Node nodeToReach,
						  final Node currentNode,
						  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.equals(nodeToReach))
		{
			for (Node child : currentNode.childNodes())
			{
				child.removeParent(currentNode);
			}

			currentNode.removeChildren();
			return;
		}

		for (Node child : currentNode.childNodes())
		{
			this.cutAfter(nodeToReach, child, visitedNodes);
		}
	}

	private void copy(final Node oldNode,
					  final Node newNode,
					  final HashSet<Node> visitedNodes,
					  final HashMap<Node, Node> correspondences)
	{
		if (visitedNodes.contains(oldNode))
		{
			return;
		}

		visitedNodes.add(oldNode);

		for (Node oldChild : oldNode.childNodes())
		{
			final Node newChild = correspondences.computeIfAbsent(oldChild, o -> oldChild.weakCopy());
			newNode.addChild(newChild);
			newChild.addParent(newNode);

			this.copy(oldChild, newChild, visitedNodes, correspondences);
		}
	}

	private void transformIntoSet(final Node currentNode,
								  final HashSet<Node> nodes)
	{
		if (nodes.contains(currentNode))
		{
			return;
		}

		nodes.add(currentNode);

		for (Node child : currentNode.childNodes())
		{
			this.transformIntoSet(child, nodes);
		}
	}

	private void transformIntoDependencySet(final Node currentNode,
											final HashSet<Dependency> dependencies)
	{
		for (Node child : currentNode.childNodes())
		{
			final Dependency dependency = new Dependency(currentNode, child);
			dependencies.add(dependency);

			this.transformIntoDependencySet(child, dependencies);
		}
	}

	private Node getNodeFromID(final String id,
							   final Node currentNode,
							   final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().id().equals(id))
		{
			return currentNode;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node node = this.getNodeFromID(id, child, visitedNodes);

			if (node != null)
			{
				return node;
			}
		}

		return null;
	}
}
