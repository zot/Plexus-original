#include "pch.h"
#include "engine.h"
#include "rendertarget.h"

VARP(shadowmap, 0, 0, 1);

extern void cleanshadowmap();
VARFP(shadowmapsize, 7, 9, 11, cleanshadowmap());
VARP(shadowmapradius, 64, 96, 256);
VAR(shadowmapheight, 0, 32, 128);
VARP(shadowmapdist, 128, 256, 512);
VARFP(fpshadowmap, 0, 0, 1, cleanshadowmap());
VARFP(shadowmapprecision, 0, 0, 1, cleanshadowmap());
VARR(shadowmapambient, 0, 0, 0xFFFFFF);

VARP(blurshadowmap, 0, 1, 3);
VARP(blursmsigma, 1, 100, 200);

#define SHADOWSKEW 0.7071068f

vec shadowoffset(0, 0, 0), shadowfocus(0, 0, 0), shadowdir(0, SHADOWSKEW, 1);
VAR(shadowmapcasters, 1, 0, 0);

void setshadowdir(int angle)
{
    shadowdir = vec(0, SHADOWSKEW, 1);
    shadowdir.rotate_around_z(angle*RAD);
}

VARFR(shadowmapangle, 0, 0, 360, setshadowdir(shadowmapangle));

void guessshadowdir()
{
    if(shadowmapangle) return;
    vec lightpos(0, 0, 0), casterpos(0, 0, 0);
    int numlights = 0, numcasters = 0;
    const vector<extentity *> &ents = et->getents();
    loopv(ents)
    {
        extentity &e = *ents[i];
        switch(e.type)
        {
            case ET_LIGHT:
                if(!e.attr1) { lightpos.add(e.o); numlights++; }
                break;

             case ET_MAPMODEL:
                casterpos.add(e.o);
                numcasters++;
                break;

             default:
                if(e.type<ET_GAMESPECIFIC) break;
                casterpos.add(e.o);
                numcasters++;
                break;
         }
    }
    if(!numlights || !numcasters) return;
    lightpos.div(numlights);
    casterpos.div(numcasters);
    vec dir(lightpos);
    dir.sub(casterpos);
    dir.z = 0;
    if(dir.iszero()) return;
    dir.normalize();
    dir.mul(SHADOWSKEW);
    dir.z = 1;
    shadowdir = dir;
}

bool shadowmapping = false;

static GLdouble shadowmapprojection[16], shadowmapmodelview[16];

VARP(shadowmapbias, 0, 5, 1024);
VARP(shadowmappeelbias, 0, 20, 1024);
VAR(smdepthpeel, 0, 1, 1);
VAR(smoothshadowmappeel, 1, 0, 0);

static struct shadowmaptexture : rendertarget
{
    const GLenum *colorformats() const
    {
        static const GLenum colorfmts[] = { GL_RGBA16F_ARB, GL_RGBA16, GL_RGBA, GL_RGBA8, GL_FALSE };
        int offset = fpshadowmap && hasTF && hasFBO ? 0 : (shadowmapprecision && hasFBO ? 1 : 2);
        return &colorfmts[offset];
    }

    bool swaptexs() const { return true; }

    bool scissorblur(int &x, int &y, int &w, int &h)
    {
        x = max(int(floor((scissorx1+1)/2*vieww)) - 2*blursize, 2);
        y = max(int(floor((scissory1+1)/2*viewh)) - 2*blursize, 2);
        w = min(int(ceil((scissorx2+1)/2*vieww)) + 2*blursize, vieww-2) - x;
        h = min(int(ceil((scissory2+1)/2*viewh)) + 2*blursize, viewh-2) - y;
        return true;
    }

    bool scissorrender(int &x, int &y, int &w, int &h)
    {
        x = y = 2;
        w = vieww - 2*2;
        h = viewh - 2*2;
        return true;
    }

    void doclear()
    {
        glClearColor(0, 0, 0, 0);
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
    }

