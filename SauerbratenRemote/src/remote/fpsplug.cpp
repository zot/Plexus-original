// plug in to fps.cpp
extern igameclient *cl;
struct moderation moderator;
char idbuf[1024];

#define fpscl ((fpsclient *)cl)
#ifdef WINDOWS
#define snprintf _snprintf
#endif

/////
// MEMBERS FOR: dynent
/////
void dynent::renderplayer(fpsclient &cl, void *void_mdl, int team) {
	fpsclient::playermodelinfo &mdl = *((fpsclient::playermodelinfo *) void_mdl);
	fpsent *d = (fpsent *)this;
	//const char *model = modelname ? modelname : modelnames[mdl];

//	printf("render player (%s) %s\n", name, suggestion);
	if(d->state!=CS_DEAD || d->superdamage<50) {
		cl.fr.renderplayer((fpsent*)this, mdl, team);
	}
	//if (fiddlies) {
	//	s_strcpy(d->info, cl.colorname(d, NULL, "@"));
	//	if(d->maxhealth>100) { s_sprintfd(sn)(" +%d", d->maxhealth-100); s_strcat(d->info, sn); }
	//	if(d->state!=CS_DEAD) particle_text(d->abovehead(), d->info, mdl ? (mdl==1 ? 16 : 13) : 11, 1);
	//}
}

void dynent::rendermonster(fpsclient &cl) {
	fpsclient::monsterset::monster *m = (fpsclient::monsterset::monster *)this;

	modelattach vwep[] = { { m->monstertypes[m->mtype].vwepname, "tag_weapon", ANIM_VWEP|ANIM_LOOP, 0 }, { NULL } };
    renderclient(m, m->monstertypes[m->mtype].mdlname, vwep, m->monsterstate==M_ATTACKING ? -ANIM_SHOOT : 0, 300, m->lastaction, m->lastpain);
    //modelattach vwep[] = { { m->monstertypes[m->mtype].vwepname, MDL_ATTACH_VWEP, ANIM_VWEP|ANIM_LOOP, 0 }, { NULL } };
    //renderclient(m, m->monstertypes[m->mtype].mdlname, vwep, m->monsterstate==M_ATTACKING ? -ANIM_SHOOT : 0, 300, m->lastaction, m->lastpain);
}
void dynent::rendermovable(fpsclient &cl, vec dir, const char *suggestion) {
	rendermodel(NULL, suggestion, ANIM_MAPMODEL|ANIM_LOOP, o, this->yaw, 0, MDL_LIGHT | MDL_SHADOW | MDL_CULL_VFC | MDL_CULL_DIST | MDL_CULL_OCCLUDED, this);
	//rendermodel(color, dir, suggestion, ANIM_MAPMODEL|ANIM_LOOP, 0, 0, o, yaw, 0, 0, 0, this, MDL_SHADOW | MDL_CULL_VFC | MDL_CULL_DIST | MDL_CULL_OCCLUDED, NULL);
}

enum ENT_TYPE {TC_ERROR = 'e', TC_PLAYER = 'p', TC_MONSTER = 'm', TC_ITEM = 'i'};


char *idfor(void *ent, char *buf, int buflen) {
	if (ent == fpscl->player1)
	{
		snprintf(buf, buflen, "p%d", fpscl->player1->tc_id);
		return buf;
	}

	for (int i = 0; i < fpscl->players.length(); ++i)
	{
		fpsent *p = fpscl->players[i];
		if (ent == p)
		{
			snprintf(buf, buflen, "p%d", p->tc_id);
			return buf;
		}
	}

	for (int j = 0; j < fpscl->ms.monsters.length(); ++j)
	{
		fpsclient::monsterset::monster *m = fpscl->ms.monsters[j];
		if (ent == m)
		{
			snprintf(buf, buflen, "m%d", m->tc_id);
			return buf;
		}
	}

	for (int k = 0; k < fpscl->mo.movables.length(); ++k) {
		fpsclient::movableset::movable *mv = fpscl->mo.movables[k];
		if (ent == mv)
		{
			snprintf(buf, buflen, "i%d", mv->tc_id);
			return buf;
		}
	}

	strncpy(buf, "error", buflen);
	return buf;
}


