// texture.cpp: texture slot management

#include "pch.h"
#include "engine.h"

static inline void reorienttexture(uchar *src, int sw, int sh, int bpp, uchar *dst, bool flipx, bool flipy, bool swapxy, bool normals = false)
{
    int stridex = bpp, stridey = bpp;
    if(swapxy) stridex *= sh; else stridey *= sw;
    if(flipx) { dst += (sw-1)*stridex; stridex = -stridex; }
    if(flipy) { dst += (sh-1)*stridey; stridey = -stridey; }
    loopi(sh)
    {
        uchar *curdst = dst;
        loopj(sw)
        {
            loopk(bpp) curdst[k] = *src++;
            if(normals)
            {
                if(flipx) curdst[0] = 255-curdst[0];
                if(flipy) curdst[1] = 255-curdst[1];
                if(swapxy) swap(curdst[0], curdst[1]);
            }
            curdst += stridex;
        }
        dst += stridey;
    }
}

SDL_Surface *texreorient(SDL_Surface *s, bool flipx, bool flipy, bool swapxy, int type = TEX_DIFFUSE)
{
    SDL_Surface *d = SDL_CreateRGBSurface(SDL_SWSURFACE, swapxy ? s->h : s->w, swapxy ? s->w : s->h, s->format->BitsPerPixel, s->format->Rmask, s->format->Gmask, s->format->Bmask, s->format->Amask);
    if(!d) fatal("create surface");
    reorienttexture((uchar *)s->pixels, s->w, s->h, s->format->BytesPerPixel, (uchar *)d->pixels, flipx, flipy, swapxy, type==TEX_NORMAL);
    SDL_FreeSurface(s);
    return d;
}

SDL_Surface *texrotate(SDL_Surface *s, int numrots, int type = TEX_DIFFUSE)
{
    // 1..3 rotate through 90..270 degrees, 4 flips X, 5 flips Y 
    if(numrots<1 || numrots>5) return s; 
    return texreorient(s, 
        numrots>=2 && numrots<=4, // flip X on 180/270 degrees
        numrots<=2 || numrots==5, // flip Y on 90/180 degrees
        (numrots&5)==1,           // swap X/Y on 90/270 degrees
        type);
}

SDL_Surface *texoffset(SDL_Surface *s, int xoffset, int yoffset)
{
    xoffset = max(xoffset, 0);
    xoffset %= s->w;
    yoffset = max(yoffset, 0);
    yoffset %= s->h;
    if(!xoffset && !yoffset) return s;
    SDL_Surface *d = SDL_CreateRGBSurface(SDL_SWSURFACE, s->w, s->h, s->format->BitsPerPixel, s->format->Rmask, s->format->Gmask, s->format->Bmask, s->format->Amask);
    if(!d) fatal("create surface");
    int depth = s->format->BytesPerPixel;
    uchar *src = (uchar *)s->pixels;
    loop(y, s->h)
    {
        uchar *dst = (uchar *)d->pixels+((y+yoffset)%d->h)*d->pitch;
        memcpy(dst+xoffset*depth, src, (s->w-xoffset)*depth);
        memcpy(dst, src+(s->w-xoffset)*depth, xoffset*depth);
        src += s->pitch;
    }
    SDL_FreeSurface(s);
    return d;
}

void texmad(SDL_Surface *s, const vec &mul, const vec &add)
{
    int maxk = min(int(s->format->BytesPerPixel), 3);
    uchar *src = (uchar *)s->pixels;
    loopi(s->h*s->w) 
    {
        loopk(maxk)
        {
            float val = src[k]*mul[k] + 255*add[k];
            src[k] = uchar(min(max(val, 0.0f), 255.0f));
        }
        src += s->format->BytesPerPixel;
    }
}

static SDL_Surface stubsurface;

SDL_Surface *texffmask(SDL_Surface *s, int minval)
{
    if(renderpath!=R_FIXEDFUNCTION) return s;
    if(nomasks || s->format->BytesPerPixel<3) { SDL_FreeSurface(s); return &stubsurface; }
    bool glow = false, envmap = true;
    uchar *src = (uchar *)s->pixels;
    loopi(s->h*s->w)
    {
        if(src[1]>minval) glow = true;
        if(src[2]>minval) { glow = envmap = true; break; }
        src += s->format->BytesPerPixel;
    }
    if(!glow && !envmap) { SDL_FreeSurface(s); return &stubsurface; }
    SDL_Surface *m = SDL_CreateRGBSurface(SDL_SWSURFACE, s->w, s->h, envmap ? 16 : 8, 0, 0, 0, 0);
    if(!m) fatal("create surface");
    uchar *dst = (uchar *)m->pixels;
    src = (uchar *)s->pixels;
    loopi(s->h*s->w)
    {
        *dst++ = src[1];
        if(envmap) *dst++ = src[2];
        src += s->format->BytesPerPixel;
    }
    SDL_FreeSurface(s);
    return m;
}

void texdup(SDL_Surface *s, int srcchan, int dstchan)
{
    if(srcchan==dstchan || max(srcchan, dstchan) >= s->format->BytesPerPixel) return;
    uchar *src = (uchar *)s->pixels;
    loopi(s->h*s->w)
    {
        src[dstchan] = src[srcchan];
        src += s->format->BytesPerPixel;
    }
}

SDL_Surface *texdecal(SDL_Surface *s)
{
    if(renderpath!=R_FIXEDFUNCTION || hasTE) return s;
    SDL_Surface *m = SDL_CreateRGBSurface(SDL_SWSURFACE, s->w, s->h, 16, 0, 0, 0, 0);
    if(!m) fatal("create surface");
    uchar *dst = (uchar *)m->pixels, *src = (uchar *)s->pixels;
    loopi(s->h*s->w)
    {
        *dst++ = *src;
        *dst++ = 255 - *src;
        src += s->format->BytesPerPixel;
    }
    SDL_FreeSurface(s);
    return m;
}

