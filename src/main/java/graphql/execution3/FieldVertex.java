/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Field;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import java.util.Objects;

/**
 *
 * @author gkesler
 */
public class FieldVertex extends NodeVertex<Field, GraphQLOutputType> {    
    public FieldVertex(Field node, GraphQLOutputType type, GraphQLFieldsContainer definedIn) {
        this(node, type, definedIn, null);
    }    
    
    public FieldVertex(Field node, GraphQLOutputType type, GraphQLFieldsContainer definedIn, String scopeAlias) {
        super(node, type);
        
        this.definedIn = Objects.requireNonNull(definedIn);
        this.scopeAlias = scopeAlias;
    }    

    public GraphQLFieldsContainer getDefinedIn() {
        return definedIn;
    }

    public Object getScope() {
        return scopeAlias;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.node.getName());
        hash = 97 * hash + Objects.hashCode(this.node.getAlias());
        hash = 97 * hash + Objects.hashCode(this.type);
        hash = 97 * hash + Objects.hashCode(this.definedIn);
        hash = 97 * hash + Objects.hashCode(this.scopeAlias);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            FieldVertex other = (FieldVertex)obj;
            return Objects.equals(this.definedIn, other.definedIn) &&
                    Objects.equals(this.scopeAlias, other.scopeAlias);
        }
        
        return false;
    }

    @Override
    protected StringBuilder toString(StringBuilder builder) {
        return super
                .toString(builder)
                .append(", definedIn=").append(definedIn)
                .append(", scopeAlias=").append(scopeAlias);
    }

    @Override
    <U> U accept(U data, NodeVertexVisitor<? super U> visitor) {
        return (U)visitor.visit(this, data);
    }

    private final GraphQLFieldsContainer definedIn;
    private final String scopeAlias;
}