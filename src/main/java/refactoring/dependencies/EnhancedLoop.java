package refactoring.dependencies;

import bpmn.graph.Node;
import refactoring.Cluster;

public class EnhancedLoop extends EnhancedNode
{
	private final Cluster entryToExitCluster; //Cluster of elements between entry point and exit point of the loop
	private final Cluster exitToEntryCluster; //Cluster of elements between exit point and entry point of the loop

	public EnhancedLoop(final Node node)
	{
		super(node);
		this.entryToExitCluster = new Cluster();
		this.exitToEntryCluster = new Cluster();
	}

	@Override
	public String stringify(final int depth)
	{
		return "	".repeat(depth) +
				"Loop |" +
				this.node.bpmnObject().id() +
				"| has paths:\n" +
				"	".repeat(depth + 1) +
				"- Path from entry to exit:\n" +
				entryToExitCluster.stringify(depth + 2) +
				"\n" +
				"	".repeat(depth + 1) +
				"- Path from exit to entry:\n" +
				this.exitToEntryCluster.stringify(depth + 2) +
				"\n";
	}

	@Override
	public EnhancedType type()
	{
		return EnhancedType.LOOP;
	}

	public Cluster entryToExitCluster()
	{
		return this.entryToExitCluster;
	}

	public Cluster exitToEntryCluster()
	{
		return this.exitToEntryCluster;
	}
}
