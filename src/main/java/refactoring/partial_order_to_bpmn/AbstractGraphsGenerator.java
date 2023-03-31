package refactoring.partial_order_to_bpmn;

import refactoring.dependencies.*;
import refactoring.Cluster;
import refactoring.exceptions.BadDependencyException;

public class AbstractGraphsGenerator
{
	private final Cluster mainCluster;

	public AbstractGraphsGenerator(final Cluster mainCluster)
	{
		this.mainCluster = mainCluster;
	}

	public void generateAbstractGraphs() throws BadDependencyException
	{
		this.generateAbstractGraph(this.mainCluster);
	}

	//Private methods

	private void generateAbstractGraph(final Cluster cluster) throws BadDependencyException
	{
		System.out.println("Found " + cluster.graphsWithDependencies().values().size() + " dependency sets.");

		for (DependencyGraph dependencyGraph : cluster.graphsWithDependencies().keySet())
		{
			final PartialOrder2AbstractGraph partialOrder2AbstractGraph = new PartialOrder2AbstractGraph(dependencyGraph);
			final AbstractGraph abstractGraph = partialOrder2AbstractGraph.generateAbstractGraph();
			cluster.addAbstractGraph(abstractGraph);
			//System.out.println("On crée un graph abstrait");
		}

		for (EnhancedNode node : cluster.elements())
		{
			if (node.type() == EnhancedType.CHOICE)
			{
				final EnhancedChoice choice = (EnhancedChoice) node;

				for (Cluster subCluster : choice.clusters())
				{
					this.generateAbstractGraph(subCluster);
				}
			}
			else if (node.type() == EnhancedType.LOOP)
			{
				final EnhancedLoop loop = (EnhancedLoop) node;

				this.generateAbstractGraph(loop.entryToExitCluster());
				this.generateAbstractGraph(loop.exitToEntryCluster());
			}
		}
	}
}
