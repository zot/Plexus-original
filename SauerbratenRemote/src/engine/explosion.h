// eye space depth texture for soft particles, done at low res then blurred to prevent ugly jaggies
VARP(depthfxfpscale, 1, 1<<12, 1<<16);
VARP(depthfxscale, 1, 1<<6, 1<<8);
VARP(depthfxblend, 1, 16, 64);
VAR(depthfxmargin, 0, 16, 64);
VAR(depthfxbias, 0, 1, 64);

extern void cleanupdepthfx();
VARFP(fpdepthfx, 0, 0, 1, cleanupdepthfx());
VARFP(depthfxprecision, 0, 0, 1, cleanupdepthfx());
VARP(depthfxemuprecision, 0, 1, 1);
VARFP(depthfxsize, 6, 7, 12, cleanupdepthfx());
VARP(depthfx, 0, 1, 1);
VARFP(depthfxrect, 0, 0, 1, cleanupdepthfx());
VARFP(depthfxfilter, 0, 1, 1, cleanupdepthfx());
VARP(blurdepthfx, 0, 1, 7);
VARP(blurdepthfxsigma, 1, 50, 200);
VAR(depthfxscissor, 0, 2, 2);
VAR(debugdepthfx, 0, 0, 1);

#define MAXDFXRANGES 4

void *depthfxowners[MAXDFXRANGES];
float depthfxranges[MAXDFXRANGES];
int numdepthfxranges = 0;
vec depthfxmin(1e16f, 1e16f, 1e16f), depthfxmax(1e16f, 1e16f, 1e16f);

static struct depthfxtexture : rendertarget
{
    const GLenum *colorformats() const
    {
        static const GLenum colorfmts[] = { GL_FLOAT_RG16_NV, GL_RGB16F_ARB, GL_RGB16, GL_RGBA, GL_RGBA8, GL_RGB, GL_RGB8, GL_FALSE };
        return &colorfmts[hasTF && hasFBO ? (fpdepthfx ? (hasNVFB && texrect() && !filter() ? 0 : 1) : (depthfxprecision ? 2 : 3)) : 3];
    }

    float eyedepth(const vec &p) const
    {
        return max(-(p.x*mvmatrix[2] + p.y*mvmatrix[6] + p.z*mvmatrix[10] + mvmatrix[14]), 0.0f);
    }

    void addscissorvert(const vec &v, float &sx1, float &sy1, float &sx2, float &sy2)
    {
        float w = v.x*mvpmatrix[3] + v.y*mvpmatrix[7] + v.z*mvpmatrix[11] + mvpmatrix[15],
              x = (v.x*mvpmatrix[0] + v.y*mvpmatrix[4] + v.z*mvpmatrix[8] + mvpmatrix[12]) / w,
              y = (v.x*mvpmatrix[1] + v.y*mvpmatrix[5] + v.z*mvpmatrix[9] + mvpmatrix[13]) / w;
        sx1 = min(sx1, x);
        sy1 = min(sy1, y);
        sx2 = max(sx2, x);
        sy2 = max(sy2, y);
    }

