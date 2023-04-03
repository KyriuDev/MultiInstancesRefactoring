package bpmn.types.process;

import constants.FlowDirection;

import java.util.HashMap;

public class Gateway extends BpmnProcessObject
{
    private boolean isMergeGateway;
    private HashMap<Integer, Double> parallelPathsExecutionTimes;

    public Gateway(BpmnProcessType type,
                   String id)
    {
        super(type, id);
        this.verifyGatewayType(type);
        this.isMergeGateway = false;
        this.parallelPathsExecutionTimes = new HashMap<>();
    }

    @Override
    public BpmnProcessObject copy()
    {
        final Gateway duplicate = new Gateway(this.type, BpmnProcessFactory.generateID(this));
        duplicate.setName(this.name());
        //duplicate.setParallelPathsExecutionTimes(new HashMap<>(this.parallelPathsExecutionTimes));

        if (this.isMergeGateway)
        {
            duplicate.markAsMergeGateway();
        }

        return duplicate;
    }

    public void setJoinOrSplitType()
    {
        int nbOutgoingFlows = 0;
        int nbIncomingFlows = 0;

        //logger.debug("Gateway {} has {} flows.", this.id(), this.flows.size());

        for (SequenceFlow sequenceFlow : this.flows.keySet())
        {
            FlowDirection flowDirection = this.flows.get(sequenceFlow);
            //logger.debug("Current flow direction: {}", flowDirection.toString());

            if (flowDirection == FlowDirection.INCOMING)
            {
                nbIncomingFlows++;
            }
            else
            {
                nbOutgoingFlows++;
            }
        }

        if (nbIncomingFlows == nbOutgoingFlows)
        {
            //TODO Create exception
            throw new IllegalStateException("BPMN process is badly formed. Gateway " + this + " has the same number of incoming flows and outgoing flows.");
        }

        if (nbIncomingFlows > 1 && nbOutgoingFlows > 1)
        {
            throw new IllegalStateException("BPMN process is badly formed. Gateway " + this + " has more than 1 incoming flow and more than 1 outgoing flow.");
        }

        if (nbIncomingFlows > nbOutgoingFlows)
        {
            this.isMergeGateway = true;
        }
    }

    public void markAsMergeGateway()
    {
        this.isMergeGateway = true;
    }

    public boolean isMergeGateway()
    {
        return this.isMergeGateway;
    }

    public boolean isSplitGateway()
    {
        return !this.isMergeGateway();
    }

    public void setParallelPathsExecutionTimes(final HashMap<Integer, Double> parallelPathsExecutionTimes)
    {
        if (this.type != BpmnProcessType.PARALLEL_GATEWAY
            || !this.isMergeGateway())
        {
            throw new IllegalStateException("Parallel paths execution times should be set only for parallel merge gateway. Gateway |" + this.id + "| is of type |" + this.type.toString() + "|" + (this.isMergeGateway ? "." : " and is a split one!"));
        }

        this.parallelPathsExecutionTimes = parallelPathsExecutionTimes;
    }

    public HashMap<Integer, Double> parallelPathsExecutionTimes()
    {
        if (this.type != BpmnProcessType.PARALLEL_GATEWAY
                || !this.isMergeGateway())
        {
            throw new IllegalStateException("Parallel paths execution times should be get only for parallel merge gateway. Gateway |" + this.id + "| is of type |" + this.type.toString() + "|" + (this.isMergeGateway ? "." : " and is a split one!"));
        }

        return this.parallelPathsExecutionTimes;
    }

    //Private methods

    private void verifyGatewayType(BpmnProcessType type)
    {
        //TODO Add gateway types when implemented
        if (type != BpmnProcessType.EXCLUSIVE_GATEWAY
            && type != BpmnProcessType.PARALLEL_GATEWAY
            && type != BpmnProcessType.INCLUSIVE_GATEWAY
            && type != BpmnProcessType.COMPLEX_GATEWAY)
        {
            throw new IllegalStateException("Le type |" + type + "| n'est pas un type de gateway.");
        }
    }
}
