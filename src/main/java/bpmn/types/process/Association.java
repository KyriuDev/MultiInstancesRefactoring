package bpmn.types.process;

import java.util.ArrayList;

public class Association extends BpmnProcessObject
{
    private String tempSource;
    private String tempTarget;
    private BpmnProcessObject source;
    private BpmnProcessObject target;

    public Association(final String id,
                       final String tempSource,
                       final String tempTarget)
    {
        super(BpmnProcessType.ASSOCIATION, id);
        this.tempSource = tempSource;
        this.tempTarget = tempTarget;
    }

    public Association(final String id)
    {
        super(BpmnProcessType.ASSOCIATION, id);
    }

    @Override
    public BpmnProcessObject copy()
    {
        Association duplicate = new Association(BpmnProcessFactory.generateID(this));
        duplicate.setName(this.name());

        return duplicate;
    }

    public void setProperSourceAndTarget(final ArrayList<BpmnProcessObject> objects)
    {
        for (BpmnProcessObject object : objects)
        {
            if (object.id().equals(tempSource))
            {
                if (this.source != null)
                {
                    throw new IllegalStateException("Il existe 2 éléments de même ID!");
                }

                this.source = object;
            }
            else if (object.id().equals(tempTarget))
            {
                if (this.target != null)
                {
                    throw new IllegalStateException("Il existe 2 éléments de même ID!");
                }

                this.target = object;
            }
        }
    }

    public BpmnProcessObject source()
    {
        return this.source;
    }

    public BpmnProcessObject target()
    {
        return this.target;
    }
}
