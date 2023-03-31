package bpmn.graph;

import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;

import java.util.HashSet;

public class GraphUtils
{
	private GraphUtils()
	{

	}

	public static Node findCorrespondingMergeGateway(final Node splitGateway)
	{
		if (!(splitGateway.bpmnObject() instanceof Gateway)
			|| ((Gateway) splitGateway.bpmnObject()).isMergeGateway())
		{
			throw new IllegalStateException("Node |" + splitGateway.bpmnObject().id() + "| is not a gateway.");
		}

		final Node correspondingMergeGateway = GraphUtils.findCorrespondingMergeGatewayRec(splitGateway, splitGateway, new HashSet<>());

		if (correspondingMergeGateway == null)
		{
			throw new IllegalStateException("No merge gateway found for gateway |" + splitGateway.bpmnObject().id() + "|.");
		}

		return correspondingMergeGateway;
	}

	public static Node findCorrespondingSplitGateway(final Node mergeGateway)
	{
		if (!(mergeGateway.bpmnObject() instanceof Gateway))
		{
			throw new IllegalStateException("Node |" + mergeGateway.bpmnObject().id() + "| is not a gateway.");
		}

		final Node correspondingSplitGateway = GraphUtils.findCorrespondingSplitGatewayRec(mergeGateway, mergeGateway, new HashSet<>());

		if (correspondingSplitGateway == null)
		{
			throw new IllegalStateException("No split gateway found for gateway |" + mergeGateway.bpmnObject().id() + "|.");
		}

		return correspondingSplitGateway;
	}

	/**
	 * This function assumes that the given loop entry node is part of a loop
	 * that has previously been extracted from its process, meaning that both
	 * the entry point and the exit point of the loop are exclusive gateway
	 * with 1 parent and 1 child.
	 *
	 * @param loopEntryNode the entry point of the loop
	 * @return the exit point of the loop
	 */
	public static Node findLoopExitNode(final Node loopEntryNode)
	{
		if (!(loopEntryNode.bpmnObject() instanceof Gateway)
			|| ((Gateway) loopEntryNode.bpmnObject()).isSplitGateway())
		{
			throw new IllegalStateException("Node |" + loopEntryNode.bpmnObject().id() + "| is not an exclusive split" +
					" gateway.");
		}

		final Node loopExitNode = GraphUtils.findLoopExitNodeRec(loopEntryNode, loopEntryNode, new HashSet<>());

		if (loopExitNode == null)
		{
			throw new IllegalStateException("No exit node found for the loop starting with node |" +
					loopEntryNode.bpmnObject().id() + "|.");
		}

		return loopExitNode;
	}

	//Private methods

	private static Node findCorrespondingMergeGatewayRec(final Node splitGateway,
														 final Node currentNode,
														 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == splitGateway.bpmnObject().type())
		{
			System.out.println("Same type merge gateway found: " + currentNode.bpmnObject().id());
		}

		if (currentNode.bpmnObject().type() == splitGateway.bpmnObject().type()
			&& ((Gateway) currentNode.bpmnObject()).isMergeGateway())
		{
			System.out.println("Candidate merge gateway found: " + currentNode.bpmnObject().id());
			boolean isAncestor = true;

			for (Node child : splitGateway.childNodes())
			{
				if (!child.isAncestorOf(currentNode))
				{
					isAncestor = false;
					break;
				}
			}

			if (isAncestor)
			{
				return currentNode;
			}
		}

		for (Node child : currentNode.childNodes())
		{
			final Node mergeGateway = GraphUtils.findCorrespondingMergeGatewayRec(splitGateway, child, visitedNodes);

			if (mergeGateway != null)
			{
				return mergeGateway;
			}
		}

		return null;
	}

	private static Node findCorrespondingSplitGatewayRec(final Node mergeGateway,
														 final Node currentNode,
														 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == mergeGateway.bpmnObject().type()
				&& ((Gateway) currentNode.bpmnObject()).isSplitGateway())
		{
			boolean isSuccessor = true;

			for (Node parent : mergeGateway.parentNodes())
			{
				if (!parent.isSuccessorOf(currentNode))
				{
					isSuccessor = false;
					break;
				}
			}

			if (isSuccessor)
			{
				return currentNode;
			}
		}

		for (Node parent : currentNode.parentNodes())
		{
			final Node splitGateway = GraphUtils.findCorrespondingSplitGatewayRec(mergeGateway, parent, visitedNodes);

			if (splitGateway != null)
			{
				return splitGateway;
			}
		}

		return null;
	}

	private static Node findLoopExitNodeRec(final Node loopEntryNode,
									 final Node currentNode,
									 final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
			&& currentNode.childNodes().size() == 1
			&& currentNode.parentNodes().size() == 1
			&& !currentNode.equals(loopEntryNode))
		{
			return currentNode;
		}

		for (Node child : currentNode.childNodes())
		{
			final Node loopExitNode = GraphUtils.findLoopExitNodeRec(loopEntryNode, child, visitedNodes);

			if (loopExitNode != null)
			{
				return loopExitNode;
			}
		}

		return null;
	}
}