VAR(hwtexsize, 1, 0, 0);
VAR(hwcubetexsize, 1, 0, 0);
VAR(hwmaxaniso, 1, 0, 0);
VARFP(maxtexsize, 0, 0, 1<<12, initwarning("texture quality", INIT_LOAD));
VARFP(texreduce, 0, 0, 12, initwarning("texture quality", INIT_LOAD));
VARFP(texcompress, 0, 1<<10, 1<<12, initwarning("texture quality", INIT_LOAD));
VARFP(trilinear, 0, 1, 1, initwarning("texture filtering", INIT_LOAD));
VARFP(bilinear, 0, 1, 1, initwarning("texture filtering", INIT_LOAD));
VARFP(aniso, 0, 0, 16, initwarning("texture filtering", INIT_LOAD));

GLenum compressedformat(GLenum format, int w, int h, bool force = false)
{
#ifdef __APPLE__
#undef GL_COMPRESSED_RGB_S3TC_DXT1_EXT
#undef GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
#define GL_COMPRESSED_RGB_S3TC_DXT1_EXT GL_COMPRESSED_RGB_ARB
#define GL_COMPRESSED_RGBA_S3TC_DXT5_EXT GL_COMPRESSED_RGBA_ARB
#endif
    if(hasTC && texcompress && (force || max(w, h) >= texcompress)) switch(format)
    {
        case GL_RGB5:
        case GL_RGB8:
        case GL_RGB: return GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
        case GL_RGBA: return GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
    }
    return format;
}

int formatsize(GLenum format)
{
    switch(format)
    {
        case GL_LUMINANCE:
        case GL_ALPHA: return 1;
        case GL_LUMINANCE_ALPHA: return 2;
        case GL_RGB: return 3;
        case GL_RGBA: return 4;
        default: return 4;
    }
}

VARFP(hwmipmap, 0, 0, 1, initwarning("texture filtering", INIT_LOAD));

void createtexture(int tnum, int w, int h, void *pixels, int clamp, bool mipit, GLenum component, GLenum subtarget, bool compress, bool filter)
{
    GLenum target = subtarget;
    switch(subtarget)
    {
        case GL_TEXTURE_CUBE_MAP_POSITIVE_X_ARB:
        case GL_TEXTURE_CUBE_MAP_NEGATIVE_X_ARB:
        case GL_TEXTURE_CUBE_MAP_POSITIVE_Y_ARB:
        case GL_TEXTURE_CUBE_MAP_NEGATIVE_Y_ARB:
        case GL_TEXTURE_CUBE_MAP_POSITIVE_Z_ARB:
        case GL_TEXTURE_CUBE_MAP_NEGATIVE_Z_ARB:
            target = GL_TEXTURE_CUBE_MAP_ARB;
            break;
    }
    if(tnum)
    {
        glBindTexture(target, tnum);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(target, GL_TEXTURE_WRAP_S, clamp&1 ? GL_CLAMP_TO_EDGE : GL_REPEAT);
        if(target!=GL_TEXTURE_1D) glTexParameteri(target, GL_TEXTURE_WRAP_T, clamp&2 ? GL_CLAMP_TO_EDGE : GL_REPEAT);
        if(target==GL_TEXTURE_2D && hasAF && min(aniso, hwmaxaniso) > 0) glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, min(aniso, hwmaxaniso));
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, filter && bilinear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, 
            mipit ?
                (trilinear ? 
                    (bilinear ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_LINEAR) : 
                    (bilinear ? GL_LINEAR_MIPMAP_NEAREST : GL_NEAREST_MIPMAP_NEAREST)) :
                (filter && bilinear ? GL_LINEAR : GL_NEAREST));
        if(hasGM && mipit && pixels)
            glTexParameteri(target, GL_GENERATE_MIPMAP_SGIS, hwmipmap ? GL_TRUE : GL_FALSE);
    }
    GLenum format = component, type = GL_UNSIGNED_BYTE;
    switch(component)
    {
        case GL_FLOAT_RG16_NV:
        case GL_FLOAT_R32_NV:
        case GL_RGB16F_ARB:
        case GL_RGB32F_ARB:
            format = GL_RGB;
            type = GL_FLOAT;
            break;

        case GL_RGBA16F_ARB:
        case GL_RGBA32F_ARB:
            format = GL_RGBA;
            type = GL_FLOAT;
            break;

        case GL_DEPTH_COMPONENT:
            type = GL_FLOAT;
            break;

        case GL_RGB5:
        case GL_RGB8:
        case GL_RGB16:
            format = GL_RGB;
            break;

        case GL_RGBA8:
        case GL_RGBA16:
            format = GL_RGBA;
            break;
    }
    uchar *scaled = NULL;
    int hwlimit = target==GL_TEXTURE_CUBE_MAP_ARB ? hwcubetexsize : hwtexsize,
        sizelimit = mipit && maxtexsize ? min(maxtexsize, hwlimit) : hwlimit;
    if(pixels && max(w, h) > sizelimit && (!mipit || sizelimit < hwlimit))
    {
        int oldw = w, oldh = h;
        while(w > sizelimit || h > sizelimit) { w /= 2; h /= 2; }
        scaled = new uchar[w*h*formatsize(format)];
        gluScaleImage(format, oldw, oldh, type, pixels, w, h, type, scaled);
        pixels = scaled;
    }
    if(mipit && pixels)
    {
        GLenum compressed = compressedformat(component, w, h, compress);
        if(target==GL_TEXTURE_1D)
        {
            if(hasGM && hwmipmap) glTexImage1D(subtarget, 0, compressed, w, 0, format, type, pixels);
            else if(gluBuild1DMipmaps(subtarget, compressed, w, format, type, pixels))
            {
                if(compressed==component || gluBuild1DMipmaps(subtarget, component, w, format, type, pixels)) conoutf(CON_ERROR, "could not build mipmaps");
            }
        }
        else if(hasGM && hwmipmap) glTexImage2D(subtarget, 0, compressed, w, h, 0, format, type, pixels);
        else if(gluBuild2DMipmaps(subtarget, compressed, w, h, format, type, pixels))
        {
            if(compressed==component || gluBuild2DMipmaps(subtarget, component, w, h, format, type, pixels)) conoutf(CON_ERROR, "could not build mipmaps");
        }
    }
    else if(target==GL_TEXTURE_1D) glTexImage1D(subtarget, 0, component, w, 0, format, type, pixels);
    else glTexImage2D(subtarget, 0, component, w, h, 0, format, type, pixels);
    if(scaled) delete[] scaled;
}

