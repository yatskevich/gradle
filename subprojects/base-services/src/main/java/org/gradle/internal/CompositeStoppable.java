/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CompositeStoppable implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeStoppable.class);
    private final List<Stoppable> elements = new CopyOnWriteArrayList<Stoppable>();

    public CompositeStoppable() {
    }

    public static CompositeStoppable stoppable(Object... elements) {
        return new CompositeStoppable().add(elements);
    }

    public static CompositeStoppable stoppable(Iterable<?> elements) {
        return new CompositeStoppable().add(elements);
    }

    public static CompositeStoppable closeable(Closeable... elements) {
        return new CompositeStoppable().add(elements);
    }

    public static CompositeStoppable closeable(Iterable<? extends Closeable> elements) {
        return new CompositeStoppable().add(elements);
    }

    public CompositeStoppable add(Iterable<?> elements) {
        for (Object element : elements) {
            this.elements.add(toStoppable(element));
        }
        return this;
    }

    public CompositeStoppable add(Object... elements) {
        for (Object closeable : elements) {
            this.elements.add(toStoppable(closeable));
        }
        return this;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static Stoppable toStoppable(final Object object) {
        if (object == null) {
            return new Stoppable() {
                public void stop() {
                }
            };
        }
        if (object instanceof Iterable) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
        if (object instanceof Stoppable) {
            return (Stoppable) object;
        }
        if (object instanceof Closeable) {
            final Closeable closeable = (Closeable) object;
            return new Stoppable() {
                @Override
                public String toString() {
                    return closeable.toString();
                }

                public void stop() {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            };
        }
        return new Stoppable() {
            @Override
            public String toString() {
                return object.toString();
            }

            public void stop() {
                try {
                    invoke(object.getClass().getMethod("stop"), object);
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                try {
                    invoke(object.getClass().getMethod("close"), object);
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
        };
    }

    public void stop() {
        Throwable failure = null;
        try {
            for (Stoppable element : elements) {
                try {
                    element.stop();
                } catch (Throwable throwable) {
                    if (failure == null) {
                        failure = throwable;
                    } else {
                        LOGGER.debug(String.format("Could not stop %s.", element), throwable);
                    }
                }
            }
        } finally {
            elements.clear();
        }

        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }

    /**
     * Similar to stop() but forwards the IOException from Closeables.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        try {
            stop();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }
}