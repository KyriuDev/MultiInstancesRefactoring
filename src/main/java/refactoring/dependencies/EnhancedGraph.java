package refactoring.dependencies;

import bpmn.graph.Graph;
import bpmn.graph.Node;

import java.util.ArrayList;

public class EnhancedGraph extends Graph
{
	private Node endNode;

	public EnhancedGraph(final Node firstNode)
	{
		super(firstNode);
	}

	public void setEndNode(final Node endNode)
	{
		this.endNode = endNode;
	}

	public Node endNode()
	{
		return this.endNode;
	}

	@Override
	public String stringify()
	{
		return this.endNode().stringifyRevert(0, new ArrayList<>());
	}

	@Override
	@Deprecated
	public Node lastNode()
	{
		throw new IllegalStateException("Should never be used in enhanced graph.");
	}
}
