#include "pch.h"
#include "cube.h"
#include "iengine.h"
#include <stdio.h>
#include <sys/types.h>
#include "remote.h"

//Millisecond value updated before each tick
int tickmillis;
int updateresolution = 200;

typedef hashtable<const char *, ident> identtable;
VAR(appmouse, 0, 0, 1);

struct watcher {
	char *id;
	dynent *entity;
	char *code;
	vec lastPosition, lastVel, lastFalling;
	float lastRoll;
	float lastPitch;
	float lastYaw;
	float moveRes;
	float rotRes;
	int lastupdate;
    int lastinwater, lasttimeinair;
	char laststrafe, lastmove;
	uchar lastphysstate;	

	watcher(char *id, char *code, float _moveRes, float _rotRes): moveRes(_moveRes), rotRes(_rotRes), lastupdate(0) {
		entity = getdynent(id);
		if (entity) {
			this->id = newstring(id);
			this->code = newstring(code);
			update();
		} else {
			this->id = NULL;
			this->code = NULL;
		}
	}
	bool valid() {
		return !!entity;
	}
	void update() {
		lastPosition = entity->o;
		lastVel = entity->vel;
		lastFalling = entity->falling;
		lastRoll = entity->roll;
		lastPitch = entity->pitch;
		lastYaw = entity->yaw;

		lastinwater = entity->inwater;
		lasttimeinair = entity->timeinair;
		laststrafe = entity->strafe;
		lastmove = entity->move;
		lastphysstate = entity->physstate;

		lastupdate = tickmillis;
	}
	bool changed() {
		if (tickmillis - lastupdate < updateresolution) {
			return false;
		}
		if (moveRes >= 0) {
			vec tmp = entity->o;
			tmp.sub(lastPosition);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
			tmp = entity->vel;
			tmp.sub(lastVel);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
			tmp = entity->falling;
			tmp.sub(lastFalling);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
		}
		if (lastinwater != entity->inwater ||
			lasttimeinair != entity->timeinair ||
			laststrafe != entity->strafe ||
			lastmove != entity->move ||
			lastphysstate != entity->physstate) return true;

		if (rotRes >= 0) {
			if (fabs(lastRoll - entity->roll) + fabs(lastPitch - entity->pitch) + fabs(lastYaw - entity->yaw) > rotRes) return true;
		}
		return false;
	}
	void execute() {
		executeret(code);
	}
};

ICOMMAND(updateperiod, "i", (int *i), {
	if (i) {
		updateresolution = *i;
	} else {
		intret(updateresolution);
	}
});

extern identtable *idents;        // contains ALL vars/commands/aliases

static char buf[1024];
char *currentcommandname = 0;
static vector<watcher> watchers;

ICOMMAND(listidents, "", (),
    enumerate(*idents, ident, id,
    	switch (id.type) {
    	case ID_VAR:
   			printf("var: %s %d\n", id.name, id.val.i);
   			break;
    	case ID_COMMAND:
   			printf("command: %s(%s)\n", id.name, id.narg);
   			break;
    	case ID_CCOMMAND:
   			printf("ccommand: %s(%s)\n", id.name, id.narg);
   			break;
    	case ID_ALIAS:
   			printf("alias: %s = [%s]\n", id.name, id.action);
    		break;
    	}
    );
);

ICOMMAND(override, "sss", (const char *cmd, char *newName, char *body), {
		printf("overriding cmd: %s as %s with definition: %s\n", cmd, newName, body);
		if (cmd && cmd[0] && newName && newName[0] && body && body[0]) {
			ident *old = idents->access(cmd);
			ident *newValue = idents->access(newName);

			if (newValue) {
				conoutf("cannot override cmd: %s as %s, because there is already a definition for %s.", cmd, newName, newName);
			} else if (!old) {
				conoutf("cannot override cmd: %s as %s, because there is no definition for %s.", cmd, newName, cmd);
			} else {
				int oldType = old->type;

				idents->access(newstring(newName), old);
				old->type = ID_ALIAS;
				old->override = NO_OVERRIDE;
				old->stack = NULL;
				old->action = newstring(body);
				//old->persist = false;
				if (oldType != ID_ALIAS) {
					old->name = newstring(old->name);
				}
			}
		} else {
			conoutf("usage: overriding cmd newName body");
		}
});

