/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.rubinius;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;

import org.jruby.truffle.runtime.RubyCallStack;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.backtrace.Backtrace;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.layouts.Layouts;
import org.jruby.truffle.runtime.subsystems.SafepointAction;

import static org.jruby.RubyThread.*;

/**
 * Rubinius primitives associated with the Ruby {@code Thread} class.
 */
public class ThreadPrimitiveNodes {

    @RubiniusPrimitive(name = "thread_raise")
    public static abstract class ThreadRaisePrimitiveNode extends RubiniusPrimitiveNode {

        public ThreadRaisePrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = { "isRubyThread(thread)", "isRubyException(exception)" })
        public DynamicObject raise(DynamicObject thread, final DynamicObject exception) {
            raiseInThread(getContext(), thread, exception, this);
            return nil();
        }

        @TruffleBoundary
        public static void raiseInThread(RubyContext context, DynamicObject rubyThread, final DynamicObject exception, Node currentNode) {
            final Thread javaThread = Layouts.FIBER.getThread((Layouts.THREAD.getFiberManager(rubyThread).getCurrentFiber()));

            context.getSafepointManager().pauseThreadAndExecuteLater(javaThread, currentNode, new SafepointAction() {
                @Override
                public void run(DynamicObject currentThread, Node currentNode) {
                    if (Layouts.EXCEPTION.getBacktrace(exception) == null) {
                        Backtrace backtrace = RubyCallStack.getBacktrace(currentNode);
                        Layouts.EXCEPTION.setBacktrace(exception, backtrace);
                    }
                    throw new RaiseException(exception);
                }
            });
        }

    }

    @RubiniusPrimitive(name = "thread_get_priority")
    public static abstract class ThreadGetPriorityPrimitiveNode extends RubiniusPrimitiveNode {
        public ThreadGetPriorityPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isRubyThread(thread)")
        public int getPriority(DynamicObject thread) {
            final Thread javaThread = Layouts.THREAD.getThread(thread);
            if (javaThread != null) {
                int javaPriority = javaThread.getPriority();
                return javaPriorityToRubyPriority(javaPriority);
            } else {
                return Layouts.THREAD.getPriority(thread);
            }
        }
    }

    @RubiniusPrimitive(name = "thread_set_priority")
    public static abstract class ThreadSetPriorityPrimitiveNode extends RubiniusPrimitiveNode {
        public ThreadSetPriorityPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        @Specialization(guards = "isRubyThread(thread)")
        public int getPriority(DynamicObject thread, int rubyPriority) {
            if (rubyPriority < RUBY_MIN_THREAD_PRIORITY) {
                rubyPriority = RUBY_MIN_THREAD_PRIORITY;
            } else if (rubyPriority > RUBY_MAX_THREAD_PRIORITY) {
                rubyPriority = RUBY_MAX_THREAD_PRIORITY;
            }

            int javaPriority = rubyPriorityToJavaPriority(rubyPriority);
            final Thread javaThread = Layouts.THREAD.getThread(thread);
            if (javaThread != null) {
                javaThread.setPriority(javaPriority);
            }
            Layouts.THREAD.setPriority(thread, rubyPriority);
            return rubyPriority;
        }
    }

}