    bool dorender()
    {
        // nvidia bug, must push modelview here, then switch to projection, then back to modelview before can safely modify it
        glPushMatrix();

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(-shadowmapradius, shadowmapradius, -shadowmapradius, shadowmapradius, -shadowmapdist, shadowmapdist);

        glMatrixMode(GL_MODELVIEW);

        vec skewdir(shadowdir);
        skewdir.neg();
        skewdir.rotate_around_z(-camera1->yaw*RAD);

        vec dir;
        vecfromyawpitch(camera1->yaw, camera1->pitch, 1, 0, dir);
        dir.z = 0;
        dir.mul(shadowmapradius);

        vec dirx, diry;
        vecfromyawpitch(camera1->yaw, 0, 0, 1, dirx);
        vecfromyawpitch(camera1->yaw, 0, 1, 0, diry);
        shadowoffset.x = -fmod(dirx.dot(camera1->o) - skewdir.x*camera1->o.z, 2.0f*shadowmapradius/vieww);
        shadowoffset.y = -fmod(diry.dot(camera1->o) - skewdir.y*camera1->o.z, 2.0f*shadowmapradius/viewh);

        GLfloat skew[] =
        {
            1, 0, 0, 0,
            0, 1, 0, 0,
            skewdir.x, skewdir.y, 1, 0,
            0, 0, 0, 1
        };
        glLoadMatrixf(skew);
        glTranslatef(skewdir.x*shadowmapheight + shadowoffset.x, skewdir.y*shadowmapheight + shadowoffset.y + dir.magnitude(), -shadowmapheight);
        glRotatef(camera1->yaw, 0, 0, -1);
        glTranslatef(-camera1->o.x, -camera1->o.y, -camera1->o.z);
        shadowfocus = camera1->o;
        shadowfocus.add(dir);
        shadowfocus.add(vec(-shadowdir.x, -shadowdir.y, 1).mul(shadowmapheight));
        shadowfocus.add(dirx.mul(shadowoffset.x));
        shadowfocus.add(diry.mul(shadowoffset.y));

        glGetDoublev(GL_PROJECTION_MATRIX, shadowmapprojection);
        glGetDoublev(GL_MODELVIEW_MATRIX, shadowmapmodelview);

        setenvparamf("shadowmapbias", SHPARAM_VERTEX, 0, -shadowmapbias/float(shadowmapdist), 1 - (shadowmapbias + (smoothshadowmappeel ? 0 : shadowmappeelbias))/float(shadowmapdist));

        shadowmapcasters = 0;
        shadowmapping = true;
        rendergame();
        shadowmapping = false;

        if(shadowmapcasters && smdepthpeel) 
        {
            int sx, sy, sw, sh;
            bool scissoring = rtscissor && scissorblur(sx, sy, sw, sh) && sw > 0 && sh > 0;
            if(scissoring) 
            {
                if(!hasFBO)
                {
                    sx += screen->w-vieww;
                    sy += screen->h-viewh;
                }
                glScissor(sx, sy, sw, sh);
            }
            if(!rtscissor || scissoring) rendershadowmapreceivers();
        }

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        return shadowmapcasters>0;
    }

    bool flipdebug() const { return false; }

    void dodebug(int w, int h)
    {
        if(shadowmapcasters)
        {
            glColorMask(GL_TRUE, GL_FALSE, GL_FALSE, GL_FALSE);
            debugscissor(w, h);
            glColorMask(GL_FALSE, GL_FALSE, GL_TRUE, GL_FALSE);
            debugblurtiles(w, h);
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        }
    }
} shadowmaptex;

void cleanshadowmap()
{
    shadowmaptex.cleanup(true);
}

void calcshadowmapbb(const vec &o, float xyrad, float zrad, float &x1, float &y1, float &x2, float &y2)
{
    vec skewdir(shadowdir);
    skewdir.neg();
    skewdir.rotate_around_z(-camera1->yaw*RAD);

    vec ro(o);
    ro.sub(camera1->o);
    ro.rotate_around_z(-camera1->yaw*RAD);
    ro.x += ro.z * skewdir.x + shadowoffset.x;
    ro.y += ro.z * skewdir.y + shadowmapradius * cosf(camera1->pitch*RAD) + shadowoffset.y;

    vec high(ro), low(ro);
    high.x += zrad * skewdir.x;
    high.y += zrad * skewdir.y;
    low.x -= zrad * skewdir.x;
    low.y -= zrad * skewdir.y;

    x1 = (min(high.x, low.x) - xyrad) / shadowmapradius;
    y1 = (min(high.y, low.y) - xyrad) / shadowmapradius;
    x2 = (max(high.x, low.x) + xyrad) / shadowmapradius;
    y2 = (max(high.y, low.y) + xyrad) / shadowmapradius;
}

bool addshadowmapcaster(const vec &o, float xyrad, float zrad)
{
    if(o.z + zrad <= shadowfocus.z - shadowmapdist || o.z - zrad >= shadowfocus.z) return false;

    float x1, y1, x2, y2;
    calcshadowmapbb(o, xyrad, zrad, x1, y1, x2, y2);

    if(!shadowmaptex.addblurtiles(x1, y1, x2, y2, 2)) return false;

    shadowmapcasters++;
    return true;
}

