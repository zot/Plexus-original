/*
 * Copyright (c) 2008 TEAM CTHULHU, Bill Burdick, Roy Riggs
 * Plexus is licensed under the ZLIB license (http://www.opensource.org/licenses/zlib-license.php):
 */
enum HIT_TYPE {NONE, RAY, PROJECTILE};

struct moderation {
	enum HIT_TYPE hittype;
	void *weaponstate;
	char *rayhitcode;
	char *projectilehitcode;
	int damage;
	dynent *shooter;
	fpsent *target;
	vec velocity;
	int gun;
	int info;

	moderation();
	void rayhit(/*fpsclient::weaponstate*/void *w, int damage, dynent *d, fpsent *at, const vec &vel, int gun, int info);
	void projectilehit(/*fpsclient::weaponstate*/void *w, int damage, dynent *d, fpsent *at, const vec &vel, int gun, int info);
	void defaulthit();
};
FVAR(tc_kickback, 0);
VAR(tc_useammo, 0, 0, 1);
extern struct moderation moderator;