    bool addscissorbox(const vec &center, float size)
    {
        extern float fovy, aspect;
        vec e(center.x*mvmatrix[0] + center.y*mvmatrix[4] + center.z*mvmatrix[8] + mvmatrix[12],
              center.x*mvmatrix[1] + center.y*mvmatrix[5] + center.z*mvmatrix[9] + mvmatrix[13],
              center.x*mvmatrix[2] + center.y*mvmatrix[6] + center.z*mvmatrix[10] + mvmatrix[14]);
        float zz = e.z*e.z, xx = e.x*e.x, yy = e.y*e.y, rr = size*size,
              dx = zz*(xx + zz) - rr*zz, dy = zz*(yy + zz) - rr*zz,
              focaldist = 1.0f/tan(fovy*0.5f*RAD),
              left = -1, right = 1, bottom = -1, top = 1;
        #define CHECKPLANE(c, dir, focaldist, low, high) \
        do { \
            float nc = (size*e.c dir drt)/(c##c + zz), \
                  nz = (size - nc*e.c)/e.z, \
                  pz = (c##c + zz - rr)/(e.z - nz/nc*e.c); \
            if(pz < 0) \
            { \
                float c = nz*(focaldist)/nc, \
                      pc = -pz*nz/nc; \
                if(pc < e.c) low = c; \
                else if(pc > e.c) high = c; \
            } \
        } while(0)
        if(dx > 0)
        {
            float drt = sqrt(dx);
            CHECKPLANE(x, -, focaldist/aspect, left, right);
            CHECKPLANE(x, +, focaldist/aspect, left, right);
        }
        if(dy > 0)
        {
            float drt = sqrt(dy);
            CHECKPLANE(y, -, focaldist, bottom, top);
            CHECKPLANE(y, +, focaldist, bottom, top);
        }
        return addblurtiles(left, bottom, right, top);
    }

    bool addscissorbox(const vec &bbmin, const vec &bbmax)
    {
        float sx1 = 1, sy1 = 1, sx2 = -1, sy2 = -1;
        loopi(8)
        {
            vec v(i&1 ? bbmax.x : bbmin.x, i&2 ? bbmax.y : bbmin.y, i&4 ? bbmax.z : bbmin.z);
            addscissorvert(v, sx1, sy1, sx2, sy2);
        }
        return addblurtiles(sx1, sy1, sx2, sy2);
    }

    bool screenview() const { return depthfxrect!=0; }
    bool texrect() const { return depthfxrect && hasTR; }
    bool filter() const { return depthfxfilter!=0; }
    bool highprecision() const { return colorfmt==GL_FLOAT_RG16_NV || colorfmt==GL_RGB16F_ARB || colorfmt==GL_RGB16; }
    bool emulatehighprecision() const { return depthfxemuprecision && !depthfxfilter; }

    bool shouldrender()
    {
        extern void finddepthfxranges();
        finddepthfxranges();
        return (numdepthfxranges && scissorx1 < scissorx2 && scissory1 < scissory2) || debugdepthfx;
    }

    bool dorender()
    {
        glClearColor(1, 1, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        depthfxing = true;
        refracting = -1;

        extern void renderdepthobstacles(const vec &bbmin, const vec &bbmax, float scale, float *ranges, int numranges);
        float scale = depthfxscale;
        float *ranges = depthfxranges;
        int numranges = numdepthfxranges;
        if(highprecision())
        {
            scale = depthfxfpscale;
            ranges = NULL;
            numranges = 0;
        }
        else if(emulatehighprecision())
        {
            scale = depthfxfpscale;
            ranges = NULL;
            numranges = -3;
        }
        renderdepthobstacles(depthfxmin, depthfxmax, scale, ranges, numranges);

        refracting = 0;
        depthfxing = false;

        return numdepthfxranges > 0;
    }

    void dodebug(int w, int h)
    {
        if(numdepthfxranges > 0)
        {
            glColor3f(0, 1, 0);
            debugscissor(w, h, true);
            glColor3f(0, 0, 1);
            debugblurtiles(w, h, true);
            glColor3f(1, 1, 1);
        }
    }
} depthfxtex;

void cleanupdepthfx()
{
    depthfxtex.cleanup(true);
}

void viewdepthfxtex()
{
    if(!depthfx) return;
    depthfxtex.debug();
}

bool depthfxing = false;

void drawdepthfxtex()
{
    if(!depthfx || renderpath==R_FIXEDFUNCTION) return;

    // Apple/ATI bug - fixed-function fog state can force software fallback even when fragment program is enabled
    glDisable(GL_FOG);
    depthfxtex.render(1<<depthfxsize, 1<<depthfxsize, blurdepthfx, blurdepthfxsigma/100.0f);
    glEnable(GL_FOG);
}

//cache our unit hemisphere
static GLushort *hemiindices = NULL;
static vec *hemiverts = NULL;
static int heminumverts = 0, heminumindices = 0;
static GLuint hemivbuf = 0, hemiebuf = 0;

static void subdivide(int depth, int face);

static void genface(int depth, int i1, int i2, int i3)
{
    int face = heminumindices; heminumindices += 3;
    hemiindices[face]   = i1;
    hemiindices[face+1] = i2;
    hemiindices[face+2] = i3;
    subdivide(depth, face);
}

static void subdivide(int depth, int face)
{
    if(depth-- <= 0) return;
    int idx[6];
    loopi(3) idx[i] = hemiindices[face+i];
    loopi(3)
    {
        int vert = heminumverts++;
        hemiverts[vert] = vec(hemiverts[idx[i]]).add(hemiverts[idx[(i+1)%3]]).normalize(); //push on to unit sphere
        idx[3+i] = vert;
        hemiindices[face+i] = vert;
    }
    subdivide(depth, face);
    loopi(3) genface(depth, idx[i], idx[3+i], idx[3+(i+2)%3]);
}

//subdiv version wobble much more nicely than a lat/longitude version
static void inithemisphere(int hres, int depth)
{
    const int tris = hres << (2*depth);
    heminumverts = heminumindices = 0;
    DELETEA(hemiverts);
    DELETEA(hemiindices);
    hemiverts = new vec[tris+1];
    hemiindices = new GLushort[tris*3];
    hemiverts[heminumverts++] = vec(0.0f, 0.0f, 1.0f); //build initial 'hres' sided pyramid
    loopi(hres)
    {
        float a = PI2*float(i)/hres;
        hemiverts[heminumverts++] = vec(cosf(a), sinf(a), 0.0f);
    }
    loopi(hres) genface(depth, 0, i+1, 1+(i+1)%hres);

    if(hasVBO)
    {
        if(renderpath!=R_FIXEDFUNCTION)
        {
            if(!hemivbuf) glGenBuffers_(1, &hemivbuf);
            glBindBuffer_(GL_ARRAY_BUFFER_ARB, hemivbuf);
            glBufferData_(GL_ARRAY_BUFFER_ARB, heminumverts*sizeof(vec), hemiverts, GL_STATIC_DRAW_ARB);
            DELETEA(hemiverts);
        }

        if(!hemiebuf) glGenBuffers_(1, &hemiebuf);
        glBindBuffer_(GL_ELEMENT_ARRAY_BUFFER_ARB, hemiebuf);
        glBufferData_(GL_ELEMENT_ARRAY_BUFFER_ARB, heminumindices*sizeof(GLushort), hemiindices, GL_STATIC_DRAW_ARB);
        DELETEA(hemiindices);
    }
}

static GLuint expmodtex[2] = {0, 0};
static GLuint lastexpmodtex = 0;

static GLuint createexpmodtex(int size, float minval)
{
    uchar *data = new uchar[size*size], *dst = data;
    loop(y, size) loop(x, size)
    {
        float dx = 2*float(x)/(size-1) - 1, dy = 2*float(y)/(size-1) - 1;
        float z = max(0.0f, 1.0f - dx*dx - dy*dy);
        if(minval) z = sqrtf(z);
        else loopk(2) z *= z;
        *dst++ = uchar(max(z, minval)*255);
    }
    GLuint tex = 0;
    glGenTextures(1, &tex);
    createtexture(tex, size, size, data, 3, true, GL_ALPHA);
    delete[] data;
    return tex;
}

static struct expvert
{
    vec pos;
    float u, v, s, t;
} *expverts = NULL;
static GLuint expvbuf = 0;

static void animateexplosion()
{
    static int lastexpmillis = 0;
    if(expverts && lastexpmillis == lastmillis)
    {
        if(hasVBO) glBindBuffer_(GL_ARRAY_BUFFER_ARB, expvbuf);
        return;
    }
    lastexpmillis = lastmillis;
    vec center = vec(13.0f, 2.3f, 7.1f);  //only update once per frame! - so use the same center for all...
    if(!expverts) expverts = new expvert[heminumverts];
    loopi(heminumverts)
    {
        expvert &e = expverts[i];
        vec &v = hemiverts[i];
        //texgen - scrolling billboard
        e.u = v.x*0.5f + 0.0004f*lastmillis;
        e.v = v.y*0.5f + 0.0004f*lastmillis;
        //ensure the mod texture is wobbled
        e.s = v.x*0.5f + 0.5f;
        e.t = v.y*0.5f + 0.5f;
        //wobble - similar to shader code
        float wobble = v.dot(center) + 0.002f*lastmillis;
        wobble -= floor(wobble);
        wobble = 1.0f + fabs(wobble - 0.5f)*0.5f;
        e.pos = vec(v).mul(wobble);
    }

    if(hasVBO)
    {
        if(!expvbuf) glGenBuffers_(1, &expvbuf);
        glBindBuffer_(GL_ARRAY_BUFFER_ARB, expvbuf);
        glBufferData_(GL_ARRAY_BUFFER_ARB, heminumverts*sizeof(expvert), expverts, GL_STREAM_DRAW_ARB);
    }
}

static struct spherevert
{
    vec pos;
    float s, t;
} *sphereverts = NULL;
static GLushort *sphereindices = NULL;
static int spherenumverts = 0, spherenumindices = 0;
static GLuint spherevbuf = 0, sphereebuf = 0;

static void initsphere(int slices, int stacks)
{
    DELETEA(sphereverts);
    spherenumverts = (stacks+1)*(slices+1);
    sphereverts = new spherevert[spherenumverts];
    float ds = 1.0f/slices, dt = 1.0f/stacks, t = 1.0f;
    loopi(stacks+1)
    {
        float rho = M_PI*(1-t), s = 0.0f;
        loopj(slices+1)
        {
            float theta = j==slices ? 0 : 2*M_PI*s;
            spherevert &v = sphereverts[i*(slices+1) + j];
            v.pos = vec(-sin(theta)*sin(rho), cos(theta)*sin(rho), cos(rho));
            v.s = s;
            v.t = t;
            s += ds;
        }
        t -= dt;
    }

    DELETEA(sphereindices);
    spherenumindices = stacks*slices*3*2;
    sphereindices = new ushort[spherenumindices];
    GLushort *curindex = sphereindices;
    loopi(stacks)
    {
        loopk(slices)
        {
            int j = i%2 ? slices-k-1 : k;

            *curindex++ = i*(slices+1)+j;
            *curindex++ = (i+1)*(slices+1)+j;
            *curindex++ = i*(slices+1)+j+1;

            *curindex++ = i*(slices+1)+j+1;
            *curindex++ = (i+1)*(slices+1)+j;
            *curindex++ = (i+1)*(slices+1)+j+1;
        }
    }

    if(hasVBO)
    {
        if(!spherevbuf) glGenBuffers_(1, &spherevbuf);
        glBindBuffer_(GL_ARRAY_BUFFER_ARB, spherevbuf);
        glBufferData_(GL_ARRAY_BUFFER_ARB, spherenumverts*sizeof(spherevert), sphereverts, GL_STATIC_DRAW_ARB);
        DELETEA(sphereverts);

        if(!sphereebuf) glGenBuffers_(1, &sphereebuf);
        glBindBuffer_(GL_ELEMENT_ARRAY_BUFFER_ARB, sphereebuf);
        glBufferData_(GL_ELEMENT_ARRAY_BUFFER_ARB, spherenumindices*sizeof(GLushort), sphereindices, GL_STATIC_DRAW_ARB);
        DELETEA(sphereindices);
    }
}

VARP(explosion2d, 0, 0, 1);

static void setupexplosion()
{
    if(renderpath!=R_FIXEDFUNCTION || maxtmus>=2)
    {
        if(!expmodtex[0]) expmodtex[0] = createexpmodtex(64, 0);
        if(!expmodtex[1]) expmodtex[1] = createexpmodtex(64, 0.25f);
        lastexpmodtex = 0;
    }

    if(renderpath!=R_FIXEDFUNCTION)
    {
        if(glaring)
        {
            if(explosion2d) SETSHADER(explosion2dglare); else SETSHADER(explosion3dglare);
        }
        else if(!reflecting && !refracting && depthfx && depthfxtex.rendertex && numdepthfxranges>0)
        {
            if(depthfxtex.target==GL_TEXTURE_RECTANGLE_ARB)
            {
                if(!depthfxtex.highprecision())
                {
                    if(explosion2d) SETSHADER(explosion2dsoft8rect); else SETSHADER(explosion3dsoft8rect);
                }
                else if(explosion2d) SETSHADER(explosion2dsoftrect); else SETSHADER(explosion3dsoftrect);

                setlocalparamf("depthfxview", SHPARAM_VERTEX, 6, 0.5f*depthfxtex.vieww, 0.5f*depthfxtex.viewh);
            }
            else
            {
                if(!depthfxtex.highprecision())
                {
                    if(explosion2d) SETSHADER(explosion2dsoft8); else SETSHADER(explosion3dsoft8);
                }
                else if(explosion2d) SETSHADER(explosion2dsoft); else SETSHADER(explosion3dsoft);

                setlocalparamf("depthfxview", SHPARAM_VERTEX, 6, 0.5f*float(depthfxtex.vieww)/depthfxtex.texw, 0.5f*float(depthfxtex.viewh)/depthfxtex.texh);
            }

            glActiveTexture_(GL_TEXTURE2_ARB);
            glBindTexture(depthfxtex.target, depthfxtex.rendertex);
            glActiveTexture_(GL_TEXTURE0_ARB);
        }
        else if(explosion2d) SETSHADER(explosion2d); else SETSHADER(explosion3d);
    }

    if(renderpath==R_FIXEDFUNCTION || explosion2d)
    {
        if(!hemiverts && !hemivbuf) inithemisphere(5, 2);
        if(renderpath==R_FIXEDFUNCTION) animateexplosion();
        if(hasVBO)
        {
            if(renderpath!=R_FIXEDFUNCTION) glBindBuffer_(GL_ARRAY_BUFFER_ARB, hemivbuf);
            glBindBuffer_(GL_ELEMENT_ARRAY_BUFFER_ARB, hemiebuf);
        }

        expvert *verts = renderpath==R_FIXEDFUNCTION ? (hasVBO ? 0 : expverts) : (expvert *)hemiverts;

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, renderpath==R_FIXEDFUNCTION ? sizeof(expvert) : sizeof(vec), verts);

        if(renderpath==R_FIXEDFUNCTION)
        {
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, sizeof(expvert), &verts->u);

            if(maxtmus>=2)
            {
                setuptmu(0, "C * T", "= Ca");

                glActiveTexture_(GL_TEXTURE1_ARB);
                glClientActiveTexture_(GL_TEXTURE1_ARB);

                glEnable(GL_TEXTURE_2D);
                setuptmu(1, "P * Ta x 4", "Pa * Ta x 4");
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                glTexCoordPointer(2, GL_FLOAT, sizeof(expvert), &verts->s);

                glActiveTexture_(GL_TEXTURE0_ARB);
                glClientActiveTexture_(GL_TEXTURE0_ARB);
            }
        }
    }
    else
    {
        if(!sphereverts && !spherevbuf) initsphere(12, 6);

        if(hasVBO)
        {
            glBindBuffer_(GL_ARRAY_BUFFER_ARB, spherevbuf);
            glBindBuffer_(GL_ELEMENT_ARRAY_BUFFER_ARB, sphereebuf);
        }

        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glVertexPointer(3, GL_FLOAT, sizeof(spherevert), &sphereverts->pos);
        glTexCoordPointer(2, GL_FLOAT, sizeof(spherevert), &sphereverts->s);
    }
}

static void drawexpverts(int numverts, int numindices, GLushort *indices)
{
    if(hasDRE) glDrawRangeElements_(GL_TRIANGLES, 0, numverts-1, numindices, GL_UNSIGNED_SHORT, indices);
    else glDrawElements(GL_TRIANGLES, numindices, GL_UNSIGNED_SHORT, indices);
    xtraverts += numindices;
    glde++;
}

static void drawexplosion(bool inside, uchar r, uchar g, uchar b, uchar a)
{
    if((renderpath!=R_FIXEDFUNCTION || maxtmus>=2) && lastexpmodtex != expmodtex[inside ? 1 : 0])
    {
        glActiveTexture_(GL_TEXTURE1_ARB);
        lastexpmodtex = expmodtex[inside ? 1 :0];
        glBindTexture(GL_TEXTURE_2D, lastexpmodtex);
        glActiveTexture_(GL_TEXTURE0_ARB);
    }
    int passes = !reflecting && !refracting && inside ? 2 : 1;
    if(renderpath!=R_FIXEDFUNCTION && !explosion2d)
    {
        if(inside) glScalef(1, 1, -1);
        loopi(passes)
        {
            glColor4ub(r, g, b, i ? a/2 : a);
            if(i) glDepthFunc(GL_GEQUAL);
            drawexpverts(spherenumverts, spherenumindices, sphereindices);
            if(i) glDepthFunc(GL_LESS);
        }
        return;
    }
    loopi(passes)
    {
        glColor4ub(r, g, b, i ? a/2 : a);
        if(i)
        {
            glScalef(1, 1, -1);
            glDepthFunc(GL_GEQUAL);
        }
        if(inside)
        {
            if(passes >= 2)
            {
                glCullFace(GL_BACK);
                drawexpverts(heminumverts, heminumindices, hemiindices);
                glCullFace(GL_FRONT);
            }
            glScalef(1, 1, -1);
        }
        drawexpverts(heminumverts, heminumindices, hemiindices);
        if(i) glDepthFunc(GL_LESS);
    }
}

static void cleanupexplosion()
{
    glDisableClientState(GL_VERTEX_ARRAY);
    if(renderpath==R_FIXEDFUNCTION)
    {
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);

        if(maxtmus>=2)
        {
            resettmu(0);

            glActiveTexture_(GL_TEXTURE1_ARB);
            glClientActiveTexture_(GL_TEXTURE1_ARB);

            glDisable(GL_TEXTURE_2D);
            resettmu(1);
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);

            glActiveTexture_(GL_TEXTURE0_ARB);
            glClientActiveTexture_(GL_TEXTURE0_ARB);
        }
    }
    else
    {
        if(explosion2d) glDisableClientState(GL_TEXTURE_COORD_ARRAY);

        if(fogging) setfogplane(1, reflectz);
    }

    if(hasVBO)
    {
        glBindBuffer_(GL_ARRAY_BUFFER_ARB, 0);
        glBindBuffer_(GL_ELEMENT_ARRAY_BUFFER_ARB, 0);
    }
}

