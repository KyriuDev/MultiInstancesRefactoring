package constants;

public class Boolean
{
    private Boolean()
    {

    }

    public static boolean parse(final String s)
    {
        if (s.trim().equalsIgnoreCase("true"))
        {
            return true;
        }
        else if (s.trim().equalsIgnoreCase("false"))
        {
            return false;
        }

        throw new IllegalStateException("String |" + s + "| does not match any boolean value.");
    }

    public static String unparse(final boolean b)
    {
        return b ? "true" : "false";
    }
}
