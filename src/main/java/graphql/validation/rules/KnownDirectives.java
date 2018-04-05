package graphql.validation.rules;


import graphql.GraphQLException;
import graphql.introspection.Introspection.DirectiveLocation;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;
import static graphql.introspection.Introspection.DirectiveLocation.QUERY;
import static graphql.introspection.Introspection.DirectiveLocation.MUTATION;

import graphql.language.CommonNodeVisitorStub;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeVisitor;
import graphql.language.NodeVisitorStub;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.schema.GraphQLDirective;
import graphql.util.SimpleTraverserContext;
import graphql.util.TraversalControl;
import static graphql.util.TraversalControl.QUIT;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import graphql.util.TraverserContext;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;
import org.openjdk.jmh.annotations.Benchmark;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class KnownDirectives extends AbstractRule {


    public KnownDirectives(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        GraphQLDirective graphQLDirective = getValidationContext().getSchema().getDirective(directive.getName());
        if (graphQLDirective == null) {
            String message = String.format("Unknown directive %s", directive.getName());
            addError(ValidationErrorType.UnknownDirective, directive.getSourceLocation(), message);
            return;
        }

        Node ancestor = ancestors.get(ancestors.size() - 1);

        boolean result = false;
        final int executions = 1000000000;
        //final int executions = 100000;
        long start = System.nanoTime();


        for(int i = 0 ; i < executions; i++) {
            Context context = new Context(ancestor,graphQLDirective);
           result = hasInvalidLocation3(context, ancestor);
           // result = hasInvalidLocation2(graphQLDirective, ancestor);
           // result = hasInvalidLocation4(graphQLDirective, ancestor);
        }

        print("res:", System.nanoTime() - start);

        if (result) {
            String message = String.format("Directive %s not allowed here", directive.getName());
            addError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    private boolean hasInvalidLocation(GraphQLDirective directive, Node ancestor) {
        Context context = new Context(ancestor,directive);
        ancestor.accept(context,LOCATION_VISITOR);
        return context.isValid();
    }

    private boolean hasInvalidLocation3(Context context, Node ancestor) {
        ancestor.accept(context,LOCATION_VISITOR);
        return context.isValid();
    }

    private boolean hasInvalidLocation4(GraphQLDirective directive, Node ancestor) {
        return (Boolean)ancestor.accept(directive,LOCATION_VISITOR_CONTEXTFREE);

    }

    @SuppressWarnings("deprecation") // the suppression stands because its deprecated but still in graphql spec
    private boolean hasInvalidLocation2(GraphQLDirective directive, Node ancestor) {
        if (ancestor instanceof OperationDefinition) {
            Operation operation = ((OperationDefinition) ancestor).getOperation();
            return Operation.QUERY.equals(operation) ?
                    !(directive.validLocations().contains(DirectiveLocation.QUERY) || directive.isOnOperation()) :
                    !(directive.validLocations().contains(DirectiveLocation.MUTATION) || directive.isOnOperation());
        } else if (ancestor instanceof Field) {
            return !(directive.validLocations().contains(DirectiveLocation.FIELD) || directive.isOnField());
        } else if (ancestor instanceof FragmentSpread) {
            return !(directive.validLocations().contains(DirectiveLocation.FRAGMENT_SPREAD) || directive.isOnFragment());
        } else if (ancestor instanceof FragmentDefinition) {
            return !(directive.validLocations().contains(DirectiveLocation.FRAGMENT_DEFINITION) || directive.isOnFragment());
        } else if (ancestor instanceof InlineFragment) {
            return !(directive.validLocations().contains(DirectiveLocation.INLINE_FRAGMENT) || directive.isOnFragment());
        }
        return true;
    }

    private final static LocationVisitor LOCATION_VISITOR = new LocationVisitor();
    private final static LocationVisitorContextLess LOCATION_VISITOR_CONTEXTFREE = new LocationVisitorContextLess();

    private class Context extends SimpleTraverserContext<Node> {
        final GraphQLDirective directive;

        Context(Node node, GraphQLDirective directive) {
            super(node);
            this.directive = directive;
        }

        @Override
        public Object getInitialData() {
            return directive;
        }

        Boolean isValid() {
            return (Boolean)getResult();
        }
    }

    private static class LocationVisitor extends NodeVisitorStub {
        @Override
        public TraversalControl visitField(Field node, TraverserContext<Node> context) {
            return setAndQuit(context, FIELD);
        }

        @Override
        public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
            return setAndQuit(context, FRAGMENT_SPREAD);
        }

        @Override
        public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
            return setAndQuit(context, FRAGMENT_DEFINITION);
        }

        @Override
        public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
            return setAndQuit(context, INLINE_FRAGMENT);
        }

        @Override
        public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
            return setAndQuit(context, Operation.QUERY.equals(node.getOperation()) ? QUERY : MUTATION);
        }

        @Override
        protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
            context.setResult(true);
            return QUIT;
        }

        private boolean has(GraphQLDirective directive, DirectiveLocation thing) {
            return directive.validLocations().contains(thing);
        }

        private TraversalControl setAndQuit(TraverserContext<Node> context, DirectiveLocation location) {
            context.setResult(isInvalidLocation(context,location));
            return QUIT;
        }

        private GraphQLDirective getDirective(TraverserContext<Node> context) {
            return (GraphQLDirective)context.getInitialData();
        }

        @SuppressWarnings("deprecation") // the suppression stands because its deprecated but still in graphql spec
        private boolean legacyIsOnDirective(DirectiveLocation location, GraphQLDirective directive) {
            switch (location) {
                case FIELD:
                    return directive.isOnField();

                case FRAGMENT_SPREAD:
                case FRAGMENT_DEFINITION:
                case INLINE_FRAGMENT:
                    return directive.isOnFragment();

                case QUERY:
                case MUTATION:
                    return directive.isOnOperation();

                default:
                    throw new GraphQLException("Legacy behaviour did not expect location " + location.toString());
            }
        }

        private boolean isInvalidLocation(TraverserContext<Node> context, DirectiveLocation location) {
            GraphQLDirective directive = getDirective(context);
            return !(has(directive,location) || legacyIsOnDirective(location, directive));

        }
    }


    private static class LocationVisitorContextLess extends CommonNodeVisitorStub<GraphQLDirective, Boolean>  {
        @Override
        public Boolean visitField(Field node, GraphQLDirective directive) {
            return  isInvalidLocation(directive,FIELD);
        }

        @Override
        public Boolean visitFragmentSpread(FragmentSpread node, GraphQLDirective directive) {
            return  isInvalidLocation(directive,FRAGMENT_SPREAD);
        }

        @Override
        public Boolean visitFragmentDefinition(FragmentDefinition node, GraphQLDirective directive) {
            return isInvalidLocation(directive,FRAGMENT_DEFINITION);
        }

        @Override
        public Boolean visitInlineFragment(InlineFragment node, GraphQLDirective directive) {

            return isInvalidLocation(directive,INLINE_FRAGMENT);
        }

        @Override
        public Boolean visitOperationDefinition(OperationDefinition node, GraphQLDirective directive) {
            return isInvalidLocation(directive,Operation.QUERY.equals(node.getOperation()) ? QUERY : MUTATION);
        }

        @Override
        protected Boolean visitNode(Node node, GraphQLDirective directive) {

            return true;
        }

        private boolean has(GraphQLDirective directive, DirectiveLocation thing) {
            return directive.validLocations().contains(thing);
        }


        @SuppressWarnings("deprecation") // the suppression stands because its deprecated but still in graphql spec
        private boolean legacyIsOnDirective(DirectiveLocation location, GraphQLDirective directive) {
            switch (location) {
                case FIELD:
                    return directive.isOnField();

                case FRAGMENT_SPREAD:
                case FRAGMENT_DEFINITION:
                case INLINE_FRAGMENT:
                    return directive.isOnFragment();

                case QUERY:
                case MUTATION:
                    return directive.isOnOperation();

                default:
                    throw new GraphQLException("Legacy behaviour did not expect location " + location.toString());
            }
        }

        private boolean isInvalidLocation(GraphQLDirective directive, DirectiveLocation location) {
            return !(has(directive,location) || legacyIsOnDirective(location, directive));

        }
    }


    private static void print( String string , long nanos) {
        TimeUnit unit = chooseUnit(nanos);
        double value = (double) nanos / NANOSECONDS.convert(1, unit);
        System.out.println(String.format(Locale.ROOT,string + "%.4g %s", value, abbreviate(unit)));
    }

    private static String abbreviate(TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return "ns";
            case MICROSECONDS:
                return "\u03bcs"; // μs
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "min";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
            default:
                throw new AssertionError();
        }
    }


    private static TimeUnit chooseUnit(long nanos) {
        if (DAYS.convert(nanos, NANOSECONDS) > 0) {
            return DAYS;
        }
        if (HOURS.convert(nanos, NANOSECONDS) > 0) {
            return HOURS;
        }
        if (MINUTES.convert(nanos, NANOSECONDS) > 0) {
            return MINUTES;
        }
        if (SECONDS.convert(nanos, NANOSECONDS) > 0) {
            return SECONDS;
        }
        if (MILLISECONDS.convert(nanos, NANOSECONDS) > 0) {
            return MILLISECONDS;
        }
        if (MICROSECONDS.convert(nanos, NANOSECONDS) > 0) {
            return MICROSECONDS;
        }
        return NANOSECONDS;
    }
}
