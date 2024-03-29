// weapon.h: all shooting and effects code, projectile management

struct weaponstate
{
    fpsclient &cl;
    fpsent *player1;

    static const int MONSTERDAMAGEFACTOR = 4;
    static const int OFFSETMILLIS = 500;
    vec sg[SGRAYS];

    IVARP(maxdebris, 10, 25, 1000);
    IVARP(maxbarreldebris, 5, 10, 1000);

    weaponstate(fpsclient &_cl) : cl(_cl), player1(_cl.player1)
    {
        CCOMMAND(getweapon, "", (weaponstate *self), intret(self->player1->gunselect));
        CCOMMAND(nextweapon, "ii", (weaponstate *self, int *dir, int *force), self->nextweapon(*dir, *force!=0));
        CCOMMAND(setweapon, "ii", (weaponstate *self, int *gun, int *force), self->setweapon(*gun, *force!=0));
        CCOMMAND(cycleweapon, "sssss", (weaponstate *self, char *w1, char *w2, char *w3, char *w4, char *w5),
        {
            int numguns = 0;
            int guns[5];
            if(w1[0]) guns[numguns++] = atoi(w1);
            if(w2[0]) guns[numguns++] = atoi(w2);
            if(w3[0]) guns[numguns++] = atoi(w3);
            if(w4[0]) guns[numguns++] = atoi(w4);
            if(w5[0]) guns[numguns++] = atoi(w5);

            self->cycleweapon(numguns, guns);
        });
        CCOMMAND(weapon, "sss", (weaponstate *self, char *w1, char *w2, char *w3),
        {
            self->weaponswitch(w1[0] ? atoi(w1) : -1,
                               w2[0] ? atoi(w2) : -1,
                               w3[0] ? atoi(w3) : -1);

        });
    }

    void gunselect(int gun)
    {
        if(gun!=player1->gunselect)
        {
            cl.cc.addmsg(SV_GUNSELECT, "ri", gun);
            playsound(S_WEAPLOAD, &player1->o);
        }
        player1->gunselect = gun;
    }

    void nextweapon(int dir, bool force = false)
    {
        if(player1->state!=CS_ALIVE) return;
        dir = (dir < 0 ? NUMGUNS-1 : 1);
        int gun = player1->gunselect;
        loopi(NUMGUNS)
        {
            gun = (gun + dir)%NUMGUNS;
            if(force || player1->ammo[gun]) break;
        }
        if(gun != player1->gunselect) gunselect(gun);
        else playsound(S_NOAMMO);
    }

    void setweapon(int gun, bool force = false)
    {
        if(player1->state!=CS_ALIVE || gun<GUN_FIST || gun>GUN_PISTOL) return;
        if(force || player1->ammo[gun]) gunselect(gun);
        else playsound(S_NOAMMO);
    }

    void cycleweapon(int numguns, int *guns, bool force = false)
    {
        if(numguns<=0 || player1->state!=CS_ALIVE) return;
        int offset = 0;
        loopi(numguns) if(guns[i] == player1->gunselect) { offset = i+1; break; }
        loopi(numguns)
        {
            int gun = guns[(i+offset)%numguns];
            if(gun>=0 && gun<NUMGUNS && (force || player1->ammo[gun]))
            {
                gunselect(gun);
                return;
            }
        }
        playsound(S_NOAMMO);
    }