fpsent *getplayer(char *id) {
	if (id[0] == TC_PLAYER) {
		int tc_id = atoi(id + 1);

		if (fpscl->player1->tc_id == tc_id) return fpscl->player1;

		for (int i = 0; i < fpscl->players.length(); ++i)
		{
			fpsent *p = fpscl->players[i];
			if (p->tc_id == tc_id) return p;
		}
	}
	return NULL;
}

fpsclient::monsterset::monster *getmonster(char *id) {
	if (id[0] == TC_MONSTER) {
		int tc_id = atoi(id + 1);
		for (int i = 0; i < fpscl->ms.monsters.length(); ++i)
		{
			fpsclient::monsterset::monster *m = fpscl->ms.monsters[i];
			if (m->tc_id == tc_id) return m;
		}
	}
	return NULL;
}

fpsclient::movableset::movable *getitem(char *id) {
	if (id[0] == TC_ITEM) {
		int tc_id = atoi(id + 1);

		for (int i = 0; i < fpscl->mo.movables.length(); ++i) {
			fpsclient::movableset::movable *mv = fpscl->mo.movables[i];
			if (mv->tc_id == tc_id) return mv;
		}
	}
	return NULL;
}

dynent *getdynent(char *id) {
	switch (id[0]) {
	case TC_ITEM:
		return getitem(id);
	case TC_MONSTER:
		return getmonster(id);
	case TC_PLAYER:
		return getplayer(id);
	default:
		return NULL;
	}
}

void dumpent(char *id) {
	if (id && id[0]) {
		switch (id[0]) {
		case TC_PLAYER:
			fpsent *p;
			
			p = getplayer(id);
			if (p) {
				conoutf("player: %s %s %s", id, p->name ? p->name : "NO NAME", p->modelname ? p->modelname : "NO MODEL");
			} else {
				conoutf("bad player id");
			}
			break;
		case TC_MONSTER:
			fpsclient::monsterset::monster *m;
			
			m = getmonster(id);
			if (m) {
				conoutf("monster: %s %s", id, m->monstertypes[m->mtype].mdlname);
			} else {
				conoutf("bad monster id");
			}
			break;
		case TC_ITEM:
			fpsclient::movableset::movable *mov;
			
			mov = getitem(id);
			if (mov) {
				conoutf("item: %s %s", id, mapmodelname(mov->mapmodel));
			} else {
				conoutf("bad item id");
			}
			break;
		default:
			conoutf("unknown entity type: %c, should start with p, m, or i", id[0]);
			break;
		}
	} else {
		conoutf("no entity id given");
	}
}
ICOMMAND(dumpent, "s", (char *id), dumpent(id));

ICOMMAND(dumpents, "", (),
	printf("Entities...\n");

	char buf[25];

	snprintf(buf, sizeof(buf), "p%d", fpscl->player1->tc_id);
	dumpent(buf);

	for (int i = 0; i < fpscl->players.length(); ++i)
	{
		fpsent *p = fpscl->players[i];
		snprintf(buf, sizeof(buf), "p%d", p->tc_id);

		dumpent(buf);
	}

	for (int j = 0; j < fpscl->ms.monsters.length(); ++j)
	{
		fpsclient::monsterset::monster *m = fpscl->ms.monsters[j];
		snprintf(buf, sizeof(buf), "m%d", m->tc_id);

		dumpent(buf);
	}

	for (int k = 0; k < fpscl->mo.movables.length(); ++k) {
		fpsclient::movableset::movable *mv = fpscl->mo.movables[k];
		snprintf(buf, sizeof(buf), "i%d", mv->tc_id);

		dumpent(buf);
	}
);

ICOMMAND(currentweapon, "", (), {
	intret(fpscl->player1->gunselect);
});

