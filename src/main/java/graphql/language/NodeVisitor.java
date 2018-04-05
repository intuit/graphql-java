package graphql.language;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@Internal
public interface NodeVisitor extends CommonNodeVisitor<TraverserContext<Node>, TraversalControl> {

}
