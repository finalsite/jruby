/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.nodes.call.*;
import org.jruby.truffle.runtime.*;
import org.jruby.truffle.runtime.core.*;
import org.jruby.truffle.runtime.core.array.*;
import org.jruby.truffle.runtime.core.range.*;
import org.jruby.truffle.runtime.control.*;

@CoreClass(name = "Range")
public abstract class RangeNodes {

    @CoreMethod(names = {"collect", "map"}, needsBlock = true, maxArgs = 0)
    public abstract static class CollectNode extends YieldingCoreMethodNode {

        public CollectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CollectNode(CollectNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray collect(VirtualFrame frame, FixnumRange range, RubyProc block) {
            final RubyContext context = getContext();

            final RubyArray array = new RubyArray(context.getCoreLibrary().getArrayClass());

            int count = 0;

            try {
                for (int n = range.getBegin(); n < range.getExclusiveEnd(); n++) {
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }

                    array.push(yield(frame, block, n));
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    ((RubyRootNode) getRootNode()).reportLoopCountThroughBlocks(count);
                }
            }

            return array;
        }

    }

    @CoreMethod(names = "each", needsBlock = true, maxArgs = 0)
    public abstract static class EachNode extends YieldingCoreMethodNode {

        private final BranchProfile breakProfile = new BranchProfile();
        private final BranchProfile nextProfile = new BranchProfile();
        private final BranchProfile redoProfile = new BranchProfile();

        public EachNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EachNode(EachNode prev) {
            super(prev);
        }

        @Specialization
        public Object each(VirtualFrame frame, FixnumRange range, RubyProc block) {
            int count = 0;

            try {
                outer:
                for (int n = range.getBegin(); n < range.getExclusiveEnd(); n++) {
                    while (true) {
                        if (CompilerDirectives.inInterpreter()) {
                            count++;
                        }

                        try {
                            yield(frame, block, n);
                            continue outer;
                        } catch (BreakException e) {
                            breakProfile.enter();
                            return e.getResult();
                        } catch (NextException e) {
                            nextProfile.enter();
                            continue outer;
                        } catch (RedoException e) {
                            redoProfile.enter();
                        }
                    }
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    ((RubyRootNode) getRootNode()).reportLoopCountThroughBlocks(count);
                }
            }

            return range;
        }

    }

    @CoreMethod(names = "exclude_end?", maxArgs = 0)
    public abstract static class ExcludeEndNode extends CoreMethodNode {

        public ExcludeEndNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExcludeEndNode(ExcludeEndNode prev) {
            super(prev);
        }

        @Specialization
        public boolean excludeEnd(RubyRange range) {
            return range.doesExcludeEnd();
        }

    }

    @CoreMethod(names = "first", maxArgs = 0)
    public abstract static class FirstNode extends CoreMethodNode {

        public FirstNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FirstNode(FirstNode prev) {
            super(prev);
        }

        @Specialization
        public int each(FixnumRange range) {
            return range.getBegin();
        }

        @Specialization
        public Object each(ObjectRange range) {
            return range.getBegin();
        }

    }

    @CoreMethod(names = {"include?", "==="}, maxArgs = 1)
    public abstract static class IncludeNode extends CoreMethodNode {

        @Child protected DispatchHeadNode callLess;
        @Child protected DispatchHeadNode callGreater;
        @Child protected DispatchHeadNode callGreaterEqual;

        public IncludeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            callLess = new DispatchHeadNode(context, getSourceSection(), "<", false, DispatchHeadNode.MissingBehavior.CALL_METHOD_MISSING);
            callGreater = new DispatchHeadNode(context, getSourceSection(), ">", false, DispatchHeadNode.MissingBehavior.CALL_METHOD_MISSING);
            callGreaterEqual = new DispatchHeadNode(context, getSourceSection(), ">=", false, DispatchHeadNode.MissingBehavior.CALL_METHOD_MISSING);
        }

        public IncludeNode(IncludeNode prev) {
            super(prev);
            callLess = prev.callLess;
            callGreater = prev.callGreater;
            callGreaterEqual = prev.callGreaterEqual;
        }

        @Specialization
        public boolean include(FixnumRange range, int value) {
            return value >= range.getBegin() && value < range.getExclusiveEnd();
        }

        @Specialization
        public boolean include(VirtualFrame frame, ObjectRange range, Object value) {
            if ((boolean) callLess.dispatch(frame, value, null, range.getBegin())) {
                return false;
            }

            if (range.doesExcludeEnd()) {
                if ((boolean) callGreaterEqual.dispatch(frame, value, null, range.getEnd())) {
                    return false;
                }
            } else {
                if ((boolean) callGreater.dispatch(frame, value, null, range.getEnd())) {
                    return false;
                }
            }

            return true;
        }
    }

    @CoreMethod(names = "last", maxArgs = 0)
    public abstract static class LastNode extends CoreMethodNode {

        public LastNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LastNode(LastNode prev) {
            super(prev);
        }

        @Specialization
        public int last(FixnumRange range) {
            return range.getEnd();
        }

        @Specialization
        public Object last(ObjectRange range) {
            return range.getEnd();
        }

    }

    @CoreMethod(names = "step", needsBlock = true, minArgs = 1, maxArgs = 1)
    public abstract static class StepNode extends YieldingCoreMethodNode {

        private final BranchProfile breakProfile = new BranchProfile();
        private final BranchProfile nextProfile = new BranchProfile();
        private final BranchProfile redoProfile = new BranchProfile();

        public StepNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StepNode(StepNode prev) {
            super(prev);
        }

        @Specialization
        public Object step(VirtualFrame frame, FixnumRange range, int step, RubyProc block) {
            int count = 0;

            try {
                outer:
                for (int n = range.getBegin(); n < range.getExclusiveEnd(); n += step) {
                    while (true) {
                        if (CompilerDirectives.inInterpreter()) {
                            count++;
                        }

                        try {
                            yield(frame, block, n);
                            continue outer;
                        } catch (BreakException e) {
                            breakProfile.enter();
                            return e.getResult();
                        } catch (NextException e) {
                            nextProfile.enter();
                            continue outer;
                        } catch (RedoException e) {
                            redoProfile.enter();
                        }
                    }
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    ((RubyRootNode) getRootNode()).reportLoopCountThroughBlocks(count);
                }
            }

            return range;
        }

    }

    @CoreMethod(names = "to_a", maxArgs = 0)
    public abstract static class ToANode extends CoreMethodNode {

        public ToANode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToANode(ToANode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray toA(RubyRange range) {
            return range.toArray();
        }

    }

    @CoreMethod(names = "to_s", maxArgs = 0)
    public abstract static class ToSNode extends CoreMethodNode {

        public ToSNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToSNode(ToSNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString toS(RubyRange range) {
            return getContext().makeString(range.toString());
        }
    }

}
