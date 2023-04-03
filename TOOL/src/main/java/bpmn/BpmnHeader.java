package bpmn;

import java.util.HashMap;
import java.util.Map;

public class BpmnHeader
{
    private final Map<String, String> metadata;

    public BpmnHeader()
    {
        this.metadata = new HashMap<>();
    }

    public void putMetadata(final String key,
                            final String value)
    {
        this.metadata.put(key, value);
    }

    public String xmlHeader()
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    }

    public Map<String, String> metadata()
    {
        return this.metadata;
    }
}
