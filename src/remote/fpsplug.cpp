// plug in to fps.cpp

extern fpsclient *cl;

void dynent::renderplayer(fpsclient &cl, int mdl, const char **modelnames, bool fiddlies) {
	fpsent *d = (fpsent *)this;
	const char *model = modelname ? modelname : modelnames[mdl];

//	printf("render player (%s) %s\n", name, suggestion);
	if(d->state!=CS_DEAD || d->superdamage<50) {
		cl.fr.renderplayer((fpsent*)this, model);
	}
	if (fiddlies) {
		s_strcpy(d->info, cl.colorname(d, NULL, "@"));
		if(d->maxhealth>100) { s_sprintfd(sn)(" +%d", d->maxhealth-100); s_strcat(d->info, sn); }
		if(d->state!=CS_DEAD) particle_text(d->abovehead(), d->info, mdl ? (mdl==1 ? 16 : 13) : 11, 1);
	}
}

void dynent::rendermonster(fpsclient &cl) {
	fpsclient::monsterset::monster *m = (fpsclient::monsterset::monster *)this;

    modelattach vwep[] = { { m->monstertypes[m->mtype].vwepname, MDL_ATTACH_VWEP, ANIM_VWEP|ANIM_LOOP, 0 }, { NULL } };
    renderclient(m, m->monstertypes[m->mtype].mdlname, vwep, m->monsterstate==M_ATTACKING ? -ANIM_SHOOT : 0, 300, m->lastaction, m->lastpain);
}
void dynent::rendermovable(fpsclient &cl, vec o, vec color, vec dir, const char *suggestion) {
	rendermodel(color, dir, suggestion, ANIM_MAPMODEL|ANIM_LOOP, 0, 0, o, yaw, 0, 0, 0, this, MDL_SHADOW | MDL_CULL_VFC | MDL_CULL_DIST | MDL_CULL_OCCLUDED, NULL);
}

enum ENT_TYPE {TC_ERROR = 'e', TC_PLAYER = 'p', TC_MONSTER = 'm', TC_ITEM = 'i'};

int enttype(int index) {
	if (index < 0) {
		return TC_ERROR;
	}
	if (!index) {
		return TC_PLAYER;
	}
	index--;
	if (index < cl->players.length()) {
		return TC_PLAYER;
	}
	index -= cl->players.length();
	if (index < cl->ms.monsters.length()) {
		return TC_MONSTER;
	}
	index -= cl->ms.monsters.length();
	if (index < cl->mo.movables.length()) {
		return TC_ITEM;
	}
	return TC_ERROR;
}

void idfor(void *ent, char *buf, int buflen) {
	int id = -1;

	loopi(cl->numdynents()) {
		dynent *o = cl->iterdynents(i);

		if (o == ent) {
			int type = enttype(i);
			
			if (type == TC_PLAYER) {
				id = i;
			} else {
				i -= cl->players.length() + 1;
				id = type == TC_MONSTER ? i : i - cl->ms.monsters.length();
			}
			snprintf(buf, buflen, "%c%d", type, id);
			return;
		}
	}
	strncpy(buf, "error", buflen);
}

fpsent *getplayer(char *id) {
	if (id[0] == TC_PLAYER) {
		int i = atoi(id + 1);

		if (!i) {
			return cl->player1;
		}
		i--;
		if (i >= 0 && i < cl->players.length()) {
			return cl->players[i];
		}
	}
	return NULL;
}

fpsclient::monsterset::monster *getmonster(char *id) {
	if (id[0] == TC_MONSTER) {
		int i = atoi(id + 1);

		if (i >= 0 && i < cl->ms.monsters.length()) {
			return cl->ms.monsters[i];
		}
	}
	return NULL;
}

fpsclient::movableset::movable *getitem(char *id) {
	if (id[0] == TC_ITEM) {
		int i = atoi(id + 1);

		if (i >= 0 && i < cl->mo.movables.length()) {
			return cl->mo.movables[i];
		}
	}
	return NULL;
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
			conoutf("unknown entity type: %c", id[0]);
			break;
		}
	} else {
		conoutf("no entity id given");
	}
}
ICOMMAND(dumpent, "s", (char *id), dumpent(id));

ICOMMAND(dumpents, "", (),
	printf("Entities...\n");
	loopi(cl->numdynents()) {
		dynent *o = cl->iterdynents(i);
		char id[1024];
		
		idfor(o, id, 1024);
		dumpent(id);
	}
);
