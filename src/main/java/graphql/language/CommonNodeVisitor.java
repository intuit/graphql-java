package graphql.language;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@Internal
public interface CommonNodeVisitor<IN,OUT> {
    OUT visitArgument(Argument node, IN data);

    OUT visitArrayValue(ArrayValue node, IN data);

    OUT visitBooleanValue(BooleanValue node,IN data);

    OUT visitDirective(Directive node, IN data);

    OUT visitDirectiveDefinition(DirectiveDefinition node, IN data);

    OUT visitDirectiveLocation(DirectiveLocation node, IN data);

    OUT visitDocument(Document node, IN data);

    OUT visitEnumTypeDefinition(EnumTypeDefinition node, IN data);

    OUT visitEnumValue(EnumValue node, IN data);

    OUT visitEnumValueDefinition(EnumValueDefinition node, IN data);

    OUT visitField(Field node, IN data);

    OUT visitFieldDefinition(FieldDefinition node, IN data);

    OUT visitFloatValue(FloatValue node, IN data);

    OUT visitFragmentDefinition(FragmentDefinition node, IN data);

    OUT visitFragmentSpread(FragmentSpread node, IN data);

    OUT visitInlineFragment(InlineFragment node, IN data);

    OUT visitInputObjectTypeDefinition(InputObjectTypeDefinition node, IN data);

    OUT visitInputValueDefinition(InputValueDefinition node, IN data);

    OUT visitIntValue(IntValue node, IN data);

    OUT visitInterfaceTypeDefinition(InterfaceTypeDefinition node, IN data);

    OUT visitListType(ListType node, IN data);

    OUT visitNonNullType(NonNullType node, IN data);

    OUT visitNullValue(NullValue node, IN data);

    OUT visitObjectField(ObjectField node, IN data);

    OUT visitObjectTypeDefinition(ObjectTypeDefinition node, IN data);

    OUT visitObjectValue(ObjectValue node, IN data);

    OUT visitOperationDefinition(OperationDefinition node, IN data);

    OUT visitOperationTypeDefinition(OperationTypeDefinition node, IN data);

    OUT visitScalarTypeDefinition(ScalarTypeDefinition node, IN data);

    OUT visitSchemaDefinition(SchemaDefinition node, IN data);

    OUT visitSelectionSet(SelectionSet node, IN data);

    OUT visitStringValue(StringValue node, IN data);

    OUT visitTypeName(TypeName node, IN data);

    OUT visitUnionTypeDefinition(UnionTypeDefinition node, IN data);

    OUT visitVariableDefinition(VariableDefinition node, IN data);

    OUT visitVariableReference(VariableReference node, IN data);
}
