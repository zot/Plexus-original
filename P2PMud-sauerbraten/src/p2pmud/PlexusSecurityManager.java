package p2pmud;

import java.security.AccessControlException;
import java.security.Permission;
import javolution.util.FastSet;

public class PlexusSecurityManager extends SecurityManager {
	private boolean allowAll = false;
	private Object key = new Object();
	private FastSet<Thread> authorizedThreads = new FastSet<Thread>();

	public static void main(String[] args) {
		Object key = install();

		((PlexusSecurityManager)System.getSecurityManager()).uninstall(key);
	}
	public static Object install() {
		PlexusSecurityManager man = new PlexusSecurityManager();

		man.allowAll = true;
		System.setSecurityManager(man);
		man.allowAll = false;
		return man.key;
	}

	public void authorize(Thread t) {
		if (allowAll) {
			authorizedThreads.add(t);
		} else {
			throw new AccessControlException("Access denied attempting to authorize thread with PLEXUS security manager");
		}
	}
	public void uninstall(Object k) {
		if (k == key) {
			allowAll = true;
			System.setSecurityManager(null);
		} else {
			throw new AccessControlException("Access denied attempting to uninstall PLEXUS security manager");
		}
	}
	public void checkPermission(Permission perm) {
		System.out.println(this);
		if (!allowAll && !authorizedThreads.contains(Thread.currentThread())) {
			super.checkPermission(perm);
		}
	}
}