    void weaponswitch(int a = -1, int b = -1, int c = -1)
    {
        if(player1->state!=CS_ALIVE || a<-1 || b<-1 || c<-1 || a>=NUMGUNS || b>=NUMGUNS || c>=NUMGUNS) return;
        int *ammo = player1->ammo;
        int s = player1->gunselect;

        if     (a>=0 && s!=a  && ammo[a])          s = a;
        else if(b>=0 && s!=b  && ammo[b])          s = b;
        else if(c>=0 && s!=c  && ammo[c])          s = c;
        else if(s!=GUN_CG     && ammo[GUN_CG])     s = GUN_CG;
        else if(s!=GUN_RL     && ammo[GUN_RL])     s = GUN_RL;
        else if(s!=GUN_SG     && ammo[GUN_SG])     s = GUN_SG;
        else if(s!=GUN_RIFLE  && ammo[GUN_RIFLE])  s = GUN_RIFLE;
        else if(s!=GUN_GL     && ammo[GUN_GL])     s = GUN_GL;
        else if(s!=GUN_PISTOL && ammo[GUN_PISTOL]) s = GUN_PISTOL;
        else                                       s = GUN_FIST;

        gunselect(s);
    }

    int reloadtime(int gun) { return guns[gun].attackdelay; }

    void offsetray(vec &from, vec &to, int spread, vec &dest)
    {
        float f = to.dist(from)*spread/1000;
        for(;;)
        {
            #define RNDD rnd(101)-50
            vec v(RNDD, RNDD, RNDD);
            if(v.magnitude()>50) continue;
            v.mul(f);
            v.z /= 2;
            dest = to;
            dest.add(v);
            vec dir = dest;
            dir.sub(from);
            dir.normalize();
            raycubepos(from, dir, dest, 0, RAY_CLIPMAT|RAY_ALPHAPOLY);
            return;
        }
    }

    void createrays(vec &from, vec &to)             // create random spread of rays for the shotgun
    {
        loopi(SGRAYS) offsetray(from, to, SGSPREAD, sg[i]);
    }

    enum { BNC_GRENADE, BNC_GIBS, BNC_DEBRIS, BNC_BARRELDEBRIS };

    struct bouncent : physent
    {
        int lifetime;
        float lastyaw, roll;
        bool local;
        fpsent *owner;
        int bouncetype;
        vec offset;
        int offsetmillis;
        int id;
        entitylight light;
    };

    vector<bouncent *> bouncers;

    void newbouncer(const vec &from, const vec &to, bool local, fpsent *owner, int type, int lifetime, int speed, entitylight *light = NULL)
    {
        bouncent &bnc = *(bouncers.add(new bouncent));
        bnc.reset();
        bnc.type = ENT_BOUNCE;
        bnc.o = from;
        bnc.radius = type==BNC_DEBRIS ? 0.5f : 1.5f;
        bnc.eyeheight = bnc.radius;
        bnc.aboveeye = bnc.radius;
        bnc.lifetime = lifetime;
        bnc.roll = 0;
        bnc.local = local;
        bnc.owner = owner;
        bnc.bouncetype = type;
        bnc.id = cl.lastmillis;
        if(light) bnc.light = *light;

        vec dir(to);
        dir.sub(from).normalize();
        bnc.vel = dir;
        bnc.vel.mul(speed);

        avoidcollision(&bnc, dir, owner, 0.1f);

        bnc.offset = hudgunorigin(type==BNC_GRENADE ? GUN_GL : -1, from, to, owner);
        bnc.offset.sub(bnc.o);
        bnc.offsetmillis = OFFSETMILLIS;

        bnc.resetinterp();
    }

