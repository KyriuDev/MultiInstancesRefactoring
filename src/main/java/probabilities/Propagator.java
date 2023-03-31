package probabilities;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import loops_management.Loop;
import loops_management.LoopFinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


public class Propagator
{
	private final Graph graph;
	private final LoopFinder loopFinder;

	public Propagator(final Graph graph,
					  final LoopFinder loopFinder)
	{
		this.graph = graph;
		this.loopFinder = loopFinder;
	}

	//Public methods

	public void propagateProbabilities()
	{
		Set<Node> oldMergeGateways = new HashSet<>();
		Set<Node> newMergeGateways = new HashSet<>();
		Set<Node> visitedNodes = new HashSet<>();
		this.propagateProbabilitiesRec(graph.initialNode(), visitedNodes, 1d, oldMergeGateways);

		//Loop over potentially ready merge gateways and remove the non-ready ones
		for (Iterator<Node> iterator = oldMergeGateways.iterator(); iterator.hasNext(); )
		{
			Node mergeGateway = iterator.next();

			for (Node incomingFlow : mergeGateway.parentNodes())
			{
				if (incomingFlow.bpmnObject().probability() == 1d)
				{
					//System.out.println("Gateway " + mergeGateway.bpmnObject().id() + " is not ready to propagate.");
					//At least one of the incoming flow has not been updated, so the gateway is not ready to propagate
					iterator.remove();
					break;
				}
			}
		}

		//Propagate probabilities to all ready gateways, and retrieve all new merge gateways
		do
		{
			//System.out.println("On passe ici");

			for (Node mergeGateway : oldMergeGateways)
			{
				//System.out.println("On propage dans la gateway " + mergeGateway.bpmnObject().id());

				double probability = 0d;

				for (Node incomingFlow : mergeGateway.parentNodes())
				{
					probability += incomingFlow.bpmnObject().probability();
				}

				probability = Math.min(probability, 1d);

				//Set the probability of the sequence flow following the gateway
				Node nextSequenceFlow = mergeGateway.childNodes().iterator().next();
				mergeGateway.bpmnObject().setProbability(probability);
				nextSequenceFlow.bpmnObject().setProbability(probability);

				this.propagateProbabilitiesRec(nextSequenceFlow, visitedNodes, 1d, newMergeGateways);
			}

			//System.out.println("Found " + newMergeGateways.size() + " new merge gateways.");

			oldMergeGateways = new HashSet<>(newMergeGateways);
			newMergeGateways.clear();

			//Loop over potentially ready merge gateways and remove the non-ready ones
			for (Iterator<Node> iterator = oldMergeGateways.iterator(); iterator.hasNext(); )
			{
				Node mergeGateway = iterator.next();

				for (Node incomingFlow : mergeGateway.parentNodes())
				{
					if (incomingFlow.bpmnObject().probability() == 1d)
					{
						//At least one of the incoming flow has not been updated, so the gateway is not ready to propagate
						iterator.remove();
						break;
					}
				}
			}
		}
		while (!oldMergeGateways.isEmpty());
	}

	//Private methods

	private void propagateProbabilitiesRec(final Node currentNode,
										   final Set<Node> visitedNodes,
										   final double currentProbability,
										   final Set<Node> mergeGateways)
	{
		//If the current node is a join gateway and do not correspond to the joining point of
		//the loop, then we need to sum the probabilities of the incoming flows to now the probability
		//of the outgoing flow.
		//We can do it in 1 step, so we store the merge gateways in order to process them in the next step
		//and so on, until no merge gateway has to be performed.
		if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
		{
			if (((Gateway) currentNode.bpmnObject()).isMergeGateway())
			{
				if (currentNode.isInLoop())
				{
					ArrayList<Loop> correspondingLoops = loopFinder.findLoopsContaining(currentNode);

					if (this.nodeCanNotEscapeLoop(correspondingLoops, currentNode))
					{
						//The current merge gateway is a "real" merge gateway, meaning that
						//it belongs to a loop but does not correspond to the starting or the
						//end point of a loop, so we need to sum the probabilities
						mergeGateways.add(currentNode);
						visitedNodes.add(currentNode);
						return;
					}
				}
				else
				{
					//A merge gateway outside a loop is necessarily a real merge gateway
					mergeGateways.add(currentNode);
					visitedNodes.add(currentNode);
					return;
				}
			}
		}

		if (visitedNodes.contains(currentNode))
		{
			//System.out.println("Node " + currentNode.bpmnObject().id() + " was already visited.");
			return;
		}

		visitedNodes.add(currentNode);

		final double newProbability = currentProbability * currentNode.bpmnObject().probability();
		currentNode.bpmnObject().setProbability(newProbability);

		for (Node child : currentNode.childNodes())
		{
			this.propagateProbabilitiesRec(child, visitedNodes, newProbability, mergeGateways);
		}
	}

	private boolean nodeCanNotEscapeLoop(final ArrayList<Loop> loops,
										 final Node currentNode)
	{
		for (Loop loop : loops)
		{
			for (Node parent : currentNode.parentNodes())
			{
				if (!loop.hasNode(parent))
				{
					//Current node can escape the loop
					return false;
				}
			}
		}

		//If all loops containing the current node also contain its parents,
		//then the current node can not escape the loops, and is considered
		//as a "real" merge gateway
		return true;
	}
}
