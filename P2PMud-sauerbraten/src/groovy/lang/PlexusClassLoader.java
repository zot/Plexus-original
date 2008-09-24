package groovy.lang;

import groovy.util.ResourceConnector;
import groovy.util.ResourceException;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import p2pmud.PlexusSecurityManager;

public class PlexusClassLoader extends GroovyClassLoader {
	private Object key;
	private ResourceConnector rc;

    private static ThreadLocal currentCacheEntryHolder = new ThreadLocal();

    private static class ScriptCacheEntry {
        public Class scriptClass;
        public long lastModified;
        public Map dependencies = new HashMap();
    }
    
    @SuppressWarnings("unchecked")
	public static PlexusClassLoader create(final ResourceConnector rc, final ClassLoader parentClassLoader, final Object key) {
        PlexusClassLoader loader = (PlexusClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return new PlexusClassLoader(parentClassLoader) {
					protected Class findClass(String className) throws ClassNotFoundException {
                        String filename = className.replace('.', File.separatorChar) + ".groovy";
                        URLConnection dependentScriptConn = null;
                        try {
                            dependentScriptConn = rc.getResourceConnection(filename);
                            ScriptCacheEntry currentCacheEntry = (ScriptCacheEntry) currentCacheEntryHolder.get();
                            currentCacheEntry.dependencies.put(
                                    dependentScriptConn.getURL(),
                                    new Long(dependentScriptConn.getLastModified()));
                            return parseClass(dependentScriptConn.getInputStream(), filename);
                        } catch (ResourceException e1) {
                            throw new ClassNotFoundException("Could not read " + className + ": " + e1);
                        } catch (CompilationFailedException e2) {
                            throw new ClassNotFoundException("Syntax error in " + className + ": " + e2);
                        } catch (IOException e3) {
                            throw new ClassNotFoundException("Problem reading " + className + ": " + e3);
                        } finally {
                            try {
                                if (dependentScriptConn != null && dependentScriptConn.getInputStream() != null) {
                                    dependentScriptConn.getInputStream().close();
                                }
                            } catch (IOException e) {
                                // IGNORE
                            }
                        }
                    }
                };
            }
        });

        loader.key = key;
        loader.rc = rc;
        return loader;
	}
	
	public PlexusClassLoader(ClassLoader loader) {
		super(loader);
	}
    protected CompilationUnit createCompilationUnit(CompilerConfiguration config, CodeSource source) {
        return new PlexusCompilationUnit(config, source, this, key);
    }
    public static class PlexusCompilationUnit extends CompilationUnit {
    	private Object key;

    	public PlexusCompilationUnit(CompilerConfiguration configuration, CodeSource security, GroovyClassLoader loader, Object key) {
			super(configuration, security, loader);
		}
		@Override
		public void addPhaseOperation(SourceUnitOperation op, int phase) {
			if (phase == Phases.PARSING) {
				op = new SourceUnitOperation() {
		            public void call(final SourceUnit source) throws CompilationFailedException {
		            	PlexusSecurityManager.runAuthorized(key, new Runnable() {
							public void run() {
								source.parse();
							}
						});
		            }
				};
			}
			super.addPhaseOperation(op, phase);
		}
    }
}