    void bounceupdate(int time)
    {
        loopv(bouncers)
        {
            bouncent &bnc = *(bouncers[i]);
            if(bnc.bouncetype==BNC_GRENADE && bnc.vel.magnitude() > 50.0f)
            {
                vec pos(bnc.o);
                pos.add(vec(bnc.offset).mul(bnc.offsetmillis/float(OFFSETMILLIS)));
                regular_particle_splash(5, 1, 150, pos);
            }
            vec old(bnc.o);
            bool stopped = false;
            if(bnc.bouncetype==BNC_GRENADE) stopped = bounce(&bnc, 0.6f, 0.5f) || (bnc.lifetime -= time)<0;
            else
            {
                // cheaper variable rate physics for debris, gibs, etc.
                for(int rtime = time; rtime > 0;)
                {
                    int qtime = min(30, rtime);
                    rtime -= qtime;
                    if((bnc.lifetime -= qtime)<0 || bounce(&bnc, qtime/1000.0f, 0.6f, 0.5f)) { stopped = true; break; }
                }
            }
            if(stopped)
            {
                if(bnc.bouncetype==BNC_GRENADE)
                {
                    int qdam = guns[GUN_GL].damage*(bnc.owner->quadmillis ? 4 : 1);
                    if(bnc.owner->type==ENT_AI) qdam /= MONSTERDAMAGEFACTOR;
                    hits.setsizenodelete(0);
                    explode(bnc.local, bnc.owner, bnc.o, NULL, qdam, GUN_GL);
                    adddecal(DECAL_SCORCH, bnc.o, vec(0, 0, 1), RL_DAMRAD/2);
                    if(bnc.local)
                        cl.cc.addmsg(SV_EXPLODE, "ri3iv", cl.lastmillis-cl.maptime, GUN_GL, bnc.id-cl.maptime,
                                hits.length(), hits.length()*sizeof(hitmsg)/sizeof(int), hits.getbuf());
                }
                delete &bnc;
                bouncers.remove(i--);
            }
            else
            {
                bnc.roll += old.sub(bnc.o).magnitude()/(4*RAD);
                bnc.offsetmillis = max(bnc.offsetmillis-time, 0);
            }
        }
    }

    void removebouncers(fpsent *owner)
    {
        loopv(bouncers) if(bouncers[i]->owner==owner) { delete bouncers[i]; bouncers.remove(i--); }
    }

    struct projectile
    {
        vec dir, o, to, offset;
        float speed;
        fpsent *owner;
        int gun;
        bool local;
        int offsetmillis;
        int id;
        entitylight light;
    };
    vector<projectile> projs;

    void projreset() { projs.setsize(0); bouncers.deletecontentsp(); bouncers.setsize(0); }

    void newprojectile(const vec &from, const vec &to, float speed, bool local, fpsent *owner, int gun)
    {
        projectile &p = projs.add();
        p.dir = vec(to).sub(from).normalize();
        p.o = from;
        p.to = to;
        p.offset = hudgunorigin(gun, from, to, owner);
        p.offset.sub(from);
        p.speed = speed;
        p.local = local;
        p.owner = owner;
        p.gun = gun;
        p.offsetmillis = OFFSETMILLIS;
        p.id = cl.lastmillis;
    }

    void removeprojectiles(fpsent *owner)
    {
        loopv(projs) if(projs[i].owner==owner) projs.remove(i--);
    }

    IVARP(blood, 0, 1, 1);

    void damageeffect(int damage, fpsent *d, bool thirdperson = true)
    {
        vec p = d->o;
        p.z += 0.6f*(d->eyeheight + d->aboveeye) - d->eyeheight;
        if(blood()) particle_splash(3, damage/10, 1000, p);
        if(thirdperson)
        {
            s_sprintfd(ds)("@%d", damage);
            particle_text(d->abovehead(), ds, 8);
        }
    }

    void spawnbouncer(vec &p, vec &vel, fpsent *d, int type, entitylight *light = NULL)
    {
        vec to(rnd(100)-50, rnd(100)-50, rnd(100)-50);
        to.normalize();
        to.add(p);
        newbouncer(p, to, true, d, type, rnd(1000)+1000, rnd(100)+20, light);
    }

    void superdamageeffect(vec &vel, fpsent *d)
    {
        if(!d->superdamage) return;
        vec from = d->abovehead();
        from.y -= 16;
        loopi(min(d->superdamage/25, 40)+1) spawnbouncer(from, vel, d, BNC_GIBS);
    }

    struct hitmsg
    {
        int target, lifesequence, info;
        ivec dir;
    };
    vector<hitmsg> hits;