static void deleteexplosions()
{
    loopi(2) if(expmodtex[i]) { glDeleteTextures(1, &expmodtex[i]); expmodtex[i] = 0; }
    if(hemivbuf) { glDeleteBuffers_(1, &hemivbuf); hemivbuf = 0; }
    if(hemiebuf) { glDeleteBuffers_(1, &hemiebuf); hemiebuf = 0; }
    DELETEA(hemiverts);
    DELETEA(hemiindices);
    if(expvbuf) { glDeleteBuffers_(1, &expvbuf); expvbuf = 0; }
    DELETEA(expverts);
    if(spherevbuf) { glDeleteBuffers_(1, &spherevbuf); spherevbuf = 0; }
    if(sphereebuf) { glDeleteBuffers_(1, &sphereebuf); sphereebuf = 0; }
    DELETEA(sphereverts);
    DELETEA(sphereindices);
}

static const float WOBBLE = 1.25f;

struct fireballrenderer : listrenderer
{
    fireballrenderer(int type)
        : listrenderer("packages/particles/explosion.jpg", type, 0, 0)
    {}

    void startrender()
    {
        setupexplosion();
    }

    void endrender()
    {
        cleanupexplosion();
        particleshader->set();
    }

    void cleanup()
    {
        deleteexplosions();
    }

