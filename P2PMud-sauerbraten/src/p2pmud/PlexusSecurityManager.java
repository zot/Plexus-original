package p2pmud;

import groovy.security.GroovyCodeSourcePermission;
import groovy.util.GroovyScriptEngine;
import java.io.File;
import java.io.FilePermission;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlException;
import java.security.Permission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.PropertyPermission;
import javolution.util.FastSet;
import sun.security.util.SecurityConstants;

public class PlexusSecurityManager extends SecurityManager {
	private ThreadLocal<Boolean> allowAll = new ThreadLocal<Boolean>();
	private Object key = new Object();
	private FastSet<Thread> suspiciousThreads = new FastSet<Thread>();
	private FastSet<Permission> permissions = new FastSet<Permission>(new HashSet<Permission>(Arrays.asList(allowed)));

	private static final Permission allowed[] = {
		new RuntimePermission("accessClassInPackage.sun.reflect"),
		SecurityConstants.CHECK_MEMBER_ACCESS_PERMISSION,
		new GroovyCodeSourcePermission("/groovy/script"),
		new GroovyCodeSourcePermission("/groovy/shell"),
		SecurityConstants.CREATE_CLASSLOADER_PERMISSION,
		new PropertyPermission("groovyjarjarantlr.ast", SecurityConstants.PROPERTY_READ_ACTION),
		new PropertyPermission("groovy.ast", SecurityConstants.PROPERTY_READ_ACTION),
	};
	public static final PlexusSecurityManager soleInstance = new PlexusSecurityManager();
	private static final String fileReadPrefixes[] = {
		new File(filePath(PlexusSecurityManager.class.getResource("/Props.class"))).getParent(),
		filePath(GroovyScriptEngine.class.getResource("GroovyScriptEngine.class")),
		"/groovy/script",
		"/groovy/shell",
	};

	public static String filePath(URL url) {
		if (url.getProtocol().equals("jar")) {
			String pref;
			try {
				pref = filePath(new URL(url.getPath()));
				return pref.substring(0, pref.indexOf('!'));
			} catch (MalformedURLException e) {
				throw new RuntimeException("Couldn't get path from URL", e);
			}
		}
		return url.getPath();
	}
	public static void main(String[] args) {
		install();
		uninstall(soleInstance.key);
	}
	public static Object install() {
		soleInstance.setAllowAll(true);
		System.setSecurityManager(soleInstance);
		soleInstance.setAllowAll(false);
		return soleInstance.key;
	}
	public static void uninstall(Object k) {
		soleInstance._uninstall(k);
	}
	public static Object getKey() {
		if (System.getSecurityManager() != null) {
			throw new AccessControlException("Access denied attempting to retrieve key for PLEXUS security manager");
		}
		return soleInstance.key;
	}
	public static void runSandboxed(Runnable block) {
		if (System.getSecurityManager() != null) {
			block.run();
		} else {
			install();
			block.run();
			uninstall(soleInstance.key);
		}
	}
	public static void runAuthorized(Object key, Runnable block) {
		if (System.getSecurityManager() == null) {
			block.run();
		} else {
			boolean oldAllow = soleInstance.allowAll.get();
			
			soleInstance.setAllowAll(true);
			block.run();
			soleInstance.setAllowAll(oldAllow);
		}
	}
	public static void checkPermission(String perm) {
		SecurityManager sm = System.getSecurityManager();
		
		if (sm != null) {
			sm.checkPermission(new RuntimePermission(perm));
		}
	}

	public PlexusSecurityManager() {
		setAllowAll(false);
	}
	public void _uninstall(Object k) {
		if (k == key) {
			setAllowAll(true);
			System.setSecurityManager(null);
		} else {
			throw new AccessControlException("Access denied attempting to uninstall PLEXUS security manager");
		}
	}
	private boolean setAllowAll(boolean value) {
		Object old = allowAll.get();
		
		allowAll.set(value);
		return old != null && (Boolean)old;
	}
	public boolean isAuthorized() {
		Object o = allowAll.get();

		return o == null || ((Boolean)o).booleanValue() || !suspiciousThreads.contains(Thread.currentThread());
	}
	public boolean isOk(Permission perm) {
		if (permissions.contains(perm)) {
			return true;
		}
		if (perm instanceof FilePermission) {
			FilePermission p = (FilePermission) perm;
			
			if (p.getActions().equals("read")) {
				for (String path : fileReadPrefixes) {
					if (p.getName().startsWith(path)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	public void addSuspiciousThread(Thread t, Object givenKey) {
		if (!isAuthorized() && key != givenKey) {
			throw new AccessControlException("Access denied attempting to add suspicious thread to PLEXUS security manager");
		}
		suspiciousThreads.add(t);
	}
	public void checkPermission(Permission perm) {
		if (!isAuthorized() && !isOk(perm)) {
//			new Exception("BORK: " + perm).printStackTrace();
			super.checkPermission(perm);
//		} else {
//			System.out.println("AUTHORIZED: " + perm);
		}
	}
}
