package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.language.Argument;
import graphql.language.AstPrinter;
import graphql.language.Directive;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.InterfaceFieldArgumentRedefinitionError;
import graphql.schema.idl.errors.InterfaceFieldRedefinitionError;
import graphql.schema.idl.errors.InvalidDeprecationDirectiveError;
import graphql.schema.idl.errors.MissingInterfaceFieldArgumentsError;
import graphql.schema.idl.errors.MissingInterfaceFieldError;
import graphql.schema.idl.errors.MissingInterfaceTypeError;
import graphql.schema.idl.errors.MissingScalarImplementationError;
import graphql.schema.idl.errors.MissingTypeError;
import graphql.schema.idl.errors.MissingTypeResolverError;
import graphql.schema.idl.errors.NonUniqueArgumentError;
import graphql.schema.idl.errors.NonUniqueDirectiveError;
import graphql.schema.idl.errors.NonUniqueNameError;
import graphql.schema.idl.errors.OperationTypesMustBeObjects;
import graphql.schema.idl.errors.QueryOperationMissingError;
import graphql.schema.idl.errors.SchemaMissingError;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.schema.idl.errors.TypeExtensionFieldRedefinitionError;
import graphql.schema.idl.errors.TypeExtensionMissingBaseTypeError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This helps pre check the state of the type system to ensure it can be made into an executable schema.
 * <p>
 * It looks for missing types and ensure certain invariants are true before a schema can be made.
 */
public class SchemaTypeChecker {

    public List<GraphQLError> checkTypeRegistry(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {
        List<GraphQLError> errors = new ArrayList<>();
        checkForMissingTypes(errors, typeRegistry);

        checkTypeExtensionsHaveCorrespondingType(errors, typeRegistry);
        checkTypeExtensionsFieldRedefinition(errors, typeRegistry);

        checkInterfacesAreImplemented(errors, typeRegistry);

        checkSchemaInvariants(errors, typeRegistry);

        checkScalarImplementationsArePresent(errors, typeRegistry, wiring);
        checkTypeResolversArePresent(errors, typeRegistry, wiring);

        checkFieldsAreSensible(errors, typeRegistry);

        return errors;
    }

    private void checkSchemaInvariants(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        /*
            https://github.com/facebook/graphql/pull/90/files#diff-fe406b08746616e2f5f00909488cce66R1000

            GraphQL type system definitions can omit the schema definition when the query
            and mutation root types are named `Query` and `Mutation`, respectively.
         */
        // schema
        if (!typeRegistry.schemaDefinition().isPresent()) {
            if (!typeRegistry.getType("Query").isPresent()) {
                errors.add(new SchemaMissingError());
            }
        } else {
            SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().get();
            List<OperationTypeDefinition> operationTypeDefinitions = schemaDefinition.getOperationTypeDefinitions();

            operationTypeDefinitions
                    .forEach(checkOperationTypesExist(typeRegistry, errors));

            operationTypeDefinitions
                    .forEach(checkOperationTypesAreObjects(typeRegistry, errors));

            // ensure we have a "query" one
            Optional<OperationTypeDefinition> query = operationTypeDefinitions.stream().filter(op -> "query".equals(op.getName())).findFirst();
            if (!query.isPresent()) {
                errors.add(new QueryOperationMissingError());
            }

        }
    }


    private void checkForMissingTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        // type extensions
        List<TypeExtensionDefinition> typeExtensions = typeRegistry.typeExtensions().values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        typeExtensions.forEach(typeExtension -> {

            List<Type> implementsTypes = typeExtension.getImplements();
            implementsTypes.forEach(checkInterfaceTypeExists(typeRegistry, errors, typeExtension));

            checkFieldTypesPresent(typeRegistry, errors, typeExtension, typeExtension.getFieldDefinitions());

        });


        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> {

            List<Type> implementsTypes = objectType.getImplements();
            implementsTypes.forEach(checkInterfaceTypeExists(typeRegistry, errors, objectType));

            checkFieldTypesPresent(typeRegistry, errors, objectType, objectType.getFieldDefinitions());

        });

