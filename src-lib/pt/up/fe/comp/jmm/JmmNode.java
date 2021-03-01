package pt.up.fe.comp.jmm;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This interface represents a node in the Jmm AST.
 * 
 * @author COMP2021
 *
 */
public interface JmmNode {

    /**
     * @return the kind of this node (e.g. MethodDeclaration, ClassDeclaration, etc.)
     */
    String getKind();

    /**
     * @return the names of the attributes supported by this Node kind
     */
    List<String> getAttributes();

    /**
     * Sets the value of an attribute.
     * 
     * @param attribute
     * @param value
     */
    void put(String attribute, String value);

    /**
     * 
     * @param attribute
     * @returns the value of an attribute. To see all the attributes iterate the list provided by
     *          {@link JmmNode#getAttributes()}
     */
    String get(String attribute);

    /**
     * 
     * @return the children of the node or an empty list if there are no children
     * 
     */
    List<JmmNode> getChildren();

    /**
     * 
     * @return the number of children of the node
     */
    int getNumChildren();

    /**
     * Adds a new node at the end of the children list
     * 
     * @param child
     */
    default void add(JmmNode child) {
        add(child, getNumChildren());
    }

    /**
     * Inserts a node at the given position
     * 
     * @param child
     * @param index
     */
    void add(JmmNode child, int index);

    default String toJson() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(JmmNode.class, new JmmSerializer())
                .create();
        return gson.toJson(this, JmmNode.class);
    }
}