hashtable<char *, Texture> textures;

Texture *notexture = NULL; // used as default, ensured to be loaded

static GLenum texformat(int bpp)
{
    switch(bpp)
    {
        case 8: return GL_LUMINANCE;
        case 16: return GL_LUMINANCE_ALPHA;
        case 24: return GL_RGB;
        case 32: return GL_RGBA;
        default: return 0;
    }
}

static void resizetexture(int &w, int &h, bool mipit = true, GLenum format = GL_RGB, GLenum target = GL_TEXTURE_2D)
{
    if(mipit) return;
    int hwlimit = target==GL_TEXTURE_CUBE_MAP_ARB ? hwcubetexsize : hwtexsize,
        sizelimit = mipit && maxtexsize ? min(maxtexsize, hwlimit) : hwlimit;
    int w2 = w, h2 = h;
    if(!hasNP2 && (w&(w-1) || h&(h-1)))
    {
        w2 = h2 = 1;
        while(w2 < w) w2 *= 2;
        while(h2 < h) h2 *= 2;
        if(w2 > sizelimit || (w - w2)/2 < (w2 - w)/2) w2 /= 2;
        if(h2 > sizelimit || (h - h2)/2 < (h2 - h)/2) h2 /= 2;
    }
    while(w2 > sizelimit || h2 > sizelimit)
    {
        w2 /= 2;
        h2 /= 2;
    }
    w = w2;
    h = h2;
}

static Texture *newtexture(Texture *t, const char *rname, SDL_Surface *s, int clamp = 0, bool mipit = true, bool canreduce = false, bool transient = false, bool compress = false)
{
    if(!t)
    {
        char *key = newstring(rname);
        t = &textures[key];
        t->name = key;
    }

    t->clamp = clamp;
    t->mipmap = mipit;
    t->type = s==&stubsurface ? Texture::STUB : (transient ? Texture::TRANSIENT : Texture::IMAGE);
    if(t->type==Texture::STUB)
    {
        t->w = t->h = t->xs = t->ys = t->bpp = 0;
        return t;
    }
    t->bpp = s->format->BitsPerPixel;
    t->w = t->xs = s->w;
    t->h = t->ys = s->h;

    glGenTextures(1, &t->id);
    if(canreduce) loopi(texreduce)
    {
        if(t->w > 1) t->w /= 2;
        if(t->h > 1) t->h /= 2;
    }
    GLenum format = texformat(t->bpp);
    resizetexture(t->w, t->h, mipit, format);
    uchar *pixels = (uchar *)s->pixels;
    if(t->w != t->xs || t->h != t->ys)
    {
        if(t->w*t->h > t->xs*t->ys) pixels = new uchar[formatsize(format)*t->w*t->h];
        gluScaleImage(format, t->xs, t->ys, GL_UNSIGNED_BYTE, s->pixels, t->w, t->h, GL_UNSIGNED_BYTE, pixels);
    }
    createtexture(t->id, t->w, t->h, pixels, clamp, mipit, format, GL_TEXTURE_2D, compress);
    if(pixels!=s->pixels) delete[] pixels;
    SDL_FreeSurface(s);
    return t;
}

#if SDL_BYTEORDER == SDL_BIG_ENDIAN
#define RMASK 0xff000000
#define GMASK 0x00ff0000
#define BMASK 0x0000ff00
#define AMASK 0x000000ff
#else
#define RMASK 0x000000ff
#define GMASK 0x0000ff00
#define BMASK 0x00ff0000
#define AMASK 0xff000000
#endif

SDL_Surface *creatergbasurface(SDL_Surface *os)
{
    SDL_Surface *ns = SDL_CreateRGBSurface(SDL_SWSURFACE, os->w, os->h, 32, RMASK, GMASK, BMASK, AMASK);
    if(!ns) fatal("creatergbsurface");
    SDL_BlitSurface(os, NULL, ns, NULL);
    SDL_FreeSurface(os);
    return ns;
}

SDL_Surface *scalesurface(SDL_Surface *os, int w, int h)
{
    SDL_Surface *ns = SDL_CreateRGBSurface(SDL_SWSURFACE, w, h, os->format->BitsPerPixel, os->format->Rmask, os->format->Gmask, os->format->Bmask, os->format->Amask);
    if(!ns) fatal("scalesurface");
    gluScaleImage(texformat(os->format->BitsPerPixel), os->w, os->h, GL_UNSIGNED_BYTE, os->pixels, w, h, GL_UNSIGNED_BYTE, ns->pixels);
    SDL_FreeSurface(os);
    return ns;
}

static vec parsevec(const char *arg)
{
    vec v(0, 0, 0);
    int i = 0;
    for(; arg[0] && (!i || arg[0]=='/') && i<3; arg += strcspn(arg, "/,><"), i++)
    {
        if(i) arg++;
        v[i] = atof(arg);
    }
    if(i==1) v.y = v.z = v.x;
    return v;
}