    void hit(int damage, dynent *d, fpsent *at, const vec &vel, int gun, int info = 1)
    {
        if(at==player1 && d!=player1) cl.lasthit = cl.lastmillis;

        if(d->type==ENT_INANIMATE)
        {
            movableset::movable *m = (movableset::movable *)d;
            m->hitpush(damage, vel, at, gun);
            m->damaged(damage, at, gun);
            return;
        }

        fpsent *f = (fpsent *)d;

        f->lastpain = cl.lastmillis;
        if(at->type==ENT_PLAYER) at->totaldamage += damage;
        f->superdamage = 0;

        if(f->type==ENT_AI || !m_mp(cl.gamemode) || f==player1) f->hitpush(damage, vel, at, gun);

        if(f->type==ENT_AI) ((monsterset::monster *)f)->monsterpain(damage, at);
        else if(!m_mp(cl.gamemode)) cl.damaged(damage, f, at);
        else
        {
            hitmsg &h = hits.add();
            h.target = f->clientnum;
            h.lifesequence = f->lifesequence;
            h.info = info;
            damageeffect(damage, f);
            if(f==player1)
            {
                h.dir = ivec(0, 0, 0);
                f->damageroll(damage);
                damageblend(damage);
                damagecompass(damage, at ? at->o : f->o);
                playsound(S_PAIN6);
            }
            else
            {
                h.dir = ivec(int(vel.x*DNF), int(vel.y*DNF), int(vel.z*DNF));
                playsound(S_PAIN1+rnd(5), &f->o);
            }
        }
    }

    void hitpush(int damage, dynent *d, fpsent *at, vec &from, vec &to, int gun, int rays)
    {
        vec v(to);
        v.sub(from);
        v.normalize();
#ifdef TC
        moderator.rayhit(this, damage, d, at, v, gun, rays);
#else
        hit(damage, d, at, v, gun, rays);
#endif
    }

    void radialeffect(dynent *o, vec &v, int qdam, fpsent *at, int gun)
    {
        if(o->state!=CS_ALIVE) return;
        vec dir;
        float dist = rocketdist(o, dir, v);
        if(dist<RL_DAMRAD)
        {
            int damage = (int)(qdam*(1-dist/RL_DISTSCALE/RL_DAMRAD));
            if(gun==GUN_RL && o==at) damage /= RL_SELFDAMDIV;
            hit(damage, o, at, dir, gun, int(dist*DMF));
        }
    }

    float rocketdist(dynent *o, vec &dir, const vec &v)
    {
        vec middle = o->o;
        middle.z += (o->aboveeye-o->eyeheight)/2;
        float dist = middle.dist(v, dir);
        dir.div(dist);
        if(dist<0) dist = 0;
        return dist;
    }

    void explode(bool local, fpsent *owner, vec &v, dynent *notthis, int qdam, int gun)
    {
        particle_splash(0, 200, 300, v);
        playsound(S_RLHIT, &v);
        particle_fireball(v, RL_DAMRAD, gun==GUN_RL || gun==GUN_BARREL ? 22 : 23);
        if(gun==GUN_RL) adddynlight(v, 1.15f*RL_DAMRAD, vec(2, 1.5f, 1), 900, 100, 0, RL_DAMRAD/2, vec(1, 0.75f, 0.5f));
        else if(gun==GUN_GL) adddynlight(v, 1.15f*RL_DAMRAD, vec(2, 1.5f, 1), 900, 100, 0, 8, vec(0.25f, 1, 1));
        else adddynlight(v, 1.15f*RL_DAMRAD, vec(2, 1.5f, 1), 900, 100);
        int numdebris = gun==GUN_BARREL ? rnd(max(maxbarreldebris()-5, 1))+5 : rnd(maxdebris()-5)+5;
        vec debrisvel = owner->o==v ? vec(0, 0, 0) : vec(owner->o).sub(v).normalize(), debrisorigin(v);
        if(gun==GUN_RL) debrisorigin.add(vec(debrisvel).mul(8));
        if(numdebris)
        {
            entitylight light;
            lightreaching(debrisorigin, light.color, light.dir);
            loopi(numdebris)
                spawnbouncer(debrisorigin, debrisvel, owner, gun==GUN_BARREL ? BNC_BARRELDEBRIS : BNC_DEBRIS, &light);
        }
        if(!local) return;
        loopi(cl.numdynents())
        {
            dynent *o = cl.iterdynents(i);
            if(!o || o==notthis) continue;
            radialeffect(o, v, qdam, owner, gun);
        }
    }

