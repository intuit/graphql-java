package graphql.language;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@Internal
public class NodeVisitorStub extends CommonNodeVisitorStub<TraverserContext<Node>,TraversalControl> implements NodeVisitor {

    @Override
    protected TraversalControl of(Node node, TraverserContext<Node> context) {
        return TraversalControl.CONTINUE;
    }
}
