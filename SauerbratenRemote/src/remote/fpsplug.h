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

extern struct moderation moderator;
