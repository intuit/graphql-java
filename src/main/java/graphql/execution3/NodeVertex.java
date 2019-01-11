/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Node;
import graphql.schema.GraphQLType;
import graphql.util.Vertex;
import java.util.Objects;

/**
 *
 * @author gkesler
 * @param <N>   actual type of Node associated with this Vertex
 * @param <T>   GraphQLType from the GraphQLSchema dictionary that describes the Node 
 */
public abstract class NodeVertex<N extends Node, T extends GraphQLType> extends Vertex<NodeVertex<N, T>> {
    protected NodeVertex (N node, T type) {
        this.node = Objects.requireNonNull(node);
        this.type = Objects.requireNonNull(type);
    }

    public N getNode() {
        return node;
    }

    public T getType() {
        return type;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.node);
        hash = 97 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NodeVertex<?, ?> other = (NodeVertex<?, ?>) obj;
        if (!equals(this.node, other.node)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return true;
    }

    private static boolean equals (Node thisNode, Node otherNode) {
        return thisNode.isEqualTo(otherNode);
    }
    
    @Override
    protected StringBuilder toString(StringBuilder builder) {
        return super
            .toString(builder)
            .append(", node=").append(node)
            .append(", type=").append(type);
    }
    
    public <U extends NodeVertex<X, Y>, X extends Node, Y extends GraphQLType> U as (Class<? super U> castTo) {
        if (castTo.isAssignableFrom(getClass()))
            return (U)castTo.cast(this);
        
        throw new IllegalArgumentException(String.format("could not cast to '%s'", castTo.getName()));
    }
    
    abstract <U> U accept (U data, NodeVertexVisitor<? super U> visitor);
    
    protected final N node;
    protected final T type;
}