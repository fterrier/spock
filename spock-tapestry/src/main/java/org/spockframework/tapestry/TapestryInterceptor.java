/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spockframework.tapestry;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import org.apache.tapestry5.ioc.*;
import org.apache.tapestry5.ioc.annotations.Autobuild;
import org.apache.tapestry5.ioc.annotations.Inject;

import org.spockframework.runtime.extension.*;
import org.spockframework.runtime.model.FieldInfo;
import org.spockframework.runtime.model.SpecInfo;
import org.spockframework.util.*;

import spock.lang.Shared;
import spock.lang.Specification;

/**
 * Controls creation, startup and shutdown of the Tapestry container,
 * and injects specifications with Tapestry-provided objects.
 *
 * @author Peter Niederwieser
 */
// Important implementation detail: Only the fixture methods of
// spec.getTopSpec() are intercepted (see TapestryExtension)
public class TapestryInterceptor extends AbstractMethodInterceptor {
  private final SpecInfo spec;
  private final Set<Class<?>> modules;

  private Registry registry;
  private IPerIterationManager perIterationManager;

  public TapestryInterceptor(SpecInfo spec, Set<Class<?>> modules) {
    this.spec = spec;
    this.modules = modules;
  }

  @Override
  public void interceptSharedInitializerMethod(IMethodInvocation invocation) throws Throwable {
    runBeforeRegistryCreatedMethods((Specification) invocation.getTarget());
    createAndStartupRegistry();
    injectServices(invocation.getTarget(), true);
    invocation.proceed();
  }

  @Override
  public void interceptCleanupSpecMethod(IMethodInvocation invocation) throws Throwable {
    try {
      invocation.proceed();
    } finally {
      shutdownRegistry();
    }
  }

  @Override
  public void interceptInitializerMethod(IMethodInvocation invocation) throws Throwable {
    injectServices(invocation.getTarget(), false);
    invocation.proceed();
  }

  @Override
  public void interceptCleanupMethod(IMethodInvocation invocation) throws Throwable {
    try {
      invocation.proceed();
    } finally {
      terminateIterationScope();
    }
  }

  private void runBeforeRegistryCreatedMethods(Specification spec) {
    Object returnValue;

    for (Method method : findAllBeforeRegistryCreatedMethods()) {
      returnValue = ReflectionUtil.invokeMethod(spec, method);

      // Return values of a type other than Registry are silently ignored.
      // This avoids problems in case the method unintentionally returns a
      // value (which is common due to Groovy's implicit return).
      if (returnValue instanceof Registry)
        registry = (Registry) returnValue;
    }
  }

  private void createAndStartupRegistry() {
    if (registry != null) return;

    RegistryBuilder builder = new RegistryBuilder();
    builder.add(ExtensionModule.class);
    for (Class<?> module : modules) builder.add(module);
    registry = builder.build();
    registry.performRegistryStartup();
    perIterationManager = registry.getService(IPerIterationManager.class);
  }

  private List<Method> findAllBeforeRegistryCreatedMethods() {
    List<Method> methods = new ArrayList<Method>();

    for (SpecInfo curr : spec.getSpecsTopToBottom()) {
      Method method = ReflectionUtil.getDeclaredMethodByName(curr.getReflection(), "beforeRegistryCreated");
      if (method != null) {
        method.setAccessible(true);
        methods.add(method);
      }
    }

    return methods;
  }

  private void injectServices(Object target, boolean sharedFields) throws IllegalAccessException {
    for (final FieldInfo field : spec.getAllFields())
      if ((field.getReflection().isAnnotationPresent(Inject.class)
          || field.getReflection().isAnnotationPresent(Autobuild.class))
            && field.getReflection().isAnnotationPresent(Shared.class) == sharedFields) {
        Field rawField = field.getReflection();
        Object value = registry.getObject(rawField.getType(), createAnnotationProvider(field));
        rawField.setAccessible(true);
        rawField.set(target, value);
      }
  }

  private AnnotationProvider createAnnotationProvider(final FieldInfo field) {
    return new AnnotationProvider() {
      public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return field.getReflection().getAnnotation(annotationClass);
      }
    };
  }

  private void shutdownRegistry() {
    if (registry != null)
      registry.shutdown();
  }

  private void terminateIterationScope() {
    if (perIterationManager != null)
      perIterationManager.cleanup();
  }
}