static SDL_Surface *texturedata(const char *tname, Slot::Tex *tex = NULL, bool msg = true, bool *compress = NULL)
{
    const char *cmds = NULL, *file = tname;

    if(!tname)
    {
        if(!tex) return NULL;
        if(tex->name[0]=='<') 
        {
            cmds = tex->name;
            file = strrchr(tex->name, '>');
            if(!file) { if(msg) conoutf(CON_ERROR, "could not load texture packages/%s", tex->name); return NULL; }
            file++;
        }
        else file = tex->name;
        
        static string pname;
        s_sprintf(pname)("packages/%s", file);
        file = path(pname);
    }
    else if(tname[0]=='<') 
    {
        cmds = tname;
        file = strrchr(tname, '>');
        if(!file) { if(msg) conoutf(CON_ERROR, "could not load texture %s", tname); return NULL; }
        file++;
    }

    if(cmds)
    {
        if(renderpath==R_FIXEDFUNCTION && !strncmp(cmds, "<noff>", 6)) return &stubsurface;
    }
    
    if(msg) show_out_of_renderloop_progress(0, file);

    SDL_Surface *s = IMG_Load(findfile(file, "rb"));
    if(!s) { if(msg) conoutf(CON_ERROR, "could not load texture %s", file); return NULL; }
    int bpp = s->format->BitsPerPixel;
    if(!texformat(bpp)) { SDL_FreeSurface(s); conoutf(CON_ERROR, "texture must be 8, 16, 24, or 32 bpp: %s", file); return NULL; }

    while(cmds)
    {
        const char *cmd = NULL, *end = NULL, *arg[3] = { NULL, NULL, NULL };
        cmd = &cmds[1];
        end = strchr(cmd, '>');
        if(!end) break;
        cmds = strchr(cmd, '<');
        size_t len = strcspn(cmd, ":,><");
        loopi(3)
        {
            arg[i] = strchr(i ? arg[i-1] : cmd, i ? ',' : ':');
            if(!arg[i] || arg[i] >= end) arg[i] = "";
            else arg[i]++;
        }
        if(!strncmp(cmd, "mad", len)) texmad(s, parsevec(arg[0]), parsevec(arg[1])); 
        else if(!strncmp(cmd, "ffcolor", len))
        {
            if(renderpath==R_FIXEDFUNCTION) texmad(s, parsevec(arg[0]), parsevec(arg[1]));
        }
        else if(!strncmp(cmd, "ffmask", len)) 
        {
            s = texffmask(s, atoi(arg[0]));
            if(s == &stubsurface) return s;
        }
        else if(!strncmp(cmd, "dup", len)) texdup(s, atoi(arg[0]), atoi(arg[1]));
        else if(!strncmp(cmd, "decal", len)) s = texdecal(s);
        else if(!strncmp(cmd, "offset", len)) s = texoffset(s, atoi(arg[0]), atoi(arg[1]));
        else if(!strncmp(cmd, "rotate", len)) s = texrotate(s, atoi(arg[0]), tex ? tex->type : 0);
        else if(!strncmp(cmd, "reorient", len)) s = texreorient(s, atoi(arg[0])>0, atoi(arg[1])>0, atoi(arg[2])>0, tex ? tex->type : TEX_DIFFUSE);
        else if(!strncmp(cmd, "compress", len)) 
        { 
            if(compress) *compress = true; 
            if(!hasTC)
            {
                int scale = atoi(arg[0]);
                if(scale > 1) s = scalesurface(s, s->w/scale, s->h/scale);
            }
        }
    }

    return s;
}

void loadalphamask(Texture *t)
{
    if(t->alphamask || t->bpp!=32) return;
    SDL_Surface *s = texturedata(t->name, NULL, false);
    if(!s || !s->format->Amask) { if(s) SDL_FreeSurface(s); return; }
    uint alpha = s->format->Amask;
    t->alphamask = new uchar[s->h * ((s->w+7)/8)];
    uchar *srcrow = (uchar *)s->pixels, *dst = t->alphamask-1;
    loop(y, s->h)
    {
        uint *src = (uint *)srcrow;
        loop(x, s->w)
        {
            int offset = x%8;
            if(!offset) *++dst = 0;
            if(*src & alpha) *dst |= 1<<offset;
            src++;
        }
        srcrow += s->pitch;
    }
    SDL_FreeSurface(s);
}

Texture *textureload(const char *name, int clamp, bool mipit, bool msg)
{
    string tname;
    s_strcpy(tname, name);
    Texture *t = textures.access(path(tname));
    if(t) return t;
    bool compress = false;
    SDL_Surface *s = texturedata(tname, NULL, msg, &compress); 
    return s ? newtexture(NULL, tname, s, clamp, mipit, false, false, compress) : notexture;
}

void settexture(const char *name, bool clamp)
{
    glBindTexture(GL_TEXTURE_2D, textureload(name, clamp, true, false)->id);
}

vector<Slot> slots;
Slot materialslots[MATF_VOLUME+1];

int curtexnum = 0, curmatslot = -1;

void texturereset()
{
    curtexnum = 0;
    slots.setsize(0);
}

COMMAND(texturereset, "");

void materialreset()
{
    loopi(MATF_VOLUME+1) materialslots[i].reset();
}

COMMAND(materialreset, "");

void texture(char *type, char *name, int *rot, int *xoffset, int *yoffset, float *scale)
{
    if(curtexnum<0 || curtexnum>=0x10000) return;
    struct { const char *name; int type; } types[] =
    {
        {"c", TEX_DIFFUSE},
        {"u", TEX_UNKNOWN},
        {"d", TEX_DECAL},
        {"n", TEX_NORMAL},
        {"g", TEX_GLOW},
        {"s", TEX_SPEC},
        {"z", TEX_DEPTH},
        {"e", TEX_ENVMAP}
    };
    int tnum = -1, matslot = findmaterial(type);
    loopi(sizeof(types)/sizeof(types[0])) if(!strcmp(types[i].name, type)) { tnum = i; break; }
    if(tnum<0) tnum = atoi(type);
    if(tnum==TEX_DIFFUSE)
    {
        if(matslot>=0) curmatslot = matslot;
        else { curmatslot = -1; curtexnum++; }
    }
    else if(curmatslot>=0) matslot=curmatslot;
    else if(!curtexnum) return;
    Slot &s = matslot>=0 ? materialslots[matslot] : (tnum!=TEX_DIFFUSE ? slots.last() : slots.add());
    s.loaded = false;
    s.texmask |= 1<<tnum;
    if(s.sts.length()>=8) conoutf(CON_WARN, "warning: too many textures in slot %d", curtexnum);
    Slot::Tex &st = s.sts.add();
    st.type = tnum;
    st.combined = -1;
    st.t = NULL;
    s_strcpy(st.name, name);
    path(st.name);
    if(tnum==TEX_DIFFUSE)
    {
        setslotshader(s);
        s.rotation = clamp(*rot, 0, 5);
        s.xoffset = max(*xoffset, 0);
        s.yoffset = max(*yoffset, 0);
        s.scale = *scale <= 0 ? 1 : *scale;
    }
}

COMMAND(texture, "ssiiif");