bool isshadowmapreceiver(vtxarray *va)
{
    if(!shadowmap || renderpath==R_FIXEDFUNCTION || !shadowmapcasters) return false;

    if(va->shadowmapmax.z <= shadowfocus.z - shadowmapdist || va->shadowmapmin.z >= shadowfocus.z) return false;

    float xyrad = SQRT2*0.5f*max(va->shadowmapmax.x-va->shadowmapmin.x, va->shadowmapmax.y-va->shadowmapmin.y),
          zrad = 0.5f*(va->shadowmapmax.z-va->shadowmapmin.z),
          x1, y1, x2, y2;
    if(xyrad<0 || zrad<0) return false;

    vec center(va->shadowmapmin.tovec());
    center.add(va->shadowmapmax.tovec()).mul(0.5f);
    calcshadowmapbb(center, xyrad, zrad, x1, y1, x2, y2);

    return shadowmaptex.checkblurtiles(x1, y1, x2, y2, 2);

#if 0
    // cheaper inexact test
    float dz = va->o.z + va->size/2 - shadowfocus.z;
    float cx = shadowfocus.x - dz*shadowdir.x, cy = shadowfocus.y - dz*shadowdir.y;
    float skew = va->size/2*SHADOWSKEW;
    if(!shadowmap || !shadowmaptex ||
       va->o.z + va->size <= shadowfocus.z - shadowmapdist || va->o.z >= shadowfocus.z ||
       va->o.x + va->size <= cx - shadowmapradius-skew || va->o.x >= cx + shadowmapradius+skew || 
       va->o.y + va->size <= cy - shadowmapradius-skew || va->o.y >= cy + shadowmapradius+skew) 
        return false;
    return true;
#endif
}

bool isshadowmapcaster(const vec &o, float rad)
{
    // cheaper inexact test
    float dz = o.z - shadowfocus.z;
    float cx = shadowfocus.x - dz*shadowdir.x, cy = shadowfocus.y - dz*shadowdir.y;
    float skew = rad*SHADOWSKEW;
    if(!shadowmapping ||
       o.z + rad <= shadowfocus.z - shadowmapdist || o.z - rad >= shadowfocus.z ||
       o.x + rad <= cx - shadowmapradius-skew || o.x - rad >= cx + shadowmapradius+skew ||
       o.y + rad <= cy - shadowmapradius-skew || o.y - rad >= cy + shadowmapradius+skew)
        return false;
    return true;
}

void pushshadowmap()
{
    if(!shadowmap || !shadowmaptex.rendertex) return;

    glActiveTexture_(GL_TEXTURE7_ARB);
    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, shadowmaptex.rendertex);

    glActiveTexture_(GL_TEXTURE2_ARB);
    glEnable(GL_TEXTURE_2D);
    glMatrixMode(GL_TEXTURE);
    glLoadIdentity();
    glTranslatef(0.5f, 0.5f, 1-shadowmapbias/float(shadowmapdist));
    glScalef(0.5f, 0.5f, -1);
    glMultMatrixd(shadowmapprojection);
    glMultMatrixd(shadowmapmodelview);
    glPushMatrix();
    glMatrixMode(GL_MODELVIEW);
    glActiveTexture_(GL_TEXTURE0_ARB);
    glClientActiveTexture_(GL_TEXTURE0_ARB);

    float r, g, b;
	if(!shadowmapambient)
	{
		if(hdr.skylight[0] || hdr.skylight[1] || hdr.skylight[2])
		{
			r = max(25.0f, 0.4f*hdr.ambient + 0.6f*max(hdr.ambient, hdr.skylight[0]));
			g = max(25.0f, 0.4f*hdr.ambient + 0.6f*max(hdr.ambient, hdr.skylight[1]));
			b = max(25.0f, 0.4f*hdr.ambient + 0.6f*max(hdr.ambient, hdr.skylight[2]));
		}
		else r = g = b = max(25.0f, 2.0f*hdr.ambient);
	}
    else if(shadowmapambient<=255) r = g = b = shadowmapambient;
    else { r = (shadowmapambient>>16)&0xFF; g = (shadowmapambient>>8)&0xFF; b = shadowmapambient&0xFF; }
    setenvparamf("shadowmapambient", SHPARAM_PIXEL, 7, r/255.0f, g/255.0f, b/255.0f);
}

void adjustshadowmatrix(const ivec &o, float scale)
{
    if(!shadowmap || !shadowmaptex.rendertex) return;

    glActiveTexture_(GL_TEXTURE2_ARB);
    glMatrixMode(GL_TEXTURE);
    glPopMatrix();
    glPushMatrix();
    glTranslatef(o.x, o.y, o.z);
    glScalef(scale, scale, scale);
    glMatrixMode(GL_MODELVIEW);
    glActiveTexture_(GL_TEXTURE0_ARB);
}

void popshadowmap()
{
    if(!shadowmap || !shadowmaptex.rendertex) return;

    glActiveTexture_(GL_TEXTURE7_ARB);
    glDisable(GL_TEXTURE_2D);

    glActiveTexture_(GL_TEXTURE2_ARB);
    glMatrixMode(GL_TEXTURE);
    glPopMatrix();
    glMatrixMode(GL_MODELVIEW);

    glActiveTexture_(GL_TEXTURE0_ARB);
}

void rendershadowmap()
{
    if(!shadowmap || renderpath==R_FIXEDFUNCTION) return;

    // Apple/ATI bug - fixed-function fog state can force software fallback even when fragment program is enabled
    glDisable(GL_FOG); 
    shadowmaptex.render(1<<shadowmapsize, 1<<shadowmapsize, blurshadowmap, blursmsigma/100.0f);
    glEnable(GL_FOG);
}

VAR(debugsm, 0, 0, 1);

void viewshadowmap()
{
    if(!shadowmap) return;
    shadowmaptex.debug();
}

