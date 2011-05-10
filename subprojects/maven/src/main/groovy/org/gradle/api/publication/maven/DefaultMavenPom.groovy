/*
* Copyright 2011 the original author or authors.
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
package org.gradle.api.publication.maven

import org.codehaus.groovy.runtime.InvokerHelper

class DefaultMavenPom implements MavenPom {
    void apply(Closure pomBuilder) {
        CustomModelBuilder modelBuilder = new CustomModelBuilder(getModel());
        InvokerHelper.invokeMethod(modelBuilder, "project", pomBuilder);
    }

    void whenConfigured(Closure modelTransformer) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    void withXml(Closure xmlBuilder) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