void autograss(char *name)
{
    Slot &s = slots.last();
    DELETEA(s.autograss);
    s_sprintfd(pname)("packages/%s", name);
    s.autograss = newstring(name[0] ? pname : "packages/textures/grass.png");
}
COMMAND(autograss, "s");

void texscroll(float *scrollS, float *scrollT)
{
    if(slots.empty()) return;
    Slot &s = slots.last();
    s.scrollS = *scrollS/1000.0f;
    s.scrollT = *scrollT/1000.0f;
}
COMMAND(texscroll, "ff");

void texoffset_(int *xoffset, int *yoffset)
{
    if(slots.empty()) return;
    Slot &s = slots.last();
    s.xoffset = max(*xoffset, 0);
    s.yoffset = max(*yoffset, 0);
}
COMMANDN(texoffset, texoffset_, "ii");

void texrotate_(int *rot)
{
    if(slots.empty()) return;
    Slot &s = slots.last();
    s.rotation = clamp(*rot, 0, 5);
}
COMMANDN(texrotate, texrotate_, "i");

void texscale(float *scale)
{
    if(slots.empty()) return;
    Slot &s = slots.last();
    s.scale = *scale <= 0 ? 1 : *scale;
}
COMMAND(texscale, "f");

static int findtextype(Slot &s, int type, int last = -1)
{
    for(int i = last+1; i<s.sts.length(); i++) if((type&(1<<s.sts[i].type)) && s.sts[i].combined<0) return i;
    return -1;
}

#define writetex(t, body) \
    { \
        uchar *dst = (uchar *)t->pixels; \
        loop(y, t->h) loop(x, t->w) \
        { \
            body; \
            dst += t->format->BytesPerPixel; \
        } \
    }

#define sourcetex(s) uchar *src = &((uchar *)s->pixels)[s->format->BytesPerPixel*(y*s->w + x)];

static void addbump(SDL_Surface *c, SDL_Surface *n)
{
    writetex(c,
        sourcetex(n);
        loopk(3) dst[k] = int(dst[k])*(int(src[2])*2-255)/255;
    );
}

static void addglow(SDL_Surface *c, SDL_Surface *g, const vec &glowcolor)
{
    writetex(c,
        sourcetex(g);
        loopk(3) dst[k] = clamp(int(dst[k]) + int(src[k]*glowcolor[k]), 0, 255);
    );
}

static void blenddecal(SDL_Surface *c, SDL_Surface *d)
{
    writetex(c,
        sourcetex(d);
        uchar a = src[3];
        loopk(3) dst[k] = (int(src[k])*int(a) + int(dst[k])*int(255-a))/255;
    );
}

static void mergespec(SDL_Surface *c, SDL_Surface *s)
{
    writetex(c,
        sourcetex(s);
        dst[3] = (int(src[0]) + int(src[1]) + int(src[2]))/3;
    );
}

static void mergedepth(SDL_Surface *c, SDL_Surface *z)
{
    writetex(c,
        sourcetex(z);
        dst[3] = src[0];
    );
}

static void addname(vector<char> &key, Slot &slot, Slot::Tex &t, bool combined = false, const char *prefix = NULL)
{
    if(combined) key.add('&');
    if(prefix) { while(*prefix) key.add(*prefix++); }
    s_sprintfd(tname)("packages/%s", t.name);
    for(const char *s = path(tname); *s; key.add(*s++));
}

static void texcombine(Slot &s, int index, Slot::Tex &t, bool forceload = false)
{
    if(renderpath==R_FIXEDFUNCTION && t.type!=TEX_DIFFUSE && t.type!=TEX_GLOW && !forceload) { t.t = notexture; return; }
    vector<char> key; 
    addname(key, s, t);
    switch(t.type)
    {
        case TEX_DIFFUSE:
            if(renderpath==R_FIXEDFUNCTION)
            {
                for(int i = -1; (i = findtextype(s, (1<<TEX_DECAL)|(1<<TEX_NORMAL), i))>=0;)
                {
                    s.sts[i].combined = index;
                    addname(key, s, s.sts[i], true);
                }
                break;
            } // fall through to shader case

        case TEX_NORMAL:
        {
            if(renderpath==R_FIXEDFUNCTION) break;
            int i = findtextype(s, t.type==TEX_DIFFUSE ? (1<<TEX_SPEC) : (1<<TEX_DEPTH));
            if(i<0) break;
            s.sts[i].combined = index;
            addname(key, s, s.sts[i], true);
            break;
        }
    }
    key.add('\0');
    t.t = textures.access(key.getbuf());
    if(t.t) return;
    bool compress = false;
    SDL_Surface *ts = texturedata(NULL, &t, true, &compress);
    if(!ts) { t.t = notexture; return; }
    switch(t.type)
    {
        case TEX_DIFFUSE:
            if(renderpath==R_FIXEDFUNCTION)
            {
                loopv(s.sts)
                {
                    Slot::Tex &b = s.sts[i];
                    if(b.combined!=index) continue;
                    SDL_Surface *bs = texturedata(NULL, &b);
                    if(!bs) continue;
                    if(bs->w!=ts->w || bs->h!=ts->h) bs = scalesurface(bs, ts->w, ts->h);
                    switch(b.type)
                    {
                        case TEX_DECAL: if(bs->format->BitsPerPixel==32) blenddecal(ts, bs); break;
                        case TEX_NORMAL: addbump(ts, bs); break;
                    }
                    SDL_FreeSurface(bs);
                }
                break;
            } // fall through to shader case

        case TEX_NORMAL:
            loopv(s.sts)
            {
                Slot::Tex &a = s.sts[i];
                if(a.combined!=index) continue;
                SDL_Surface *as = texturedata(NULL, &a);
                if(!as) break;
                if(ts->format->BitsPerPixel!=32) ts = creatergbasurface(ts);
                if(as->w!=ts->w || as->h!=ts->h) as = scalesurface(as, ts->w, ts->h);
                switch(a.type)
                {
                    case TEX_SPEC: mergespec(ts, as); break;
                    case TEX_DEPTH: mergedepth(ts, as); break;
                }
                SDL_FreeSurface(as);
                break; // only one combination
            }
            break;
    }
    t.t = newtexture(NULL, key.getbuf(), ts, 0, true, true, true, compress);
}

