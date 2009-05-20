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

package org.spockframework.runtime.intercept;

import java.lang.annotation.Annotation;

import spock.lang.Timeout;

import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.SpeckInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.MethodKind;

/**
 * Processes @Timeout directives on Specks and Speck methods.
 *
 * @author Peter Niederwieser
 */
public class TimeoutProcessor extends AbstractDirectiveProcessor {
  public void visitSpeckDirective(Annotation directive, SpeckInfo speck) {
    speck.addInterceptor(new TimeoutInterceptor((Timeout)directive));
  }

  public void visitMethodDirective(Annotation directive, MethodInfo method) {
    IMethodInterceptor interceptor = new TimeoutInterceptor((Timeout)directive);
    if (method.getKind() == MethodKind.FEATURE)
      method.getFeature().addInterceptor(interceptor);
    else
      method.addInterceptor(interceptor);
  }
}