package other;

public class Quadruple<T,U,V,W>
{
    private T first;
    private U second;

    private V third;
    private W fourth;

    public Quadruple(T first,
                     U second,
                     V third,
                     W fourth)
    {
        this.first = first;
        this.second = second;
        this.third = third;
        this.fourth = fourth;
    }

    public T first()
    {
        return this.first;
    }

    public U second()
    {
        return this.second;
    }

    public V third()
    {
        return this.third;
    }

    public W fourth()
    {
        return this.fourth;
    }

    public void setFirst(T first)
    {
        this.first = first;
    }

    public void setSecond(U second)
    {
        this.second = second;
    }

    public void setThird(V third)
    {
        this.third = third;
    }

    public void setFourth(W fourth)
    {
        this.fourth = fourth;
    }

    public Quadruple<T,U,V,W> copy()
    {
        return new Quadruple<>(this.first, this.second, this.third, this.fourth);
    }
}
