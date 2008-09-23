/*
 * Copyright (c) 2008 TEAM CTHULHU, Bill Burdick, Roy Riggs
 * Plexus is licensed under the ZLIB license (http://www.opensource.org/licenses/zlib-license.php):
 */
#include "tc.h"

// plug in to fps.cpp
extern igameclient *cl;
struct moderation moderator;
char idbuf[1024];

#define fpscl ((fpsclient *)cl)

/////
// MEMBERS FOR: dynent
/////
void dynent::renderplayer(fpsclient &cl, const char *mdlname, void *attachments, int attack, int attackdelay, int lastaction, int lastpain, float sink) {
	fpsent *ent = (fpsent *)this;
	if(ent->state!=CS_DEAD) {
		mdlname = ent->modelname;
		if (!mdlname || *mdlname == '\0') mdlname = "mrfixit";

		attachments = NULL;		// this will turn off all weapons
        renderclient(ent, mdlname, (modelattach *) attachments, attack, attackdelay, lastaction, lastpain, sink);

		vec v = ent->abovehead().add(vec(0, 0, 2));
		if (ent->name[0]) particle_text(v, ent->name, 24, 1);

		if (ent->team[0]) {
			particle_text(v.sub(vec(0, 0, 2)),  ent->team, 24, 1);
		}
		// could make a 'health bar' with this:
		//particle_meter(d->abovehead(), 0.75, 17);
	}
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

enum ENT_TYPE {TC_ERROR = 'E', TC_PLAYER = 'p', TC_MONSTER = 'm', TC_ITEM = 'i', TC_ENT = 'e'};


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

void deleteplayer(fpsent *p)
{
	for (int i = 0; i < fpscl->players.length(); ++i)
	{
		fpsent *who = fpscl->players[i];
		if (who && who == p) {
			delete who;
			fpscl->players[i] = 0;
			return;
		}
	}

}

fpsent *getplayer(char *id) {
	if (id[0] == TC_PLAYER) {
		int tc_id = atoi(id + 1);

		if (fpscl->player1->tc_id == tc_id) return fpscl->player1;

		for (int i = 0; i < fpscl->players.length(); ++i)
		{
			fpsent *p = fpscl->players[i];
			if (p && p->tc_id == tc_id) return p;
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
			if (m && m->tc_id == tc_id) return m;
		}
	}
	return NULL;
}

extentity *getent(char *id) {
	if (id[0] == TC_ENT) {
		int tc_id = atoi(id + 1);

		loopv(fpscl->et.ents) {
			extentity *ent = fpscl->et.ents[i];

			if (ent->tc_id == tc_id) return ent;
		}
	}
	return NULL;
}

fpsclient::movableset::movable *getitem(char *id) {
	if (id[0] == TC_ITEM) {
		int tc_id = atoi(id + 1);

		for (int i = 0; i < fpscl->mo.movables.length(); ++i) {
			fpsclient::movableset::movable *mv = fpscl->mo.movables[i];
			if (mv && mv->tc_id == tc_id) return mv;
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
		if (p) {
			snprintf(buf, sizeof(buf), "p%d", p->tc_id);
			dumpent(buf);
		}
	}

	for (int j = 0; j < fpscl->ms.monsters.length(); ++j)
	{
		fpsclient::monsterset::monster *m = fpscl->ms.monsters[j];
		if (m) {
			snprintf(buf, sizeof(buf), "m%d", m->tc_id);
			dumpent(buf);
		}
	}

	for (int k = 0; k < fpscl->mo.movables.length(); ++k) {
		fpsclient::movableset::movable *mv = fpscl->mo.movables[k];
		if (mv) {
			snprintf(buf, sizeof(buf), "i%d", mv->tc_id);
			dumpent(buf);
		}
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
			   vec high = d->abovehead().add(vec(0, 0, d->aboveeye+2));
               particle_text(high, ds, 16, 10000);
               conoutf("%s:\f0 %s", fpscl->colorname(d), txt);
       }
});

ICOMMAND(mfreeze, "s", (char *ent), {
       if (ent && ent[0] == TC_MONSTER) {
               fpsclient::monsterset::monster *m = getmonster(ent);

               if (m) {
                       m->monsterstate = M_NONE;
               }
       }
});
//this can have 1, 2, 3, or 4 args
ICOMMAND(createitem, "sissi", (char *ent, int *triggerType, char *x, char *y, int *z), {
	if (ent && ent[0] == 'e') {
		extentity *t = getent(ent);
		vec pos;
		int numArgs = !*x ? 2 : !*y ? 3 : 4;
		int trig = *triggerType;

		if (NULL == t) {
			if (numArgs > 2) {
				pos.x = numArgs == 4 ? atoi(x) : *triggerType;
				pos.y = numArgs == 4 ? atoi(y) : atoi(x);
				pos.z = numArgs == 4 ? *z : atoi(y);
				if (numArgs == 3) {
					trig = 0;
				}
			} else {
				pos = fpscl->player1->o;
			}
			conoutf("num args: %d, coord args: [%d, %d, %d]\n", numArgs, pos.x, pos.y, pos.z);
			extern void tc_newentity(vec &o, int type, int a1, int a2, int a3, int a4);
			tc_newentity(pos, ET_MAPMODEL, 0, trig, numArgs != 3 ? 12345 : 0, 0);
			t = fpscl->et.getents()[fpscl->et.getents().length() - 1];
			conoutf("trigger type: %d, number: %d, coords: [%d, %d, %d]\n", t->attr3, t->attr4, t->o.x, t->o.y, t->o.z);
			t->tc_id = atoi(ent + 1);
			intret(1);
		} else {
			conoutf("/createitem error: attempt to reuse existing item %s", ent);
			intret(0);
		}
    } else if (!ent) {
    	conoutf("/createitem error: No item id given");
    } else {
    	conoutf("/createitem error: Wrong id format. Expected e<number>.");
    }
});

ICOMMAND(createplayer, "ss", (char *ent, char *name), {
	if (ent && ent[0] == TC_PLAYER) {
		// if we try to recreate a new player of the same id, just reuse the existing one
		fpsent *p = getplayer(ent);
		if (p == fpscl->player1) {
			conoutf("/createplayer error: cannot recreate original player");
			return;
		}
		if (NULL == p) p = fpscl->newclient(fpscl->players.length());
		else conoutf("/createplayer warning: reusing existing player %s", ent);

		if (name && name[0]) strncpy(p->name, name, sizeof(p->name));
		fpscl->spawnplayer(p);
		p->state = CS_ALIVE;
		p->tc_id = atoi(ent + 1);
		//conoutf("Welcome player %s to this world", p->name);
    }
});

ICOMMAND(deleteallplayers, "", (), {
		for (int i = fpscl->players.length() - 1; i >= 0; --i)
		{
			fpsent *p = fpscl->players[i];
			if (p) {
				extern void deleteplayer(fpsent *p);
				deleteplayer(p);
				extern void unlinkWatcher(fpsent *p);
				unlinkWatcher(p);
			}
		}
});

ICOMMAND(tc_respawn, "s", (char *ent), {
	if (ent && ent[0] == 'p') {
		fpsent *p = (fpsent *) getdynent(ent);
		if (NULL == p) {
			conoutf("/tc_respawn error: player %s not found", ent);
			return;
		}
		fpscl->spawnplayer(p);
		p->state = CS_ALIVE;
	}
});

ICOMMAND(tc_taunt, "s", (char *ent), {
	if (ent && ent[0] == 'p') {
		fpsent *p = (fpsent *) getdynent(ent);
		if (NULL == p) {
			conoutf("/tc_taunt error: player %s not found", ent);
			return;
		}
		p->lasttaunt = fpscl->lastmillis;
	}
});

void playersetmodel(fpsent *p, char *model)
{
	if (p->modelname) { free(p->modelname); p->modelname = NULL; }
	if (model && model[0]) p->modelname = strdup(model);
}

ICOMMAND(playerinfo, "sss", (char *ent, char *team, char *model), {
	if (ent && ent[0] == TC_PLAYER) {
		// if we try to recreate a new player of the same id, just reuse the existing one
		fpsent *p = getplayer(ent);
		if (NULL == p) {
			conoutf("/playerinfo error: player %s not found", ent);
			return;
		}

		if (team && team[0]) {
			snprintf(p->team, sizeof(p->team), "<%s>", team);
		} else {
			p->team[0] = '\0';
		}
		playersetmodel(p, model);
	}
});

ICOMMAND(createmonster, "s", (char *ent), {
	if (ent && ent[0] == TC_MONSTER) {
		fpsent *p = new fpsent();
        findplayerspawn(p, -1, 0);
        //spawnstate(p);
		p->tc_id = atoi(ent + 1);

    }
});

ICOMMAND(monsterstate, "s", (char *ent), {
	if (ent && ent[0] == TC_MONSTER) {
		fpsclient::monsterset::monster *m = getmonster(ent);

		if (m) {
			sprintf(idbuf, "%d", m->monsterstate);
			result(idbuf);
		}
	}
});


extern void mousemove(int dx, int dy);
ICOMMAND(spinleft, "s", (char *amt), { int a = amt ? abs(atoi(amt)) : 50; mousemove(-a, 0); });
ICOMMAND(spinright, "s", (char *amt), { int a = amt ? abs(atoi(amt)) : 50;  mousemove(a, 0); });
ICOMMAND(spinup, "s", (char *amt), { int a = amt ? abs(atoi(amt)) : 50;  mousemove(0, a); });
ICOMMAND(spindown, "s", (char *amt), { int a = amt ? abs(atoi(amt)) : 50;  mousemove(0, -a); });
int zup = 0;
ICOMMAND(zup, "s", (char *amt), { int a = amt ? abs(atoi(amt)) : 10;  zup = a; });
ICOMMAND(zdown, "s", (char *amt), { int a = amt ? abs(atoi(amt)) : 10;  zup = -a; });

inline int parse(char *c) {
	return (c && c[0]) ? atoi(c) : 0;
}

ICOMMAND(selcube, "ssssssss", (char *oX, char *oY, char *oZ, char *sX, char *sY, char *sZ, char *grid, char *orient), {
	extern selinfo sel;
	sel.o.x = parse(oX);
	sel.o.y = parse(oY);
	sel.o.z = parse(oZ);
	sel.s.x = parse(sX);
	sel.s.y = parse(sY);
	sel.s.z = parse(sZ);
	sel.grid = parse(grid);
	sel.orient = parse(orient);
	sel.cx = 0;
	sel.cxs = 0;
	sel.cy = 0;
	sel.cys = 0;
	sel.corner = 0;
});

/* ICOMMAND(tc_delcube, "", (), {
	extern selinfo sel;
	mpdelcube(sel, true);
}); */

ICOMMAND(tc_settex, "ss", (char *t, char *allfaces), {
	extern void mpedittex(int tex, int allfaces, selinfo &sel, bool local);
	extern selinfo sel;
	mpedittex(parse(t), parse(allfaces), sel, true);
});

ICOMMAND(debugsel, "", (), {
	extern selinfo sel;
	char buf[512];
	snprintf(buf, sizeof(buf), "o (%d %d %d) s (%d %d %d) grd %d or %d, cx (%d %d %d %d) corner %d",
               sel.o.x, sel.o.y, sel.o.z, sel.s.x, sel.s.y, sel.s.z, sel.grid, sel.orient,
               sel.cx, sel.cxs, sel.cy, sel.cys, sel.corner);
	result(buf);
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

// lifted out of client.h parsepositions() function
void interpolatePlayer(void *p, float oldyaw, float oldpitch, vec oldpos)
{
	fpsent *d = (fpsent *) p;

	ndebugf(3, "Allowmove: %s\n", fpscl->allowmove(d) ? "true" : "false");
	if(fpscl->allowmove(d))
	{
		updatephysstate(d);
		fpscl->cc.updatepos(d);
	}
	ndebugf(3, "Smoothmove: %s\n", fpscl->smoothmove(d) ? "true" : "false");
	if(d->state==CS_DEAD)
	{
		d->resetinterp();
		d->smoothmillis = 0;
	}
	else if(fpscl->smoothmove() && d->smoothmillis>=0 && oldpos.dist(d->o) < fpscl->smoothdist())
	{
		d->newpos = d->o;
		d->newyaw = d->yaw;
		d->newpitch = d->pitch;
		d->o = oldpos;
		d->yaw = oldyaw;
		d->pitch = oldpitch;
		(d->deltapos = oldpos).sub(d->newpos);
		d->deltayaw = oldyaw - d->newyaw;
		if(d->deltayaw > 180) d->deltayaw -= 360;
		else if(d->deltayaw < -180) d->deltayaw += 360;
		d->deltapitch = oldpitch - d->newpitch;
		d->smoothmillis = fpscl->lastmillis;
	}
	else d->smoothmillis = 0;
}

bool plug_fpspluginitialized = initfpsplug();
