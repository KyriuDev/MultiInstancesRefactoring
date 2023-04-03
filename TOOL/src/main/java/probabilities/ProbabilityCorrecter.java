package probabilities;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import bpmn.types.process.SequenceFlow;
import loops_management.Loop;
import loops_management.LoopFinder;

import java.util.HashMap;
import java.util.HashSet;

public class ProbabilityCorrecter
{
	private final Graph graph;
	private final LoopFinder loopFinder;

	public ProbabilityCorrecter(final Graph graph,
								final LoopFinder loopFinder)
	{
		this.graph = graph;
		this.loopFinder = loopFinder;
	}

	public void correctProbabilities()
	{
		this.correctProbabilities(this.graph.initialNode(), new HashSet<>());
	}

	//Private methods

	private void correctProbabilities(final Node currentNode,
									  final HashSet<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
			&& ((Gateway) currentNode.bpmnObject()).isSplitGateway())
		{
			if (this.loopFinder.nodeIsInLoop(currentNode))
			{
				//Either loop exit point or choice start
				final Loop loop = this.loopFinder.findInnerLoopOf(currentNode);

				if (loop.exitPoint().equals(currentNode))
				{
					//Exit point of a loop
					this.probabilizeLoop(currentNode, loop);
				}
				else
				{
					//Choice start inside a loop
					this.probabilizeChoice(currentNode);
				}
			}
			else
			{
				//Choice start outside a loop
				this.probabilizeChoice(currentNode);
			}
		}
	}

	private void probabilizeLoop(final Node currentNode,
								 final Loop loop)
	{
		Node nodeInLoop = null;
		Node nodeOutsideLoop = null;

		for (Node child : currentNode.childNodes())
		{
			if (loop.hasNode(child))
			{
				nodeInLoop = child;
			}
			else
			{
				nodeOutsideLoop = child;
			}
		}

		if (nodeInLoop == null
				|| nodeOutsideLoop == null)
		{
			throw new IllegalStateException("Loop |" + loop.entryPoint().bpmnObject().id() + "| is badly formed.");
		}

		double totalProba = 0d;
		int nbFlowsProbabilized = 0;
		final SequenceFlow flowInLoop = (SequenceFlow) nodeInLoop.bpmnObject();
		final SequenceFlow flowOutsideLoop = (SequenceFlow) nodeOutsideLoop.bpmnObject();

		if (flowInLoop.probabilized())
		{
			totalProba += flowOutsideLoop.probability();
			nbFlowsProbabilized++;
		}
		if (flowOutsideLoop.probabilized())
		{
			totalProba += flowOutsideLoop.probability();
			nbFlowsProbabilized++;
		}

		if (totalProba > 1d
				|| (totalProba < 1d
				&& nbFlowsProbabilized == 2))
		{
			throw new IllegalStateException("Loop |" + loop.entryPoint().bpmnObject().id() + "| is badly" +
					" probabilized. Please verify the probability of its exit flows before continuing.");
		}

		if (flowInLoop.probabilized())
		{
			flowOutsideLoop.setProbability(1d);
		}
		else
		{
			if (flowOutsideLoop.probabilized())
			{
				flowInLoop.setProbability(1d - flowOutsideLoop.probability());
				flowOutsideLoop.setProbability(1d);
			}
			else
			{
				flowInLoop.setProbability(0.5d);
				flowOutsideLoop.setProbability(1d);
			}
		}
	}

	private void probabilizeChoice(final Node currentNode)
	{
		final HashSet<SequenceFlow> nonProbabilizedFlows = new HashSet<>();
		int nbProbabilizedFlows = 0;
		double totalProbability = 0d;

		for (Node childFlow : currentNode.childNodes())
		{
			final SequenceFlow sequenceFlow = (SequenceFlow) childFlow.bpmnObject();

			if (sequenceFlow.probabilized())
			{
				nbProbabilizedFlows++;
				totalProbability += sequenceFlow.probability();
			}
			else
			{
				nonProbabilizedFlows.add(sequenceFlow);
			}
		}

		if (totalProbability > 1d
				|| (totalProbability < 1d
				&& currentNode.childNodes().size() == nbProbabilizedFlows))
		{
			throw new IllegalStateException("Choice |" + currentNode.bpmnObject().id() + "| is badly" +
					" probabilized. Please verify the probability of its child flows before continuing.");
		}

		if (totalProbability != 1d)
		{
			//Total probability is lower than 1, but there are some flows that have not been probabilized
			final double remainingProbability = 1d - totalProbability;
			final double probabilityPerFlow = remainingProbability / (double) nonProbabilizedFlows.size();

			for (SequenceFlow nonProbabilizedFlow : nonProbabilizedFlows)
			{
				nonProbabilizedFlow.setProbability(probabilityPerFlow);
			}
		}
	}
}
