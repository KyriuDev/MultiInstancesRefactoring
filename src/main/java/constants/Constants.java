package constants;

public class Constants
{
    private Constants()
    {

    }

    public static final String BPMN_PREFIX = "bpmn:";
    public static final String BPMNDI_PREFIX = "bpmndi:";
    public static final String PROCESS = "process";
    public static final String BPMN_PROCESS = BPMN_PREFIX + PROCESS;
    public static final String ID = "id";
    public static final String IS_EXECUTABLE = "isExecutable";
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final String BPMN_CATEGORY = BPMN_PREFIX + "category";
    public static final String BPMN_CATEGORY_VALUE = BPMN_CATEGORY + "Value";
    public static final String VALUE = "value";
    public static final String BPMN_DIAGRAM = BPMNDI_PREFIX + "BPMNDiagram";
    public static final String BPMN_PLANE = BPMNDI_PREFIX + "BPMNPlane";
    public static final String BPMN_ELEMENT = "bpmnElement";
    public static final String SOURCE_REF = "sourceRef";
    public static final String TARGET_REF = "targetRef";
    public static final String NAME = "name";
    public static final String AUTO_STORE_VARIABLES = "activiti:autoStoreVariables";
    public static final String BPMN_INCOMNIG = BPMN_PREFIX + "incoming";
    public static final String BPMN_OUTGOING = BPMN_PREFIX + "outgoing";
    public static final String CATEGORY_VALUE_REF = "categoryValueRef";
    public static final String BPMN_TEXT = BPMN_PREFIX + "text";
    public static final String WAYPOINT = "waypoint";
    public static final String CURRENT_WAYPOINT = "di:" + WAYPOINT;
    public static final String LEGACY_WAYPOINT = "omg" + CURRENT_WAYPOINT;
    public static final String X = "x";
    public static final String Y = "y";
    public static final String DOT = ".";
    public static final String IS_MARKER_VISIBLE = "isMarkerVisible";
    public static final String BIOC = "bioc:";
    public static final String BIOC_STROKE = BIOC + "stroke";
    public static final String BIOC_FILL = BIOC + "fill";
    public static final String BOUNDS = "Bounds";
    public static final String CURRENT_BOUNDS = "dc:" + BOUNDS;
    public static final String LEGACY_BOUNDS = "omg" + CURRENT_BOUNDS;
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    public static final String BPMN_CONDITION = BPMN_PREFIX + "condition";
    public static final String XSI_TYPE = "xsi:type";
    public static final String SPACE = " ";
    public static final String EQUALS = "=";
    public static final String GREATER_THAN = ">";
    public static final String LOWER_THAN = "<";
    public static final String TWO_SPACES = SPACE + SPACE;
    public static final String FOUR_SPACES = TWO_SPACES + TWO_SPACES;
    public static final String SIX_SPACES = FOUR_SPACES + TWO_SPACES;
    public static final String EIGHT_SPACES = FOUR_SPACES + FOUR_SPACES;
    public static final String UTF_8 = "UTF-8";
}