static void moderationTick() {
	loopi(watchers.length()) {
		watcher *w = &watchers[i];
		if (w->changed()) {
            float oldyaw = w->entity->yaw, oldpitch = w->entity->pitch;
            vec oldpos(w->entity->o);

			w->execute();
			extern void interpolatePlayer(void *d, float oldyaw, float oldpitch, vec oldpos);
			interpolatePlayer(w->entity, oldyaw, oldpitch, oldpos);
			w->update(); // update AFTER execute to reset last known info
		}
	}
}

static void floatVal(float &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%f", fl);
		result(buf);
	} else {
		fl = atof(value);
	}
}

static void intVal(int &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%d", fl);
		result(buf);
	} else {
		fl = atoi(value);
	}
}

static void charVal(char &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%d", (int) fl);
		result(buf);
	} else {
		fl = (char) atoi(value);
	}
}

static void ucharVal(uchar &fl, char *value) {
	if (!value[0]) {
		sprintf(buf, "%d", (int) fl);
		result(buf);
	} else {
		fl = (uchar) atoi(value);
	}
}

static void entX(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->o.x, value);
		}
	}
}

static void entY(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->o.y, value);
		}
	}
}

static void entZ(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->o.z, value);
		}
	}
}

static void entvX(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->vel.x, value);
		}
	}
}

static void entvY(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->vel.y, value);
		}
	}
}

static void entvZ(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->vel.z, value);
		}
	}
}

static void entfX(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->falling.x, value);
		}
	}
}

static void entfY(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->falling.y, value);
		}
	}
}

static void entfZ(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->falling.z, value);
		}
	}
}

static void entRoll(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->roll, value);
		}
	}
}

static void entPitch(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->pitch, value);
		}
	}
}

static void entYaw(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) {
			floatVal(ent->targetyaw, value);
			floatVal(ent->yaw, value);
		}
	}
}
static void entInWater(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) intVal(ent->inwater, value);
	}
}

static void entTimeInAir(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) intVal(ent->timeinair, value);
	}
}
static void entStrafe(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) charVal(ent->strafe, value);
	}
}
static void entMove(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) charVal(ent->move, value);
	}
}
static void entPhysState(char *id, char *value) {
	if (id && id[0]) {
		dynent *ent = getdynent(id);

		if (ent) ucharVal(ent->physstate, value);
	}
}

watcher *findWatcher(dynent *ent) {
	loopi(watchers.length()) {
		watcher *w = &watchers[i];
		if (NULL != w && w->entity == ent) return w;
	}
	return NULL;
}

static void report(char *output, char *field, float value)
{
	char mybuf[32];
	sprintf(mybuf, " %s %f", field, value);
	strcat(output, mybuf);
}

static void report(char *output, char *field, int value)
{
	char mybuf[32];
	sprintf(mybuf, " %s %d", field, value);
	strcat(output, mybuf);
}

static bool areDoublesEqual(double d1, double d2)
{
	return fabs(d1 - d2) < 0.00001;
}

