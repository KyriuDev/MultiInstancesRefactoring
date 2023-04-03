package bpmn;

import bpmn.types.process.Category;

import java.util.ArrayList;

public class BpmnCategories
{
    private final ArrayList<Category> categories;

    public BpmnCategories()
    {
        this.categories = new ArrayList<>();
    }

    public void addCategory(Category category)
    {
        this.categories.add(category);
    }

    public ArrayList<Category> categories()
    {
        return this.categories;
    }
}