/////
// MEMBERS FOR: dynent
/////
moderation::moderation(): rayhitcode(NULL), projectilehitcode(NULL) {}
void moderation::defaulthit() {
	((fpsclient::weaponstate*)weaponstate)->hit(damage, shooter, target, velocity, gun, info);
}
void moderation::rayhit(/*fpsclient::weaponstate*/void *w, int damage, dynent *d, fpsent *at, const vec &vel, int gun, int info) {
	hittype = RAY;
	weaponstate = w;
	this->damage = damage;
	shooter = d;
	target = at;
	velocity = vel;
	this->gun = gun;
	this->info = info;
	if (rayhitcode) {
		executeret(rayhitcode);
	} else {
		defaulthit();
	}
	hittype = NONE;
	weaponstate = NULL;
	shooter = NULL;
	target = NULL;
}
void moderation::projectilehit(/*fpsclient::weaponstate*/void *w, int damage, dynent *d, fpsent *at, const vec &vel, int gun, int info) {
	hittype = PROJECTILE;
	weaponstate = w;
	this->damage = damage;
	shooter = d;
	target = at;
	velocity = vel;
	this->gun = gun;
	this->info = info;
	if (projectilehitcode) {
		executeret(projectilehitcode);
	} else {
		defaulthit();
	}
	hittype = NONE;
	target = NULL;
	weaponstate = NULL;
	shooter = NULL;
}
ICOMMAND(defaulthit, "", (),
	if (moderator.hittype != NONE) {
		moderator.defaulthit();
	}
);
ICOMMAND(onrayhit, "s", (char *code),
	if (moderator.rayhitcode) {
		delete[] moderator.rayhitcode;
	}
	moderator.rayhitcode = code[0] ? newstring(code) : NULL;
);
ICOMMAND(onprojectilehit, "s", (char *code),
	if (moderator.projectilehitcode) {
		delete[] moderator.projectilehitcode;
	}
	moderator.projectilehitcode = code[0] ? newstring(code) : NULL;
);

ICOMMAND(psay, "ss", (char *ent, char *txt), {
       fpsent *d = (fpsent*)getdynent(ent);

       if (d) {
               s_sprintfd(ds)("@%s", txt);
               particle_text(d->abovehead(), ds, 16, 10000);
               conoutf("%s:\f0 %s", fpscl->colorname(d), txt);
       }
});

ICOMMAND(mfreeze, "s", (char *ent), {
       if (ent && ent[0] == 'm') {
               fpsclient::monsterset::monster *m = getmonster(ent);

               if (m) {
                       m->monsterstate = M_NONE;
               }
       }
});

ICOMMAND(createplayer, "s", (char *ent), {
	if (ent && ent[0] == 'p') {
		// if we try to recreate a new player of the same id, just reuse the existing one
		fpsent *p = getplayer(ent);
		if (p == fpscl->player1) {
			conoutf("/createplayer error: cannot recreate original player");
			return;
		}
		if (NULL == p) p = fpscl->newclient(fpscl->players.length());
		else conoutf("/createplayer warning: reusing existing player %s", ent);
		fpscl->spawnplayer(p);
        //findplayerspawn(p, -1, 0);
        //fpscl.spawnstate(p);
		p->tc_id = atoi(ent + 1);

    }
});

ICOMMAND(createmonster, "s", (char *ent), {
	if (ent && ent[0] == 'm') {
		fpsent *p = new fpsent();
        findplayerspawn(p, -1, 0);
        //spawnstate(p);
		p->tc_id = atoi(ent + 1);

    }
});
ICOMMAND(monsterstate, "s", (char *ent), {
	if (ent && ent[0] == 'm') {
		fpsclient::monsterset::monster *m = getmonster(ent);

		if (m) {
			sprintf(idbuf, "%d", m->monsterstate);
			result(idbuf);
		}
	}
});

static void hit_shooter() {
	if (moderator.hittype != NONE) {
		result(idfor(moderator.shooter, idbuf, sizeof (idbuf)));
	}
}

static void hit_target() {
	if (moderator.hittype != NONE) {
		result(idfor(moderator.target, idbuf, sizeof (idbuf)));
	}
}

static void hit_damage() {
	if (moderator.hittype != NONE) {
		intret(moderator.damage);
	}
}

static void hit_type() {
	intret(moderator.hittype);
}

static void hit_gun() {
	if (moderator.hittype != NONE) {
		intret(moderator.gun);
	}
}

static void hit_info() {
	if (moderator.hittype != NONE) {
		intret(moderator.info);
	}
}

static bool initfpsplug() {
	addcommand("hit.shooter", hit_shooter, "");
	addcommand("hit.target", hit_target, "");
	addcommand("hit.damage", hit_damage, "");
	addcommand("hit.type", hit_type, "");
	addcommand("hit.gun", hit_gun, "");
	addcommand("hit.info", hit_info, "");
	return true;
}

bool plug_fpspluginitialized = initfpsplug();