static void tc_info(char *id) {
	buf[0] = '\0';

	if (id && id[0]) {
		dynent *ent = getdynent(id);
		watcher *w = findWatcher(ent);
		if (ent && w) {
			//fprintf(stderr, "tc_info have ent and watcher!\n");
			if (!areDoublesEqual(ent->o.x, w->lastPosition.x)) report(buf, "x", ent->o.x);
			if (!areDoublesEqual(ent->o.y, w->lastPosition.y)) report(buf, "y", ent->o.y);
			if (!areDoublesEqual(ent->o.z, w->lastPosition.z)) report(buf, "z", ent->o.z);
			if (!areDoublesEqual(ent->roll, w->lastRoll)) report(buf, "rol", ent->roll);
			if (!areDoublesEqual(ent->pitch, w->lastPitch)) report(buf, "pit", ent->pitch);
			if (!areDoublesEqual(ent->yaw, w->lastYaw)) report(buf, "yaw", ent->yaw);

			if (!areDoublesEqual(ent->vel.x, w->lastVel.x)) report(buf, "vx", ent->vel.x);
			if (!areDoublesEqual(ent->vel.y, w->lastVel.y)) report(buf, "vy", ent->vel.y);
			if (!areDoublesEqual(ent->vel.z, w->lastVel.z)) report(buf, "vz", ent->vel.z);
			if (!areDoublesEqual(ent->falling.x, w->lastFalling.x)) report(buf, "fx", ent->falling.x);
			if (!areDoublesEqual(ent->falling.y, w->lastFalling.y)) report(buf, "fy", ent->falling.y);
			if (!areDoublesEqual(ent->falling.z, w->lastFalling.z)) report(buf, "fz", ent->falling.z);

			if (ent->inwater != w->lastinwater) report(buf, "iw", ent->inwater);
			if (ent->timeinair != w->lasttimeinair) report(buf, "tia", ent->timeinair);
			if (ent->strafe != w->laststrafe) report(buf, "s", ent->strafe);
			if (ent->move != w->lastmove) report(buf, "m", ent->move);
			if (ent->physstate != w->lastphysstate) report(buf, "ps", ent->physstate);
		} else {
			conoutf("tc_info error: no entity or watcher specified");
			strcpy(buf, "tc_info error");
		}
	}
	result(buf);
}

static bool initModeration() {
	printf("INITIALIZING\n");
	extern void addTickHook(void (*hook)());
	addTickHook(moderationTick);
	addcommand("tc_info", (void(*)())tc_info, "s");
	addcommand("ent.x", (void(*)())entX, "ss");
	addcommand("ent.y", (void(*)())entY, "ss");
	addcommand("ent.z", (void(*)())entZ, "ss");
	addcommand("ent.rol", (void(*)())entRoll, "ss");
	addcommand("ent.pit", (void(*)())entPitch, "ss");
	addcommand("ent.yaw", (void(*)())entYaw, "ss");

	addcommand("ent.vx", (void(*)())entvX, "ss");
	addcommand("ent.vy", (void(*)())entvY, "ss");
	addcommand("ent.vz", (void(*)())entvZ, "ss");
	addcommand("ent.fx", (void(*)())entfX, "ss");
	addcommand("ent.fy", (void(*)())entfY, "ss");
	addcommand("ent.fz", (void(*)())entfZ, "ss");

	addcommand("ent.iw", (void(*)())entInWater, "ss");
	addcommand("ent.tia", (void(*)())entTimeInAir, "ss");
	addcommand("ent.s", (void(*)())entStrafe, "ss");
	addcommand("ent.m", (void(*)())entMove, "ss");
	addcommand("ent.ps", (void(*)())entPhysState, "ss");
	return true;
}
bool init = initModeration();

ICOMMAND(watch, "sss", (char *entName, char *code, char *resolution), {
		if (!entName || !entName[0]) {
			conoutf("no entity specified to watch");
		} else if (!code) {
			conoutf("no watching code given");
		} else if (!code[0]) {
			loopi(watchers.length()) {
				watcher *w = &watchers[i];

				if (!strcmp(w->id, entName)) {
					if (w->id) {
						delete[] w->id;
					}
					if (w->code) {
						delete[] w->code;
					}
					watchers.remove(i);
					return;
				}
			}
		} else {
			loopi(watchers.length()) {
				watcher *w = &watchers[i];

				if (!strcmp(w->id, entName)) {
					if (w->code) {
						printf("REPLACING OLD CODE \"%s\" WITH ", w->code);
						delete[] w->code;
					}
					w->code = newstring(code);
					printf("NEW CODE \"%s\"\n", w->code);
					return;
				}
			}
			float res = 1.0;
			if (resolution && resolution[0]) floatVal(res, resolution);
			watcher w(entName, code, res, res);
			
			if (!w.valid()) {
				conoutf("no entity for id [%s]", entName);
			} else {
				watchers.add(w);
			}
		}
});
ICOMMAND(maxspeed, "ss", (char *ent, char *speed), {
	if (ent && ent[0]) {
		dynent *d = getdynent(ent);

		if (d) {
			floatVal(d->maxspeed, speed);
		}
	}
});