    int finddepthfxranges(void **owners, float *ranges, int maxranges, vec &bbmin, vec &bbmax)
    {
        physent e;
        e.type = ENT_CAMERA;

        int numranges = 0;
        for(listparticle *p = list; p; p = p->next)
        {
            int ts = p->fade <= 5 ? 1 : lastmillis-p->millis;
            float pmax = p->val,
                  size = p->fade ? float(ts)/p->fade : 1,
                  psize = (p->size + pmax * size)*WOBBLE;
            if(2*(p->size + pmax)*WOBBLE < depthfxblend ||
               (!depthfxtex.highprecision() && !depthfxtex.emulatehighprecision() && psize > depthfxscale - depthfxbias) ||
               isvisiblesphere(psize, p->o) >= VFC_FOGGED) continue;

            e.o = p->o;
            e.radius = e.eyeheight = e.aboveeye = psize;
            if(::collide(&e, vec(0, 0, 0), 0, false)) continue;

            if(depthfxscissor==2 && !depthfxtex.addscissorbox(p->o, psize)) continue;

            vec dir = camera1->o;
            dir.sub(p->o);
            float dist = dir.magnitude();
            dir.mul(psize/dist).add(p->o);
            float depth = depthfxtex.eyedepth(dir);

            loopk(3)
            {
                bbmin[k] = min(bbmin[k], p->o[k] - psize);
                bbmax[k] = max(bbmax[k], p->o[k] + psize);
            }

            int pos = numranges;
            loopi(numranges) if(depth < ranges[i]) { pos = i; break; }
            if(pos >= maxranges) continue;

            if(numranges > pos)
            {
                int moved = min(numranges-pos, maxranges-(pos+1));
                memmove(&ranges[pos+1], &ranges[pos], moved*sizeof(float));
                memmove(&owners[pos+1], &owners[pos], moved*sizeof(void *));
            }
            if(numranges < maxranges) numranges++;

            ranges[pos] = depth;
            owners[pos] = p;
        }

        return numranges;
    }