    void splash(projectile &p, vec &v, dynent *notthis, int qdam)
    {
        if(guns[p.gun].part)
        {
            particle_splash(0, 100, 200, v);
            playsound(S_FEXPLODE, &v);
            // no push?
        }
        else
        {
            explode(p.local, p.owner, v, notthis, qdam, GUN_RL);
            adddecal(DECAL_SCORCH, v, vec(p.dir).neg(), RL_DAMRAD/2);
        }
    }

    bool projdamage(dynent *o, projectile &p, vec &v, int qdam)
    {
        if(o->state!=CS_ALIVE) return false;
        if(!intersect(o, p.o, v)) return false;
        splash(p, v, o, qdam);
        vec dir;
        if(guns[p.gun].part) { dir = v; dir.normalize(); }
        else rocketdist(o, dir, v);
#ifdef TC
        moderator.projectilehit(this, qdam, o, p.owner, dir, p.gun, 0);
#else
        hit(qdam, o, p.owner, dir, p.gun, 0);
#endif
        return true;
    }

    void moveprojectiles(int time)
    {
        loopv(projs)
        {
            projectile &p = projs[i];
            p.offsetmillis = max(p.offsetmillis-time, 0);
            int qdam = guns[p.gun].damage*(p.owner->quadmillis ? 4 : 1);
            if(p.owner->type==ENT_AI) qdam /= MONSTERDAMAGEFACTOR;
            vec v;
            float dist = p.to.dist(p.o, v);
            float dtime = dist*1000/p.speed;
            if(time > dtime) dtime = time;
            v.mul(time/dtime);
            v.add(p.o);
            bool exploded = false;
            hits.setsizenodelete(0);
            if(p.local)
            {
                loopj(cl.numdynents())
                {
                    dynent *o = cl.iterdynents(j);
                    if(!o || p.owner==o || o->o.reject(v, 10.0f)) continue;
                    if(projdamage(o, p, v, qdam)) { exploded = true; break; }
                }
            }
            if(!exploded)
            {
                if(dist<4)
                {
                    if(p.o!=p.to) // if original target was moving, reevaluate endpoint
                    {
                        if(raycubepos(p.o, p.dir, p.to, 0, RAY_CLIPMAT|RAY_ALPHAPOLY)>=4) continue;
                    }
                    splash(p, v, NULL, qdam);
                    exploded = true;
                }
                else
                {
                    vec pos(v);
                    pos.add(vec(p.offset).mul(p.offsetmillis/float(OFFSETMILLIS)));
                    if(guns[p.gun].part)
                    {
                         regular_particle_splash(1, 2, 300, pos);
                         particle_splash(guns[p.gun].part, 1, 1, pos);
                    }
                    else
                    {
                        regular_particle_splash(5, 2, 300, pos);
                    }
                }
            }
            if(exploded)
            {
                if(p.local)
                    cl.cc.addmsg(SV_EXPLODE, "ri3iv", cl.lastmillis-cl.maptime, p.gun, p.id-cl.maptime,
                            hits.length(), hits.length()*sizeof(hitmsg)/sizeof(int), hits.getbuf());
                projs.remove(i--);
            }
            else p.o = v;
        }
    }

