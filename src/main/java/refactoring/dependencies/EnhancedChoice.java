package refactoring.dependencies;

import bpmn.graph.Node;
import refactoring.Cluster;

import java.util.Collection;
import java.util.HashMap;

public class EnhancedChoice extends EnhancedNode
{
	private final HashMap<Node, Cluster> choicePaths;

	public EnhancedChoice(final Node node)
	{
		super(node);
		this.choicePaths = new HashMap<>();
	}

	@Override
	public String stringify(final int depth)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("	".repeat(depth))
				.append("Choice |")
				.append(this.node.bpmnObject().id())
				.append("| has paths:\n");

		int i = 1;

		for (Cluster cluster : this.choicePaths.values())
		{
			builder.append("	".repeat(depth + 1))
					.append("- Path n°")
					.append(i++)
					.append(":\n");

			builder.append(cluster.stringify(depth + 2))
					.append("\n");
		}

		return builder.toString();
	}

	@Override
	public EnhancedType type()
	{
		return EnhancedType.CHOICE;
	}

	//Public methods

	/**
	 * The cluster to which we add an element corresponds to the current path of the choice.
	 * This path is identified by its initial sequence flow, that is used as key in this
	 * method.
	 *
	 * @param key the sequence flow starting the current path
	 * @param element the enhanced node belonging to the current path
	 */
	public void addElementToClusterWithKey(final Node key,
										   final EnhancedNode element)
	{
		final Cluster cluster = this.choicePaths.computeIfAbsent(key, c -> new Cluster());
		cluster.addElement(element);
	}

	/**
	 * The cluster to which we add an element corresponds to the current path of the choice.
	 * This path is identified by its initial sequence flow, that is used as key in this
	 * method.
	 *
	 * @param key the sequence flow starting the current path
	 * @param dependency the dependency belonging to the current path
	 */
	public void addDependencyToClusterWithKey(final Node key,
											  final Dependency dependency)
	{
		final Cluster cluster = this.choicePaths.computeIfAbsent(key, c -> new Cluster());
		cluster.addDependency(dependency);
	}

	public Cluster getClusterFromKey(final Node key)
	{
		return this.choicePaths.computeIfAbsent(key, c -> new Cluster());
	}

	public Collection<Cluster> clusters()
	{
		return this.choicePaths.values();
	}
}