    void renderpart(listparticle *p, const vec &o, const vec &d, int blend, int ts, uchar *color)
    {
        float pmax = p->val,
              size = p->fade ? float(ts)/p->fade : 1,
              psize = p->size + pmax * size;

        if(isvisiblesphere(psize*WOBBLE, p->o) >= VFC_FOGGED) return;

        glPushMatrix();
        glTranslatef(o.x, o.y, o.z);
        if(fogging)
        {
            if(renderpath!=R_FIXEDFUNCTION) setfogplane(0, reflectz - o.z, true);
            else blend = (uchar)(blend * max(0.0f, min(1.0f, 1.0f - (reflectz - o.z)/waterfog)));
        }

        bool inside = o.dist(camera1->o) <= psize*WOBBLE;
        vec oc(o);
        oc.sub(camera1->o);
        if(reflecting) oc.z = o.z - reflectz;

        float yaw = inside ? camera1->yaw - 180 : atan2(oc.y, oc.x)/RAD - 90,
        pitch = (inside ? camera1->pitch : asin(oc.z/oc.magnitude())/RAD) - 90;
        vec rotdir;
        if(renderpath==R_FIXEDFUNCTION || explosion2d)
        {
            glRotatef(yaw, 0, 0, 1);
            glRotatef(pitch, 1, 0, 0);
            rotdir = vec(0, 0, 1);
        }
        else
        {
            vec s(1, 0, 0), t(0, 1, 0);
            s.rotate(pitch*RAD, vec(-1, 0, 0));
            s.rotate(yaw*RAD, vec(0, 0, -1));
            t.rotate(pitch*RAD, vec(-1, 0, 0));
            t.rotate(yaw*RAD, vec(0, 0, -1));

            rotdir = vec(-1, 1, -1).normalize();
            s.rotate(-lastmillis/7.0f*RAD, rotdir);
            t.rotate(-lastmillis/7.0f*RAD, rotdir);

            setlocalparamf("texgenS", SHPARAM_VERTEX, 2, 0.5f*s.x, 0.5f*s.y, 0.5f*s.z, 0.5f);
            setlocalparamf("texgenT", SHPARAM_VERTEX, 3, 0.5f*t.x, 0.5f*t.y, 0.5f*t.z, 0.5f);
        }

        if(renderpath!=R_FIXEDFUNCTION)
        {
            setlocalparamf("center", SHPARAM_VERTEX, 0, o.x, o.y, o.z);
            setlocalparamf("animstate", SHPARAM_VERTEX, 1, size, psize, pmax, float(lastmillis));
            if(!glaring && !reflecting && !refracting && depthfx && depthfxtex.rendertex && numdepthfxranges>0)
            {
                float scale = 0, offset = -1, texscale = 0;
                if(!depthfxtex.highprecision())
                {
                    float select[4] = { 0, 0, 0, 0 };
                    if(!depthfxtex.emulatehighprecision())
                    {
                        loopi(numdepthfxranges) if(depthfxowners[i]==p)
                        {
                            select[i] = float(depthfxscale)/depthfxblend;
                            scale = 1.0f/depthfxblend;
                            offset = -float(depthfxranges[i] - depthfxbias)/depthfxblend;
                            break;
                        }
                    }
                    else if(2*(p->size + pmax)*WOBBLE >= depthfxblend)
                    {
                        select[0] = float(depthfxfpscale)/depthfxblend;
                        select[1] = select[0]/256;
                        select[2] = select[1]/256;
                        scale = 1.0f/depthfxblend;
                        offset = 0;
                    }
                    setlocalparamfv("depthfxselect", SHPARAM_PIXEL, 6, select);
                }
                else if(2*(p->size + pmax)*WOBBLE >= depthfxblend)
                {
                    scale = 1.0f/depthfxblend;
                    offset = 0;
                    texscale = float(depthfxfpscale)/depthfxblend;
                }
                setlocalparamf("depthfxparams", SHPARAM_VERTEX, 5, scale, offset, texscale, inside ? blend/(2*255.0f) : 0);
                setlocalparamf("depthfxparams", SHPARAM_PIXEL, 5, scale, offset, texscale, inside ? blend/(2*255.0f) : 0);
            }
        }

        glRotatef(lastmillis/7.0f, -rotdir.x, rotdir.y, -rotdir.z);
        glScalef(-psize, psize, -psize);
        drawexplosion(inside, color[0], color[1], color[2], blend);

        glPopMatrix();
    }
};
static fireballrenderer fireballs(PT_FIREBALL|PT_GLARE), noglarefireballs(PT_FIREBALL);

void finddepthfxranges()
{
    depthfxmin = vec(1e16f, 1e16f, 1e16f);
    depthfxmax = vec(0, 0, 0);
    numdepthfxranges = fireballs.finddepthfxranges(depthfxowners, depthfxranges, MAXDFXRANGES, depthfxmin, depthfxmax);
    loopk(3)
    {
        depthfxmin[k] -= depthfxmargin;
        depthfxmax[k] += depthfxmargin;
    }

    if(depthfxscissor<2 && numdepthfxranges>0) depthfxtex.addscissorbox(depthfxmin, depthfxmax);
}