    vec hudgunorigin(int gun, const vec &from, const vec &to, fpsent *d)
    {
        vec offset(from);
        if(d!=cl.hudplayer() || isthirdperson())
        {
            vec front, right;
            vecfromyawpitch(d->yaw, d->pitch, 1, 0, front);
            offset.add(front.mul(d->radius));
            if(d->type!=ENT_AI)
            {
                offset.z += (d->aboveeye + d->eyeheight)*0.75f - d->eyeheight;
                vecfromyawpitch(d->yaw, 0, 0, -1, right);
                offset.add(right.mul(0.5f*d->radius));
                offset.add(front);
            }
            return offset;
        }
        offset.add(vec(to).sub(from).normalize().mul(2));
        if(cl.hudgun())
        {
            offset.sub(vec(camup).mul(1.0f));
            offset.add(vec(camright).mul(0.8f));
        }
        return offset;
    }

    void shootv(int gun, vec &from, vec &to, fpsent *d, bool local)     // create visual effect from a shot
    {
        playsound(guns[gun].sound, d==player1 ? NULL : &d->o);
        int pspeed = 25;
        switch(gun)
        {
            case GUN_FIST:
                break;

            case GUN_SG:
            {
                loopi(SGRAYS)
                {
                    particle_splash(0, 20, 250, sg[i]);
                    particle_flare(hudgunorigin(gun, from, sg[i], d), sg[i], 300, 10);
                    if(!local) adddecal(DECAL_BULLET, sg[i], vec(from).sub(sg[i]).normalize(), 2.0f);
                }
                break;
            }

            case GUN_CG:
            case GUN_PISTOL:
            {
                particle_splash(0, 200, 250, to);
                //particle_trail(1, 10, from, to);
                particle_flare(hudgunorigin(gun, from, to, d), to, 600, 10);
                if(!local) adddecal(DECAL_BULLET, to, vec(from).sub(to).normalize(), 2.0f);
                //if(gun==GUN_CG) adddynlight(hudgunorigin(gun, d->o, to, d), 30, vec(1, 0.75f, 0.5f), 50, 0, DL_FLASH);
                break;
            }

            case GUN_RL:
            case GUN_FIREBALL:
            case GUN_ICEBALL:
            case GUN_SLIMEBALL:
                pspeed = guns[gun].projspeed*4;
                if(d->type==ENT_AI) pspeed /= 2;
                newprojectile(from, to, (float)pspeed, local, d, gun);
                break;

            case GUN_GL:
            {
                float dist = from.dist(to);
                vec up = to;
                up.z += dist/8;
                newbouncer(from, up, local, d, BNC_GRENADE, 2000, 200);
                break;
            }

            case GUN_RIFLE:
                particle_splash(0, 200, 250, to);
                particle_trail(21, 500, hudgunorigin(gun, from, to, d), to);
                if(!local) adddecal(DECAL_BULLET, to, vec(from).sub(to).normalize(), 3.0f);
                break;
        }
    }

    dynent *intersectclosest(vec &from, vec &to, fpsent *at)
    {
        dynent *best = NULL;
        float bestdist = 1e16f;
        loopi(cl.numdynents())
        {
            dynent *o = cl.iterdynents(i);
            if(!o || o==at || o->state!=CS_ALIVE) continue;
            if(!intersect(o, from, to)) continue;
            float dist = at->o.dist(o->o);
            if(dist<bestdist)
            {
                best = o;
                bestdist = dist;
            }
        }
        return best;
    }

    void shorten(vec &from, vec &to, vec &target)
    {
        target.sub(from).normalize().mul(from.dist(to)).add(from);
    }

