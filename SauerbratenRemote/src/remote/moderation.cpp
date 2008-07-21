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

static vec tmp;

struct watcher {
	char *id;
	dynent *entity;
	char *code;
	vec lastPosition;
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
		if (moveRes > 0) {
			tmp = entity->o;
			tmp.sub(lastPosition);
			if (tmp.squaredlen() > moveRes) {
				return true;
			}
		}
		if (lastinwater != entity->inwater ||
			lasttimeinair != entity->timeinair ||
			laststrafe != entity->strafe ||
			lastmove != entity->move ||
			lastphysstate != entity->physstate) return true;

		return rotRes > 0 && fabs(lastRoll - entity->roll) + fabs(lastPitch - entity->pitch) + fabs(lastYaw - entity->yaw) > rotRes;
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
		tmp = w->entity->o;
		tmp.sub(w->lastPosition);
		if (w->changed()) {
			w->execute();
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
	char buf[32];
	sprintf(buf, " %s %f", field, value);
	strcpy(output, buf);
}

static void report(char *output, char *field, int value)
{
	char buf[32];
	sprintf(buf, " %s %d", field, value);
	strcat(output, buf);
}


static void tc_info(char *id, char *output) {
	output[0] = '\0';

	if (!(id && id[0])) return;{
	dynent *ent = getdynent(id);
	if (!ent) return;
	watcher *w = findWatcher(ent);
	if (!w) return;

	if (ent->o.x != w->lastPosition.x) report(output, "x", ent->o.x);
	if (ent->o.y != w->lastPosition.y) report(output, "y", ent->o.y);
	if (ent->o.z != w->lastPosition.z) report(output, "z", ent->o.z);
	if (ent->roll != w->lastRoll) report(output, "r", ent->roll);
	if (ent->pitch != w->lastPitch) report(output, "p", ent->pitch);
	if (ent->yaw != w->lastYaw) report(output, "y", ent->yaw);

	if (ent->inwater != w->lastinwater) report(output, "w", ent->inwater);
	if (ent->timeinair != w->lasttimeinair) report(output, "t", ent->timeinair);
	if (ent->strafe != w->laststrafe) report(output, "s", ent->strafe);
	if (ent->move != w->lastmove) report(output, "m", ent->move);
	if (ent->physstate != w->lastphysstate) report(output, "q", ent->physstate);
	}
}

static bool initModeration() {
	printf("INITIALIZING\n");
	extern void addTickHook(void (*hook)());
	addTickHook(moderationTick);
	addcommand("tc_info", (void(*)())tc_info, "ss");
	addcommand("ent.x", (void(*)())entX, "ss");
	addcommand("ent.y", (void(*)())entY, "ss");
	addcommand("ent.z", (void(*)())entZ, "ss");
	addcommand("ent.r", (void(*)())entRoll, "ss");
	addcommand("ent.p", (void(*)())entPitch, "ss");
	addcommand("ent.y", (void(*)())entYaw, "ss");

	addcommand("ent.w", (void(*)())entInWater, "ss");
	addcommand("ent.t", (void(*)())entTimeInAir, "ss");
	addcommand("ent.s", (void(*)())entStrafe, "ss");
	addcommand("ent.m", (void(*)())entMove, "ss");
	addcommand("ent.q", (void(*)())entPhysState, "ss");
	return true;
}
bool init = initModeration();

ICOMMAND(watch, "ss", (char *entName, char *code), {
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
			watcher w(entName, code, 1, 1);
			
			if (!w.valid()) {
				conoutf("no entity for id [%s]", entName);
			} else {
				watchers.add(w);
			}
		}
});