        // interfaces
        List<InterfaceTypeDefinition> interfaceTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfaceTypes.forEach(interfaceType -> {
            List<FieldDefinition> fields = interfaceType.getFieldDefinitions();

            checkFieldTypesPresent(typeRegistry, errors, interfaceType, fields);

        });

        // union types
        List<UnionTypeDefinition> unionTypes = filterTo(typesMap, UnionTypeDefinition.class);
        unionTypes.forEach(unionType -> {
            List<Type> memberTypes = unionType.getMemberTypes();
            memberTypes.forEach(checkTypeExists("union member", typeRegistry, errors, unionType));

        });


        // input types
        List<InputObjectTypeDefinition> inputTypes = filterTo(typesMap, InputObjectTypeDefinition.class);
        inputTypes.forEach(inputType -> {
            List<InputValueDefinition> inputValueDefinitions = inputType.getInputValueDefinitions();
            List<Type> inputValueTypes = inputValueDefinitions.stream()
                    .map(InputValueDefinition::getType)
                    .collect(Collectors.toList());

            inputValueTypes.forEach(checkTypeExists("input value", typeRegistry, errors, inputType));

        });
    }

    private void checkScalarImplementationsArePresent(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {
        typeRegistry.scalars().forEach((scalarName, scalarTypeDefinition) -> {
            WiringFactory wiringFactory = wiring.getWiringFactory();
            ScalarWiringEnvironment environment = new ScalarWiringEnvironment(typeRegistry, scalarTypeDefinition);
            if (!wiringFactory.providesScalar(environment) && !wiring.getScalars().containsKey(scalarName)) {
                errors.add(new MissingScalarImplementationError(scalarName));
            }
        });
    }

    private void checkFieldsAreSensible(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // type extensions
        List<TypeExtensionDefinition> typeExtensions = typeRegistry.typeExtensions().values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        typeExtensions.forEach(typeExtension -> checkTypeExtFields(errors, typeExtension, typeExtension.getFieldDefinitions()));

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> checkObjTypeFields(errors, objectType, objectType.getFieldDefinitions()));

        // interfaces
        List<InterfaceTypeDefinition> interfaceTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfaceTypes.forEach(interfaceType -> checkInterfaceFields(errors, interfaceType, interfaceType.getFieldDefinitions()));

        // enum types
        List<EnumTypeDefinition> enumTypes = filterTo(typesMap, EnumTypeDefinition.class);
        enumTypes.forEach(enumType -> checkEnumValues(errors, enumType, enumType.getEnumValueDefinitions()));

        // input types
        List<InputObjectTypeDefinition> inputTypes = filterTo(typesMap, InputObjectTypeDefinition.class);
        inputTypes.forEach(inputType -> checkInputValues(errors, inputType, inputType.getInputValueDefinitions()));
    }

    private void checkTypeExtFields(List<GraphQLError> errors, TypeExtensionDefinition typeExtension, List<FieldDefinition> fieldDefinitions) {

        // field unique ness
        checkNamedUniqueness(errors, fieldDefinitions, FieldDefinition::getName,
                (name, fieldDef) -> new NonUniqueNameError(typeExtension, fieldDef));

        // field arg unique ness
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueArgumentError(typeExtension, fld, name)));

        // directive checks
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getDirectives(), Directive::getName,
                (directiveName, directive) -> new NonUniqueDirectiveError(typeExtension, fld, directiveName)));

        fieldDefinitions.forEach(fld -> fld.getDirectives().forEach(directive -> {
            checkDeprecatedDirective(errors, directive,
                    () -> new InvalidDeprecationDirectiveError(typeExtension, fld));

            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                    (argumentName, argument) -> new NonUniqueArgumentError(typeExtension, fld, argumentName));

        }));
    }

    private void checkObjTypeFields(List<GraphQLError> errors, ObjectTypeDefinition typeDefinition, List<FieldDefinition> fieldDefinitions) {
        // field unique ness
        checkNamedUniqueness(errors, fieldDefinitions, FieldDefinition::getName,
                (name, fieldDef) -> new NonUniqueNameError(typeDefinition, fieldDef));

        // field arg unique ness
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueArgumentError(typeDefinition, fld, name)));

        // directive checks
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getDirectives(), Directive::getName,
                (directiveName, directive) -> new NonUniqueDirectiveError(typeDefinition, fld, directiveName)));

        fieldDefinitions.forEach(fld -> fld.getDirectives().forEach(directive -> {
            checkDeprecatedDirective(errors, directive,
                    () -> new InvalidDeprecationDirectiveError(typeDefinition, fld));

            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                    (argumentName, argument) -> new NonUniqueArgumentError(typeDefinition, fld, argumentName));

        }));
    }

    private void checkInterfaceFields(List<GraphQLError> errors, InterfaceTypeDefinition interfaceType, List<FieldDefinition> fieldDefinitions) {
        // field unique ness
        checkNamedUniqueness(errors, fieldDefinitions, FieldDefinition::getName,
                (name, fieldDef) -> new NonUniqueNameError(interfaceType, fieldDef));

        // field arg unique ness
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getInputValueDefinitions(), InputValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueArgumentError(interfaceType, fld, name)));

        // directive checks
        fieldDefinitions.forEach(fld -> checkNamedUniqueness(errors, fld.getDirectives(), Directive::getName,
                (directiveName, directive) -> new NonUniqueDirectiveError(interfaceType, fld, directiveName)));

        fieldDefinitions.forEach(fld -> fld.getDirectives().forEach(directive -> {
            checkDeprecatedDirective(errors, directive,
                    () -> new InvalidDeprecationDirectiveError(interfaceType, fld));

            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                    (argumentName, argument) -> new NonUniqueArgumentError(interfaceType, fld, argumentName));

        }));
    }

    private void checkEnumValues(List<GraphQLError> errors, EnumTypeDefinition enumType, List<EnumValueDefinition> enumValueDefinitions) {

        // enum unique ness
        checkNamedUniqueness(errors, enumValueDefinitions, EnumValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueNameError(enumType, inputValueDefinition));


        // directive checks
        enumValueDefinitions.forEach(enumValue -> {
            BiFunction<String, Directive, NonUniqueDirectiveError> errorFunction = (directiveName, directive) -> new NonUniqueDirectiveError(enumType, enumValue, directiveName);
            checkNamedUniqueness(errors, enumValue.getDirectives(), Directive::getName, errorFunction);
        });

        enumValueDefinitions.forEach(enumValue -> enumValue.getDirectives().forEach(directive -> {
            checkDeprecatedDirective(errors, directive,
                    () -> new InvalidDeprecationDirectiveError(enumType, enumValue));

            BiFunction<String, Argument, NonUniqueArgumentError> errorFunction = (argumentName, argument) -> new NonUniqueArgumentError(enumType, enumValue, argumentName);
            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName, errorFunction);

        }));
    }

    private void checkInputValues(List<GraphQLError> errors, InputObjectTypeDefinition inputType, List<InputValueDefinition> inputValueDefinitions) {

        // field unique ness
        checkNamedUniqueness(errors, inputValueDefinitions, InputValueDefinition::getName,
                (name, inputValueDefinition) -> new NonUniqueNameError(inputType, inputValueDefinition));


        // directive checks
        inputValueDefinitions.forEach(inputValueDef -> checkNamedUniqueness(errors, inputValueDef.getDirectives(), Directive::getName,
                (directiveName, directive) -> new NonUniqueDirectiveError(inputType, inputValueDef, directiveName)));

        inputValueDefinitions.forEach(inputValueDef -> inputValueDef.getDirectives().forEach(directive -> {
            checkDeprecatedDirective(errors, directive,
                    () -> new InvalidDeprecationDirectiveError(inputType, inputValueDef));

            checkNamedUniqueness(errors, directive.getArguments(), Argument::getName,
                    (argumentName, argument) -> new NonUniqueArgumentError(inputType, inputValueDef, argumentName));
        }));
    }


    private void checkDeprecatedDirective(List<GraphQLError> errors, Directive directive, Supplier<InvalidDeprecationDirectiveError> errorSupplier) {
        if ("deprecated".equals(directive.getName())) {
            // it can have zero args
            List<Argument> arguments = directive.getArguments();
            if (arguments.size() == 0) {
                return;
            }
            // but if has more than it must have 1 called "reason" of type StringValue
            if (arguments.size() == 1) {
                Argument arg = arguments.get(0);
                if ("reason".equals(arg.getName()) && arg.getValue() instanceof StringValue) {
                    return;
                }
            }
            // not valid
            errors.add(errorSupplier.get());
        }
    }

    /*
     * A simple function that takes a list of things, asks for their names and checks that the
     * names are unique within that list.  If not it calls the error handler function
     */
    private <T, E extends GraphQLError> void checkNamedUniqueness(List<GraphQLError> errors, List<T> listOfNamedThings, Function<T, String> namer, BiFunction<String, T, E> errorFunction) {
        Map<String, T> mapOfThings = new LinkedHashMap<>();
        listOfNamedThings.forEach(thing -> {
            String name = namer.apply(thing);
            if (mapOfThings.containsKey(name)) {
                errors.add(errorFunction.apply(name, thing));
            } else {
                mapOfThings.put(name, thing);
            }
        });
    }

    private void checkTypeResolversArePresent(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {

        Predicate<InterfaceTypeDefinition> noDynamicResolverForInterface = interaceTypeDef -> !wiring.getWiringFactory().providesTypeResolver(new InterfaceWiringEnvironment(typeRegistry, interaceTypeDef));
        Predicate<UnionTypeDefinition> noDynamicResolverForUnion = unionTypeDef -> !wiring.getWiringFactory().providesTypeResolver(new UnionWiringEnvironment(typeRegistry, unionTypeDef));

        Predicate<TypeDefinition> noTypeResolver = typeDefinition -> !wiring.getTypeResolvers().containsKey(typeDefinition.getName());
        Consumer<TypeDefinition> addError = typeDefinition -> errors.add(new MissingTypeResolverError(typeDefinition));

        typeRegistry.types().values().stream()
                .filter(typeDef -> typeDef instanceof InterfaceTypeDefinition)
                .map(InterfaceTypeDefinition.class::cast)
                .filter(noDynamicResolverForInterface)
                .filter(noTypeResolver)
                .forEach(addError);

        typeRegistry.types().values().stream()
                .filter(typeDef -> typeDef instanceof UnionTypeDefinition)
                .map(UnionTypeDefinition.class::cast)
                .filter(noDynamicResolverForUnion)
                .filter(noTypeResolver)
                .forEach(addError);

    }

    private void checkFieldTypesPresent(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fields) {
        List<Type> fieldTypes = fields.stream().map(FieldDefinition::getType).collect(Collectors.toList());
        fieldTypes.forEach(checkTypeExists("field", typeRegistry, errors, typeDefinition));

        List<Type> fieldInputValues = fields.stream()
                .map(f -> f.getInputValueDefinitions()
                        .stream()
                        .map(InputValueDefinition::getType)
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        fieldInputValues.forEach(checkTypeExists("field input", typeRegistry, errors, typeDefinition));
    }


    private Consumer<Type> checkTypeExists(String typeOfType, TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition) {
        return t -> {
            TypeName unwrapped = TypeInfo.typeInfo(t).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError(typeOfType, typeDefinition, unwrapped));
            }
        };
    }

    private Consumer<? super Type> checkInterfaceTypeExists(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition) {
        return t -> {
            TypeInfo typeInfo = TypeInfo.typeInfo(t);
            TypeName unwrapped = typeInfo.getTypeName();
            Optional<TypeDefinition> type = typeRegistry.getType(unwrapped);
            if (!type.isPresent()) {
                errors.add(new MissingInterfaceTypeError("interface", typeDefinition, unwrapped));
            } else if (!(type.get() instanceof InterfaceTypeDefinition)) {
                errors.add(new MissingInterfaceTypeError("interface", typeDefinition, unwrapped));
            }
        };
    }

    private void checkInterfacesAreImplemented(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> {
            List<Type> implementsTypes = objectType.getImplements();
            implementsTypes.forEach(checkInterfaceIsImplemented("object", typeRegistry, errors, objectType));
        });

        Map<String, List<TypeExtensionDefinition>> typeExtensions = typeRegistry.typeExtensions();
        typeExtensions.values().forEach(extList -> extList.forEach(typeExtension -> {
            List<Type> implementsTypes = typeExtension.getImplements();
            implementsTypes.forEach(checkInterfaceIsImplemented("extension", typeRegistry, errors, typeExtension));
        }));
    }

    private Consumer<? super Type> checkInterfaceIsImplemented(String typeOfType, TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors, ObjectTypeDefinition objectTypeDef) {
        return t -> {
            TypeInfo typeInfo = TypeInfo.typeInfo(t);
            TypeName unwrapped = typeInfo.getTypeName();
            Optional<TypeDefinition> type = typeRegistry.getType(unwrapped);
            // previous checks handle the missing case and wrong type case
            if (type.isPresent() && type.get() instanceof InterfaceTypeDefinition) {
                InterfaceTypeDefinition interfaceTypeDef = (InterfaceTypeDefinition) type.get();

                Map<String, FieldDefinition> objectFields = objectTypeDef.getFieldDefinitions().stream()
                        .collect(Collectors.toMap(
                                FieldDefinition::getName, Function.identity(), mergeFirstValue()
                        ));

                interfaceTypeDef.getFieldDefinitions().forEach(interfaceFieldDef -> {
                    FieldDefinition objectFieldDef = objectFields.get(interfaceFieldDef.getName());
                    if (objectFieldDef == null) {
                        errors.add(new MissingInterfaceFieldError(typeOfType, objectTypeDef, interfaceTypeDef, interfaceFieldDef));
                    } else {
                        String interfaceFieldType = AstPrinter.printAst(interfaceFieldDef.getType());
                        String objectFieldType = AstPrinter.printAst(objectFieldDef.getType());
                        if (!interfaceFieldType.equals(objectFieldType)) {
                            errors.add(new InterfaceFieldRedefinitionError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectFieldType, interfaceFieldType));
                        }

                        // look at arguments
                        List<InputValueDefinition> objectArgs = objectFieldDef.getInputValueDefinitions();
                        List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
                        if (objectArgs.size() != interfaceArgs.size()) {
                            errors.add(new MissingInterfaceFieldArgumentsError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef));
                        } else {
                            checkArgumentConsistency(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, interfaceFieldDef, errors);
                        }
                    }
                });
            }
        };
    }

    private void checkArgumentConsistency(String typeOfType, ObjectTypeDefinition objectTypeDef, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, FieldDefinition interfaceFieldDef, List<GraphQLError> errors) {
        List<InputValueDefinition> objectArgs = objectFieldDef.getInputValueDefinitions();
        List<InputValueDefinition> interfaceArgs = interfaceFieldDef.getInputValueDefinitions();
        for (int i = 0; i < interfaceArgs.size(); i++) {
            InputValueDefinition interfaceArg = interfaceArgs.get(i);
            InputValueDefinition objectArg = objectArgs.get(i);
            String interfaceArgStr = AstPrinter.printAst(interfaceArg);
            String objectArgStr = AstPrinter.printAst(objectArg);
            if (!interfaceArgStr.equals(objectArgStr)) {
                errors.add(new InterfaceFieldArgumentRedefinitionError(typeOfType, objectTypeDef, interfaceTypeDef, objectFieldDef, objectArgStr, interfaceArgStr));
            }
        }
    }

    /*
    A type can re-define a field if its actual the same type, but if they make 'fieldA : String' into
    'fieldA : Int' then we cant handle that.  Even 'fieldA : String' to 'fieldA: String!' is tough to handle
    so we don't
    */
    private void checkTypeExtensionsFieldRedefinition(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, List<TypeExtensionDefinition>> typeExtensions = typeRegistry.typeExtensions();
        typeExtensions.values().forEach(extList -> extList.forEach(typeExtension -> {
            //
            // first check for field re-defs within a type ext
            for (TypeExtensionDefinition otherTypeExt : extList) {
                if (otherTypeExt == typeExtension) {
                    continue;
                }
                // its the children that matter - the fields cannot be redefined
                checkForFieldRedefinition(errors, otherTypeExt, otherTypeExt.getFieldDefinitions(), typeExtension.getFieldDefinitions());
            }
            //
            // then check for field re-defs from the base type
            Optional<TypeDefinition> type = typeRegistry.getType(typeExtension.getName());
            if (type.isPresent() && type.get() instanceof ObjectTypeDefinition) {
                ObjectTypeDefinition baseType = (ObjectTypeDefinition) type.get();

                checkForFieldRedefinition(errors, typeExtension, typeExtension.getFieldDefinitions(), baseType.getFieldDefinitions());
            }

        }));

    }

    private void checkForFieldRedefinition(List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fieldDefinitions, List<FieldDefinition> referenceFieldDefinitions) {
        Map<String, FieldDefinition> referenceFields = referenceFieldDefinitions.stream()
                .collect(Collectors.toMap(
                        FieldDefinition::getName, Function.identity(), mergeFirstValue()
                ));

        fieldDefinitions.forEach(fld -> {
            FieldDefinition referenceField = referenceFields.get(fld.getName());
            if (referenceFields.containsKey(fld.getName())) {
                // ok they have the same field but is it the same type
                if (!isSameType(fld.getType(), referenceField.getType())) {
                    errors.add(new TypeExtensionFieldRedefinitionError(typeDefinition, fld));
                }
            }
        });
    }

    private boolean isSameType(Type type1, Type type2) {
        String s1 = AstPrinter.printAst(type1);
        String s2 = AstPrinter.printAst(type2);
        return s1.equals(s2);
    }

    private void checkTypeExtensionsHaveCorrespondingType(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        Map<String, List<TypeExtensionDefinition>> typeExtensions = typeRegistry.typeExtensions();
        typeExtensions.forEach((name, extTypeList) -> {
            TypeExtensionDefinition extensionDefinition = extTypeList.get(0);
            Optional<TypeDefinition> typeDefinition = typeRegistry.getType(new TypeName(name));
            if (!typeDefinition.isPresent()) {
                errors.add(new TypeExtensionMissingBaseTypeError(extensionDefinition));
            } else {
                if (!(typeDefinition.get() instanceof ObjectTypeDefinition)) {
                    errors.add(new TypeExtensionMissingBaseTypeError(extensionDefinition));
                }
            }
        });
    }


    private Consumer<OperationTypeDefinition> checkOperationTypesExist(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            TypeName unwrapped = TypeInfo.typeInfo(op.getType()).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError("operation", op, op.getName(), unwrapped));
            }
        };
    }

    private Consumer<OperationTypeDefinition> checkOperationTypesAreObjects(TypeDefinitionRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            // make sure it is defined as a ObjectTypeDef
            Type queryType = op.getType();
            Optional<TypeDefinition> type = typeRegistry.getType(queryType);
            type.ifPresent(typeDef -> {
                if (!(typeDef instanceof ObjectTypeDefinition)) {
                    errors.add(new OperationTypesMustBeObjects(op));
                }
            });
        };
    }

    private <T extends TypeDefinition> List<T> filterTo(Map<String, TypeDefinition> types, Class<? extends T> clazz) {
        return types.values().stream()
                .filter(t -> clazz.equals(t.getClass()))
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

    private <T> BinaryOperator<T> mergeFirstValue() {
        return (v1, v2) -> v1;
    }
}