    void raydamage(vec &from, vec &to, fpsent *d)
    {
        int qdam = guns[d->gunselect].damage;
        if(d->quadmillis) qdam *= 4;
        if(d->type==ENT_AI) qdam /= MONSTERDAMAGEFACTOR;
        dynent *o, *cl;
        if(d->gunselect==GUN_SG)
        {
            bool done[SGRAYS];
            loopj(SGRAYS) done[j] = false;
            for(;;)
            {
                bool raysleft = false;
                int hitrays = 0;
                o = NULL;
                loop(r, SGRAYS) if(!done[r] && (cl = intersectclosest(from, sg[r], d)))
                {
                    if((!o || o==cl) /*&& (damage<cl->health+cl->armour || cl->type!=ENT_AI)*/)
                    {
                        hitrays++;
                        o = cl;
                        done[r] = true;
                        shorten(from, o->o, sg[r]);
                    }
                    else raysleft = true;
                }
                if(hitrays) hitpush(hitrays*qdam, o, d, from, to, d->gunselect, hitrays);
                if(!raysleft) break;
            }
            loopj(SGRAYS) if(!done[j]) adddecal(DECAL_BULLET, sg[j], vec(from).sub(sg[j]).normalize(), 2.0f);
        }
        else if((o = intersectclosest(from, to, d)))
        {
            hitpush(qdam, o, d, from, to, d->gunselect, 1);
            shorten(from, o->o, to);
        }
        else if(d->gunselect!=GUN_FIST && d->gunselect!=GUN_BITE) adddecal(DECAL_BULLET, to, vec(from).sub(to).normalize(), d->gunselect==GUN_RIFLE ? 3.0f : 2.0f);
    }

    void shoot(fpsent *d, vec &targ)
    {
        int attacktime = cl.lastmillis-d->lastaction;
        if(attacktime<d->gunwait) return;
        d->gunwait = 0;
        if(d==player1 && !d->attacking) return;
        d->lastaction = cl.lastmillis;
        d->lastattackgun = d->gunselect;
        if(!d->ammo[d->gunselect])
        {
            if(d==player1)
            {
                cl.playsoundc(S_NOAMMO, d);
                d->gunwait = 600;
                d->lastattackgun = -1;
#ifdef TC
                execute("weapon -1 -1 -1");
#else
                weaponswitch();
#endif
            }
            return;
        }
#ifdef TC
        if(d->gunselect && tc_useammo) d->ammo[d->gunselect]--;
#else
        if(d->gunselect) d->ammo[d->gunselect]--;
#endif
        vec from = d->o;
        vec to = targ;

        vec unitv;
        float dist = to.dist(from, unitv);
        unitv.div(dist);
#ifdef TC
        vec kickback(unitv);
        kickback.mul(guns[d->gunselect].kickamount*-2.5f * tc_kickback);
        d->vel.add(kickback);
       	if(fabs(tc_kickback) < 1.1 && d->pitch<80.0f) d->pitch += guns[d->gunselect].kickamount*0.05f * tc_kickback;
#else
        vec kickback(unitv);
        kickback.mul(guns[d->gunselect].kickamount*-2.5f);
        d->vel.add(kickback);
        if(d->pitch<80.0f) d->pitch += guns[d->gunselect].kickamount*0.05f;
#endif
        float shorten = 0;
        if(guns[d->gunselect].range && dist > guns[d->gunselect].range)
            shorten = guns[d->gunselect].range;
        float barrier = raycube(d->o, unitv, dist, RAY_CLIPMAT|RAY_ALPHAPOLY);
        if(barrier < dist && (!shorten || barrier < shorten))
            shorten = barrier;
        if(shorten)
        {
            to = unitv;
            to.mul(shorten);
            to.add(from);
        }

        if(d->gunselect==GUN_SG) createrays(from, to);
        else if(d->gunselect==GUN_CG) offsetray(from, to, 1, to);

        if(d->quadmillis && attacktime>200) cl.playsoundc(S_ITEMPUP, d);

        hits.setsizenodelete(0);

        if(!guns[d->gunselect].projspeed) raydamage(from, to, d);

        shootv(d->gunselect, from, to, d, true);

        if(d==player1)
        {
            cl.cc.addmsg(SV_SHOOT, "ri2i6iv", cl.lastmillis-cl.maptime, d->gunselect,
                            (int)(from.x*DMF), (int)(from.y*DMF), (int)(from.z*DMF),
                            (int)(to.x*DMF), (int)(to.y*DMF), (int)(to.z*DMF),
                            hits.length(), hits.length()*sizeof(hitmsg)/sizeof(int), hits.getbuf());
        }

        d->gunwait = guns[d->gunselect].attackdelay;

        d->totalshots += guns[d->gunselect].damage*(d->quadmillis ? 4 : 1)*(d->gunselect==GUN_SG ? SGRAYS : 1);
    }

