/*
 * Copyright 2003-2007 the original author or authors.
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
package org.codehaus.groovy.reflection;

import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ReflectPermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

public class CachedField extends MetaProperty {
    public final Field field;

    CachedClass cachedClass;
    boolean alreadySetAccessible;

    public CachedField(CachedClass clazz, Field field) {
        super (field.getName(), field.getType());
        this.field = field;
        cachedClass = clazz;
        alreadySetAccessible = Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers());
    }

    public boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    public int getModifiers() {
        return field.getModifiers();
    }

    boolean checked = false;
    boolean notPublic = true;
    private static final java.security.Permission ACCESS_PERMISSION = new ReflectPermission("suppressAccessChecks");
    private void checkAccess() {
    	if (!checked) {
    		checked = true;
    		notPublic = !Modifier.isPublic(field.getModifiers());
    	}
    	if (notPublic) {
    		SecurityManager sm = System.getSecurityManager();
    		if (sm != null) sm.checkPermission(ACCESS_PERMISSION);
    	}
    }
    /**
     * @return the property of the given object
     * @throws Exception if the property could not be evaluated
     */
    public Object getProperty(final Object object) {
        if ( !alreadySetAccessible ) {
        	if (!field.isAccessible()) {
        		AccessController.doPrivileged(new PrivilegedAction() {
        			public Object run() {
        				field.setAccessible(true);
        				return null;
        			}
        		});
        	}
            alreadySetAccessible = true;
        }

        try {
       		checkAccess();
            return field.get(object);
        } catch (IllegalAccessException e) {
            throw new GroovyRuntimeException("Cannot get the property '" + name + "'.", e);
        }
    }

    /**
     * Sets the property on the given object to the new value
     *
     * @param object on which to set the property
     * @param newValue the new value of the property
     * @throws RuntimeException if the property could not be set
     */
    public void setProperty(final Object object, Object newValue) {
        final Object goalValue = DefaultTypeTransformation.castToType(newValue, field.getType());

        if ( !alreadySetAccessible ) {
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    field.setAccessible(true);
                    return null;
                }
            });
            alreadySetAccessible = true;
        }

        try {
            field.set(object, goalValue);
        } catch (IllegalAccessException ex) {
            throw new GroovyRuntimeException("Cannot set the property '" + name + "'.", ex);
        }
    }
}