Slot dummyslot;

Slot &lookuptexture(int slot, bool load)
{
    Slot &s = slot<0 && slot>=-MATF_VOLUME ? materialslots[-slot] : (slots.inrange(slot) ? slots[slot] : (slots.empty() ? dummyslot : slots[0]));
    if(s.loaded || !load) return s;
    loopv(s.sts)
    {
        Slot::Tex &t = s.sts[i];
        if(t.combined>=0) continue;
        switch(t.type)
        {
            case TEX_ENVMAP:
                if(hasCM && (renderpath!=R_FIXEDFUNCTION || (slot<0 && slot>=-MATF_VOLUME))) t.t = cubemapload(t.name);
                break;

            default:
                texcombine(s, i, t, slot<0 && slot>=-MATF_VOLUME);
                break;
        }
    }
    s.loaded = true;
    return s;
}

Shader *lookupshader(int slot) { return slot<0 && slot>=-MATF_VOLUME ? materialslots[-slot].shader : (slots.inrange(slot) ? slots[slot].shader : defaultshader); }

Texture *loadthumbnail(Slot &slot)
{
    if(slot.thumbnail) return slot.thumbnail;
    vector<char> name;
    addname(name, slot, slot.sts[0], false, "<thumbnail>");
    int glow = -1;
    if(slot.texmask&(1<<TEX_GLOW)) 
    { 
        loopvj(slot.sts) if(slot.sts[j].type==TEX_GLOW) { glow = j; break; } 
        if(glow >= 0) 
        {
            s_sprintfd(prefix)("<mad:%.2f/%.2f/%.2f>", slot.glowcolor.x, slot.glowcolor.y, slot.glowcolor.z); 
            addname(name, slot, slot.sts[glow], true, prefix);
        }
    }
    name.add('\0');
    Texture *t = textures.access(path(name.getbuf()));
    if(t) slot.thumbnail = t;
    else
    {
        SDL_Surface *s = texturedata(NULL, &slot.sts[0], false), *g = glow >= 0 ? texturedata(NULL, &slot.sts[glow], false) : NULL;
        if(!s) slot.thumbnail = notexture;
        else
        {
            int xs = s->w, ys = s->h;
            if(s->w > 64 || s->h > 64) s = scalesurface(s, min(s->w, 64), min(s->h, 64));
            if(g)
            {
                if(g->w != s->w || g->h != s->h) g = scalesurface(g, s->w, s->h);
                addglow(s, g, slot.glowcolor);
            }
            t = newtexture(NULL, name.getbuf(), s, 0, false, false, true);
            t->xs = xs;
            t->ys = ys;
            slot.thumbnail = t;
        }
        if(g) SDL_FreeSurface(g);
    }
    return t;
}

// environment mapped reflections

cubemapside cubemapsides[6] =
{
    { GL_TEXTURE_CUBE_MAP_NEGATIVE_X_ARB, "lf", true,  true,  true  },
    { GL_TEXTURE_CUBE_MAP_POSITIVE_X_ARB, "rt", false, false, true  },
    { GL_TEXTURE_CUBE_MAP_NEGATIVE_Y_ARB, "ft", true,  false, false },
    { GL_TEXTURE_CUBE_MAP_POSITIVE_Y_ARB, "bk", false, true,  false },
    { GL_TEXTURE_CUBE_MAP_NEGATIVE_Z_ARB, "dn", false, false, true  },
    { GL_TEXTURE_CUBE_MAP_POSITIVE_Z_ARB, "up", false, false, true  },
};

GLuint cubemapfromsky(int size)
{
    extern Texture *sky[6];
    if(!sky[0]) return 0;
    
    int tsize = 0, cmw, cmh;
    GLint tw[6], th[6]; 
    loopi(6)
    {
        glBindTexture(GL_TEXTURE_2D, sky[i]->id);
        glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH, &tw[i]);
        glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT, &th[i]);
        tsize = max(tsize, (int)max(tw[i], th[i]));
    }
    cmw = cmh = min(tsize, size);
    resizetexture(cmw, cmh, true, GL_RGB5, GL_TEXTURE_CUBE_MAP_ARB);
    
    GLuint tex;
    glGenTextures(1, &tex);
    int bufsize = 3*max(cmw, tsize)*max(cmh, tsize);
    uchar *pixels = new uchar[2*bufsize],
          *rpixels = &pixels[bufsize];
    loopi(6)
    {
        glBindTexture(GL_TEXTURE_2D, sky[i]->id);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGB, GL_UNSIGNED_BYTE, pixels);
        if(tw[i]!=cmw || th[i]!=cmh) gluScaleImage(GL_RGB, tw[i], th[i], GL_UNSIGNED_BYTE, pixels, cmw, cmh, GL_UNSIGNED_BYTE, pixels);
        cubemapside &side = cubemapsides[i];
        reorienttexture(pixels, cmw, cmh, 3, rpixels, side.flipx, side.flipy, side.swapxy); 
        createtexture(!i ? tex : 0, cmw, cmh, rpixels, 3, true, GL_RGB5, side.target);
    }
    delete[] pixels;
    return tex;
}   