    void adddynlights()
    {
        loopv(projs)
        {
            projectile &p = projs[i];
            if(p.gun!=GUN_RL) continue;
            vec pos(p.o);
            pos.add(vec(p.offset).mul(p.offsetmillis/float(OFFSETMILLIS)));
            adddynlight(pos, RL_DAMRAD/2, vec(1, 0.75f, 0.5f));
        }
        loopv(bouncers)
        {
            bouncent &bnc = *bouncers[i];
            if(bnc.bouncetype!=BNC_GRENADE) continue;
            vec pos(bnc.o);
            pos.add(vec(bnc.offset).mul(bnc.offsetmillis/float(OFFSETMILLIS)));
            adddynlight(pos, 8, vec(0.25f, 1, 1));
        }
    }

    void renderprojectiles()
    {
        float yaw, pitch;
        loopv(bouncers)
        {
            bouncent &bnc = *(bouncers[i]);
            vec pos(bnc.o);
            pos.add(vec(bnc.offset).mul(bnc.offsetmillis/float(OFFSETMILLIS)));
            vec vel(bnc.vel);
            if(vel.magnitude() <= 25.0f) yaw = bnc.lastyaw;
            else
            {
                vectoyawpitch(vel, yaw, pitch);
                yaw += 90;
                bnc.lastyaw = yaw;
            }
            pitch = -bnc.roll;
            const char *mdl = "projectiles/grenade";
            string debrisname;
            int cull = MDL_CULL_VFC|MDL_CULL_DIST|MDL_CULL_OCCLUDED;
            if(bnc.bouncetype==BNC_GIBS) { mdl = ((int)(size_t)&bnc)&0x40 ? "gibc" : "gibh"; cull |= MDL_LIGHT|MDL_DYNSHADOW; }
            else if(bnc.bouncetype==BNC_DEBRIS) { s_sprintf(debrisname)("debris/debris0%d", ((((int)(size_t)&bnc)&0xC0)>>6)+1); mdl = debrisname; }
            else if(bnc.bouncetype==BNC_BARRELDEBRIS) { s_sprintf(debrisname)("barreldebris/debris0%d", ((((int)(size_t)&bnc)&0xC0)>>6)+1); mdl = debrisname; }
            else { cull |= MDL_LIGHT|MDL_DYNSHADOW; cull &= ~MDL_CULL_DIST; }
            rendermodel(&bnc.light, mdl, ANIM_MAPMODEL|ANIM_LOOP, pos, yaw, pitch, cull);
        }
        loopv(projs)
        {
            projectile &p = projs[i];
            if(p.gun!=GUN_RL) continue;
            vec pos(p.o);
            pos.add(vec(p.offset).mul(p.offsetmillis/float(OFFSETMILLIS)));
            if(p.to==pos) continue;
            vec v(p.to);
            v.sub(pos);
            v.normalize();
            // the amount of distance in front of the smoke trail needs to change if the model does
            vectoyawpitch(v, yaw, pitch);
            yaw += 90;
            v.mul(3);
            v.add(pos);
            rendermodel(&p.light, "projectiles/rocket", ANIM_MAPMODEL|ANIM_LOOP, v, yaw, pitch, MDL_CULL_VFC|MDL_CULL_OCCLUDED|MDL_LIGHT);
        }
    }
};
