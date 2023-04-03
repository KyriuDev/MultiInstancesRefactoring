package refactoring.dependencies;

import bpmn.graph.Node;

public class EnhancedNode
{
	protected final Node node;

	public EnhancedNode(final Node node)
	{
		this.node = node;
	}

	//Overrides
	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof EnhancedNode))
		{
			return false;
		}

		return this.node.equals(((EnhancedNode) o).node);
	}

	@Override
	public int hashCode()
	{
		return this.node.hashCode();
	}

	//Public methods

	public EnhancedType type()
	{
		return EnhancedType.CLASSICAL;
	}

	public Node node()
	{
		return this.node;
	}

	public String stringify(final int depth)
	{
		return "	".repeat(depth) + "Node |" + this.node.bpmnObject().id() + "|.";
	}
}