Texture *cubemaploadwildcard(Texture *t, const char *name, bool mipit, bool msg)
{
    if(!hasCM) return NULL;
    string tname;
    if(!name) s_strcpy(tname, t->name);
    else
    {
        s_strcpy(tname, name);
        t = textures.access(path(tname));
        if(t) return t;
    }
    char *wildcard = strchr(tname, '*');
    SDL_Surface *surface[6];
    string sname;
    if(!wildcard) s_strcpy(sname, tname);
    GLenum format = 0;
    int tsize = 0;
    bool compress = false;
    loopi(6)
    {
        if(wildcard)
        {
            s_strncpy(sname, tname, wildcard-tname+1);
            s_strcat(sname, cubemapsides[i].name);
            s_strcat(sname, wildcard+1);
        }
        surface[i] = texturedata(sname, NULL, msg, &compress);
        if(!surface[i])
        {
            loopj(i) SDL_FreeSurface(surface[j]);
            return NULL;
        }
        if(!format) format = texformat(surface[i]->format->BitsPerPixel);
        else if(texformat(surface[i]->format->BitsPerPixel)!=format)
        {
            if(surface[i] && msg) conoutf(CON_ERROR, "cubemap texture %s doesn't match other sides' format", sname);
            loopj(i) SDL_FreeSurface(surface[j]);
            return NULL;
        }
        tsize = max(tsize, max(surface[i]->w, surface[i]->h));
    }
    if(name)
    {
        char *key = newstring(tname);
        t = &textures[key];
        t->name = key;
    }
    t->bpp = surface[0]->format->BitsPerPixel;
    t->mipmap = mipit;
    t->clamp = 3;
    t->type = Texture::CUBEMAP;
    t->w = t->xs = tsize;
    t->h = t->ys = tsize;
    resizetexture(t->w, t->h, mipit, format, GL_TEXTURE_CUBE_MAP_ARB);
    glGenTextures(1, &t->id);
    uchar *pixels = NULL;
    loopi(6)
    {
        cubemapside &side = cubemapsides[i];
        SDL_Surface *s = texreorient(surface[i], side.flipx, side.flipy, side.swapxy);
        if(s->w != t->w || s->h != t->h)
        {
            if(!pixels) pixels = new uchar[formatsize(format)*t->w*t->h];
            gluScaleImage(format, s->w, s->h, GL_UNSIGNED_BYTE, s->pixels, t->w, t->h, GL_UNSIGNED_BYTE, pixels);
        }
        createtexture(!i ? t->id : 0, t->w, t->h, s->w != t->w || s->h != t->h ? pixels : s->pixels, 3, mipit, format, side.target, compress);
        SDL_FreeSurface(s);
    }
    if(pixels) delete[] pixels;
    return t;
}

Texture *cubemapload(const char *name, bool mipit, bool msg)
{
    if(!hasCM) return NULL;
    string pname;
    s_strcpy(pname, makerelpath("packages", name));
    path(pname);
    Texture *t = NULL;
    if(!strchr(pname, '*'))
    {
        s_sprintfd(jpgname)("%s_*.jpg", pname);
        t = cubemaploadwildcard(NULL, jpgname, mipit, false);
        if(!t)
        {
            s_sprintfd(pngname)("%s_*.png", pname);
            t = cubemaploadwildcard(NULL, pngname, mipit, false);
            if(!t && msg) conoutf(CON_ERROR, "could not load envmap %s", name);
        }
    }
    else t = cubemaploadwildcard(NULL, pname, mipit, msg);
    return t;
}

VARFP(envmapsize, 4, 7, 9, setupmaterials());
VAR(envmapradius, 0, 128, 10000);

struct envmap
{
    int radius, size;
    vec o;
    GLuint tex;
};  

static vector<envmap> envmaps;
static GLuint skyenvmap = 0;

void clearenvmaps()
{
    if(skyenvmap)
    {
        glDeleteTextures(1, &skyenvmap);
        skyenvmap = 0;
    }
    loopv(envmaps) glDeleteTextures(1, &envmaps[i].tex);
    envmaps.setsize(0);
}

VAR(aaenvmap, 0, 2, 4);

GLuint genenvmap(const vec &o, int envmapsize)
{
    int rendersize = 1<<(envmapsize+aaenvmap), sizelimit = min(hwcubetexsize, min(screen->w, screen->h));
    if(maxtexsize) sizelimit = min(sizelimit, maxtexsize);
    while(rendersize > sizelimit) rendersize /= 2;
    int texsize = min(rendersize, 1<<envmapsize);
    if(!aaenvmap) rendersize = texsize;
    GLuint tex;
    glGenTextures(1, &tex);
    glViewport(0, 0, rendersize, rendersize);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    float yaw = 0, pitch = 0;
    uchar *pixels = new uchar[3*rendersize*rendersize];
    loopi(6)
    {
        const cubemapside &side = cubemapsides[i];
        switch(side.target)
        {
            case GL_TEXTURE_CUBE_MAP_NEGATIVE_X_ARB: // lf
                yaw = 270; pitch = 0; break;
            case GL_TEXTURE_CUBE_MAP_POSITIVE_X_ARB: // rt
                yaw = 90; pitch = 0; break;
            case GL_TEXTURE_CUBE_MAP_NEGATIVE_Y_ARB: // ft
                yaw = 0; pitch = 0; break;
            case GL_TEXTURE_CUBE_MAP_POSITIVE_Y_ARB: // bk
                yaw = 180; pitch = 0; break;
            case GL_TEXTURE_CUBE_MAP_NEGATIVE_Z_ARB: // dn
                yaw = 90; pitch = -90; break;
            case GL_TEXTURE_CUBE_MAP_POSITIVE_Z_ARB: // up
                yaw = 90; pitch = 90; break;
        }
        glFrontFace((side.flipx==side.flipy)!=side.swapxy ? GL_CCW : GL_CW);
        drawcubemap(rendersize, o, yaw, pitch, side);
        glReadPixels(0, 0, rendersize, rendersize, GL_RGB, GL_UNSIGNED_BYTE, pixels);
        if(texsize<rendersize) gluScaleImage(GL_RGB, rendersize, rendersize, GL_UNSIGNED_BYTE, pixels, texsize, texsize, GL_UNSIGNED_BYTE, pixels);
        createtexture(tex, texsize, texsize, pixels, 3, true, GL_RGB5, side.target);
    }
    glFrontFace(GL_CCW);
    delete[] pixels;
    glViewport(0, 0, screen->w, screen->h);
    clientkeepalive();
    return tex;
}

void initenvmaps()
{
    if(!hasCM) return;
    clearenvmaps();
    skyenvmap = cubemapfromsky(1<<envmapsize);
    const vector<extentity *> &ents = et->getents();
    loopv(ents)
    {
        const extentity &ent = *ents[i];
        if(ent.type != ET_ENVMAP) continue;
        envmap &em = envmaps.add();
        em.radius = ent.attr1 ? max(0, min(10000, int(ent.attr1))) : envmapradius;
        em.size = ent.attr2 ? max(4, min(9, int(ent.attr2))) : 0;
        em.o = ent.o;
        em.tex = 0;
    }
}

