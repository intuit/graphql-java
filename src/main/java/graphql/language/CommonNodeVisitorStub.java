package graphql.language;

import graphql.Internal;

@Internal
public class CommonNodeVisitorStub<IN, OUT>
        implements CommonNodeVisitor<IN, OUT> {
    @Override
    public OUT visitArgument(Argument node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitArrayValue(ArrayValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitBooleanValue(BooleanValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitDirective(Directive node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitDirectiveDefinition(DirectiveDefinition node, IN context) {
        return visitDefinition(node, context);
    }

    @Override
    public OUT visitDirectiveLocation(DirectiveLocation node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitDocument(Document node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitEnumTypeDefinition(EnumTypeDefinition node, IN context) {
        return visitTypeDefinition(node, context);
    }

    @Override
    public OUT visitEnumValue(EnumValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitEnumValueDefinition(EnumValueDefinition node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitField(Field node, IN context) {
        return visitSelection(node, context);
    }

    @Override
    public OUT visitFieldDefinition(FieldDefinition node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitFloatValue(FloatValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitFragmentDefinition(FragmentDefinition node, IN context) {
        return visitDefinition(node, context);
    }

    @Override
    public OUT visitFragmentSpread(FragmentSpread node, IN context) {
        return visitSelection(node, context);
    }

    @Override
    public OUT visitInlineFragment(InlineFragment node, IN context) {
        return visitSelection(node, context);
    }

    @Override
    public OUT visitInputObjectTypeDefinition(InputObjectTypeDefinition node, IN context) {
        return visitTypeDefinition(node, context);
    }

    @Override
    public OUT visitInputValueDefinition(InputValueDefinition node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitIntValue(IntValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitInterfaceTypeDefinition(InterfaceTypeDefinition node, IN context) {
        return visitTypeDefinition(node, context);
    }

    @Override
    public OUT visitListType(ListType node, IN context) {
        return visitType(node, context);
    }

    @Override
    public OUT visitNonNullType(NonNullType node, IN context) {
        return visitType(node, context);
    }

    @Override
    public OUT visitNullValue(NullValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitObjectField(ObjectField node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitObjectTypeDefinition(ObjectTypeDefinition node, IN context) {
        return visitTypeDefinition(node, context);
    }

    @Override
    public OUT visitObjectValue(ObjectValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitOperationDefinition(OperationDefinition node, IN context) {
        return visitDefinition(node, context);
    }

    @Override
    public OUT visitOperationTypeDefinition(OperationTypeDefinition node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitScalarTypeDefinition(ScalarTypeDefinition node, IN context) {
        return visitTypeDefinition(node, context);
    }

    @Override
    public OUT visitSchemaDefinition(SchemaDefinition node, IN context) {
        return visitDefinition(node, context);
    }

    @Override
    public OUT visitSelectionSet(SelectionSet node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitStringValue(StringValue node, IN context) {
        return visitValue(node, context);
    }

    @Override
    public OUT visitTypeName(TypeName node, IN context) {
        return visitType(node, context);
    }

    @Override
    public OUT visitUnionTypeDefinition(UnionTypeDefinition node, IN context) {
        return visitTypeDefinition(node, context);
    }

    @Override
    public OUT visitVariableDefinition(VariableDefinition node, IN context) {
        return visitNode(node, context);
    }

    @Override
    public OUT visitVariableReference(VariableReference node, IN context) {
        return visitValue(node, context);
    }


    protected OUT visitValue(Value<?> node, IN context) {
        return visitNode(node, context);
    }

    protected OUT visitDefinition(Definition<?> node, IN context) {
        return visitNode(node, context);
    }

    protected OUT visitTypeDefinition(TypeDefinition<?> node, IN context) {
        return visitDefinition(node, context);
    }

    protected OUT visitSelection(Selection<?> node, IN context) {
        return visitNode(node, context);
    }

    protected OUT visitType(Type<?> node, IN context) {
        return visitNode(node, context);
    }

    protected OUT visitNode(Node node, IN context) {
        return of(node,context);
    }

    protected OUT of(Node node, IN context) {
        throw new RuntimeException("Must be implemented");
    }
}