void genenvmaps()
{
    if(envmaps.empty()) return;
    show_out_of_renderloop_progress(0, "generating environment maps...");
    loopv(envmaps)
    {
        envmap &em = envmaps[i];
        em.tex = genenvmap(em.o, em.size ? em.size : envmapsize);
    }
}

ushort closestenvmap(const vec &o)
{
    ushort minemid = EMID_SKY;
    float mindist = 1e16f;
    loopv(envmaps)
    {
        envmap &em = envmaps[i];
        float dist = em.o.dist(o);
        if(dist < em.radius && dist < mindist)
        {
            minemid = EMID_RESERVED + i;
            mindist = dist;
        }
    }
    return minemid;
}

ushort closestenvmap(int orient, int x, int y, int z, int size)
{
    vec loc(x, y, z);
    int dim = dimension(orient);
    if(dimcoord(orient)) loc[dim] += size;
    loc[R[dim]] += size/2;
    loc[C[dim]] += size/2;
    return closestenvmap(loc);
}

GLuint lookupenvmap(Slot &slot)
{
    loopv(slot.sts) if(slot.sts[i].type==TEX_ENVMAP && slot.sts[i].t) return slot.sts[i].t->id;
    return skyenvmap;
}

GLuint lookupenvmap(ushort emid)
{
    if(emid==EMID_SKY || emid==EMID_CUSTOM) return skyenvmap;
    if(emid==EMID_NONE || !envmaps.inrange(emid-EMID_RESERVED)) return 0;
    GLuint tex = envmaps[emid-EMID_RESERVED].tex;
    return tex ? tex : skyenvmap;
}

void writetgaheader(FILE *f, SDL_Surface *s, int bits)
{
    fwrite("\0\0\x02\0\0\0\0\0\0\0\0\0", 1, 12, f);
    ushort dim[] = { s->w, s->h };
    endianswap(dim, sizeof(ushort), 2);
    fwrite(dim, sizeof(short), 2, f);
    fputc(bits, f);
    fputc(0, f);
}

void flipnormalmapy(char *destfile, char *normalfile)           // RGB (jpg/png) -> BGR (tga)
{
    SDL_Surface *ns = IMG_Load(findfile(path(normalfile), "rb"));
    if(!ns) return;
    FILE *f = openfile(path(destfile), "wb");
    if(f)
    {
        writetgaheader(f, ns, 24);
        for(int y = ns->h-1; y>=0; y--) loop(x, ns->w)
        {
            uchar *nd = (uchar *)ns->pixels+(x+y*ns->w)*3;
            fputc(nd[2], f);
            fputc(255-nd[1], f);
            fputc(nd[0], f);
        }
        fclose(f);
    }
    if(ns) SDL_FreeSurface(ns);
}

void mergenormalmaps(char *heightfile, char *normalfile)    // BGR (tga) -> BGR (tga) (SDL loads TGA as BGR!)
{
    SDL_Surface *hs = IMG_Load(findfile(path(heightfile), "rb"));
    SDL_Surface *ns = IMG_Load(findfile(path(normalfile), "rb"));
    if(hs && ns)
    {
        uchar def_n[] = { 255, 128, 128 };
        FILE *f = openfile(normalfile, "wb");
        if(f)
        {
            writetgaheader(f, ns, 24); 
            for(int y = ns->h-1; y>=0; y--) loop(x, ns->w)
            {
                int off = (x+y*ns->w)*3;
                uchar *hd = hs ? (uchar *)hs->pixels+off : def_n;
                uchar *nd = ns ? (uchar *)ns->pixels+off : def_n;
                #define S(x) x/255.0f*2-1 
                vec n(S(nd[0]), S(nd[1]), S(nd[2]));
                vec h(S(hd[0]), S(hd[1]), S(hd[2]));
                n.mul(2).add(h).normalize().add(1).div(2).mul(255);
                uchar o[3] = { (uchar)n.x, (uchar)n.y, (uchar)n.z };
                fwrite(o, 3, 1, f);
                #undef S
            }
            fclose(f);
        }
    }
    if(hs) SDL_FreeSurface(hs);
    if(ns) SDL_FreeSurface(ns);
}

COMMAND(flipnormalmapy, "ss");
COMMAND(mergenormalmaps, "sss");

void cleanuptextures()
{
    clearenvmaps();
    loopv(slots) slots[i].cleanup();
    loopi(MATF_VOLUME+1) materialslots[i].cleanup();
    vector<Texture *> transient;
    enumerate(textures, Texture, tex,
        DELETEA(tex.alphamask);
        if(tex.id) { glDeleteTextures(1, &tex.id); tex.id = 0; }
        if(tex.type==Texture::TRANSIENT) transient.add(&tex);
    );
    loopv(transient) textures.remove(transient[i]->name);
}

bool reloadtexture(const char *name)
{
    Texture *t = textures.access(path(name, true));
    if(t) return reloadtexture(*t);
    return false;
}

bool reloadtexture(Texture &tex)
{
    if(tex.id) return true;
    switch(tex.type)
    {
        case Texture::STUB:
        case Texture::IMAGE:
        {
            bool compress = false;
            SDL_Surface *s = texturedata(tex.name, NULL, true, &compress);
            if(!s || !newtexture(&tex, NULL, s, tex.clamp, tex.mipmap, false, false, compress)) return false;
            break;
        }

        case Texture::CUBEMAP:
            if(!cubemaploadwildcard(&tex, NULL, tex.mipmap, true)) return false;
            break;
    }    
    return true;
}

void reloadtex(char *name)
{
    Texture *t = textures.access(path(name, true));
    if(!t) { conoutf("texture %s is not loaded", name); return; }
    if(t->type==Texture::TRANSIENT) { conoutf("can't reload transient texture %s", name); return; }
    DELETEA(t->alphamask);
    Texture oldtex = *t;
    t->id = 0;
    if(!reloadtexture(*t))
    {
        if(t->id) glDeleteTextures(1, &t->id);
        *t = oldtex;
        conoutf("failed to reload texture %s", name);
    }
}

COMMAND(reloadtex, "s");

void reloadtextures()
{
    enumerate(textures, Texture, tex, reloadtexture(tex)); 
}

