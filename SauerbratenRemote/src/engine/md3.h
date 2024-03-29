struct md3;

md3 *loadingmd3 = NULL;

string md3dir;

struct md3frame
{
    vec bbmin, bbmax, origin;
    float radius;
    uchar name[16];
};

struct md3tag
{
    char name[64];
    vec pos;
    float rotation[3][3];
};

struct md3vertex
{
    short vertex[3];
    short normal;
};

struct md3triangle
{
    int vertexindices[3];
};

struct md3header
{
    char id[4];
    int version;
    char name[64];
    int flags;
    int numframes, numtags, nummeshes, numskins;
    int ofs_frames, ofs_tags, ofs_meshes, ofs_eof; // offsets
};

struct md3meshheader
{
    char id[4];
    char name[64];
    int flags;
    int numframes, numshaders, numvertices, numtriangles;
    int ofs_triangles, ofs_shaders, ofs_uv, ofs_vertices, meshsize; // offsets
};

struct md3 : vertmodel
{
    md3(const char *name) : vertmodel(name) {}

    int type() const { return MDL_MD3; }

    struct md3meshgroup : vertmeshgroup
    {
        bool load(char *path)
        {
            FILE *f = openfile(path, "rb");
            if(!f) return false;
            md3header header;
            fread(&header, sizeof(md3header), 1, f);
            endianswap(&header.version, sizeof(int), 1);
            endianswap(&header.flags, sizeof(int), 9);
            if(strncmp(header.id, "IDP3", 4) != 0 || header.version != 15) // header check
            { 
                fclose(f);
                conoutf("md3: corrupted header"); 
                return false; 
            }

            name = newstring(path);

            numframes = header.numframes;
            numtags = header.numtags;        
            if(numtags)
            {
                tags = new tag[numframes*numtags];
                fseek(f, header.ofs_tags, SEEK_SET);
                md3tag tag;
                
                loopi(header.numframes*header.numtags) 
                {
                    fread(&tag, sizeof(md3tag), 1, f);
                    endianswap(&tag.pos, sizeof(float), 12);
                    if(tag.name[0] && i<header.numtags) tags[i].name = newstring(tag.name);
                    matrix3x4 &m = tags[i].transform;
                    tag.pos.y *= -1;
                    // undo the -y
                    loopj(3) tag.rotation[1][j] *= -1;
                    // then restore it
                    loopj(3) tag.rotation[j][1] *= -1;
                    m.X.w = tag.pos.x;
                    m.Y.w = tag.pos.y;
                    m.Z.w = tag.pos.z;
                    loopj(3) 
                    {
                        m.X[j] = tag.rotation[j][0];
                        m.Y[j] = tag.rotation[j][1];
                        m.Z[j] = tag.rotation[j][2];
                    }
#if 0
                    tags[i].pos = vec(tag.pos.x, -tag.pos.y, tag.pos.z);
                    memcpy(tags[i].transform, tag.rotation, sizeof(tag.rotation));
                    // undo the -y
                    loopj(3) tags[i].transform[1][j] *= -1;
                    // then restore it
                    loopj(3) tags[i].transform[j][1] *= -1;
#endif
                }
            }

            int mesh_offset = header.ofs_meshes;
            loopi(header.nummeshes)
            {
                vertmesh &m = *new vertmesh;
                m.group = this;
                meshes.add(&m);

                md3meshheader mheader;
                fseek(f, mesh_offset, SEEK_SET);
                fread(&mheader, sizeof(md3meshheader), 1, f);
                endianswap(&mheader.flags, sizeof(int), 10); 

                m.name = newstring(mheader.name);
               
                m.numtris = mheader.numtriangles; 
                m.tris = new tri[m.numtris];
                fseek(f, mesh_offset + mheader.ofs_triangles, SEEK_SET);
                loopj(m.numtris)
                {
                    md3triangle tri;
                    fread(&tri, sizeof(md3triangle), 1, f); // read the triangles
                    endianswap(&tri, sizeof(int), 3);
                    loopk(3) m.tris[j].vert[k] = (ushort)tri.vertexindices[k];
                }

                m.numverts = mheader.numvertices;
                m.tcverts = new tcvert[m.numverts];
                fseek(f, mesh_offset + mheader.ofs_uv , SEEK_SET); 
                fread(m.tcverts, 2*sizeof(float), m.numverts, f); // read the UV data
                endianswap(m.tcverts, sizeof(float), 2*m.numverts);
                
                m.verts = new vert[numframes*m.numverts];
                fseek(f, mesh_offset + mheader.ofs_vertices, SEEK_SET); 
                loopj(numframes*m.numverts)
                {
                    md3vertex v;
                    fread(&v, sizeof(md3vertex), 1, f); // read the vertices
                    endianswap(&v, sizeof(short), 4);

                    m.verts[j].pos.x = v.vertex[0]/64.0f;
                    m.verts[j].pos.y = -v.vertex[1]/64.0f;
                    m.verts[j].pos.z = v.vertex[2]/64.0f;

                    float lng = (v.normal&0xFF)*PI2/255.0f; // decode vertex normals
                    float lat = ((v.normal>>8)&0xFF)*PI2/255.0f;
                    m.verts[j].norm.x = cosf(lat)*sinf(lng);
                    m.verts[j].norm.y = -sinf(lat)*sinf(lng);
                    m.verts[j].norm.z = cosf(lng);
                }

                mesh_offset += mheader.meshsize;
            }

            fclose(f);
            return true;
        }
    };
    
    void extendbb(int frame, vec &center, vec &radius, modelattach &a)
    {
        vec acenter, aradius;
        a.m->boundbox(frame, acenter, aradius);
        float margin = 2*max(aradius.x, max(aradius.y, aradius.z));
        radius.x += margin;
        radius.y += margin;
    }   

    meshgroup *loadmeshes(char *name, va_list args)
    {
        md3meshgroup *group = new md3meshgroup;
        if(!group->load(name)) { delete group; return NULL; }
        return group;
    }

    bool loaddefaultparts()
    {
        const char *pname = parentdir(loadname);
        part &mdl = *new part;
        parts.add(&mdl);
        mdl.model = this;
        mdl.index = 0;
#ifdef TC
		pname = "packages/plexus/models";
        s_sprintfd(name1)("%s/%s/tris.md3", pname, loadname);
        mdl.meshes = sharemeshes(path(name1));
        if(!mdl.meshes)
        {
			pname = parentdir(loadname);
#endif
        s_sprintfd(name1)("packages/models/%s/tris.md3", loadname);
        mdl.meshes = sharemeshes(path(name1));
        if(!mdl.meshes)
        {
            s_sprintfd(name2)("packages/models/%s/tris.md3", pname);    // try md3 in parent folder (vert sharing)
            mdl.meshes = sharemeshes(path(name2));
            if(!mdl.meshes) return false;
        }
#ifdef TC
		}
#endif
        Texture *tex, *masks;
        loadskin(loadname, pname, tex, masks);
        mdl.initskins(tex, masks);
        if(tex==notexture) conoutf("could not load model skin for %s", name1);
        return true;
    }

    bool load()
    {
        if(loaded) return true;
#ifdef TC
		const char *pname = "packages/plexus/models";
        s_sprintf(md3dir)("%s/%s", pname, loadname);
        s_sprintfd(cfgname)("%s/%s/md3.cfg", pname, loadname);
		if (!fileexists(md3dir, "r")) {
			s_sprintf(md3dir)("packages/models/%s", loadname);
			s_sprintf(cfgname)("packages/models/%s/md3.cfg", loadname);
		}
#else
        s_sprintf(md3dir)("packages/models/%s", loadname);
        s_sprintfd(cfgname)("packages/models/%s/md3.cfg", loadname);
#endif
        loadingmd3 = this;
        persistidents = false;
        if(execfile(cfgname) && parts.length()) // configured md3, will call the md3* commands below
        {
            persistidents = true;
            loadingmd3 = NULL;
            loopv(parts) if(!parts[i]->meshes) return false;
        }
        else // md3 without configuration, try default tris and skin
        {
            persistidents = true;
            loadingmd3 = NULL;
            if(!loaddefaultparts()) return false;
        }
        loopv(parts) parts[i]->meshes = parts[i]->meshes->scaleverts(scale/4.0f, i ? vec(0, 0, 0) : vec(translate.x, -translate.y, translate.z));
        return loaded = true;
    }
};

void md3load(char *model)
{   
    if(!loadingmd3) { conoutf("not loading an md3"); return; }
    s_sprintfd(filename)("%s/%s", md3dir, model);
    md3::part &mdl = *new md3::part;
    loadingmd3->parts.add(&mdl);
    mdl.model = loadingmd3;
    mdl.index = loadingmd3->parts.length()-1;
    if(mdl.index) mdl.pitchscale = mdl.pitchoffset = mdl.pitchmin = mdl.pitchmax = 0;
    mdl.meshes = loadingmd3->sharemeshes(path(filename));
    if(!mdl.meshes) conoutf("could not load %s", filename); // ignore failure
    else mdl.initskins();
}  

void md3pitch(float *pitchscale, float *pitchoffset, float *pitchmin, float *pitchmax)
{
    if(!loadingmd3 || loadingmd3->parts.empty()) { conoutf("not loading an md3"); return; }
    md3::part &mdl = *loadingmd3->parts.last();

    mdl.pitchscale = *pitchscale;
    mdl.pitchoffset = *pitchoffset;
    if(*pitchmin || *pitchmax)
    {
        mdl.pitchmin = *pitchmin;
        mdl.pitchmax = *pitchmax;
    }
    else
    {
        mdl.pitchmin = -360*mdl.pitchscale;
        mdl.pitchmax = 360*mdl.pitchscale;
    }
}

#define loopmd3meshes(meshname, m, body) \
    if(!loadingmd3 || loadingmd3->parts.empty()) { conoutf("not loading an md3"); return; } \
    md3::part &mdl = *loadingmd3->parts.last(); \
    if(!mdl.meshes) return; \
    loopv(mdl.meshes->meshes) \
    { \
        md3::vertmesh &m = *(md3::vertmesh *)mdl.meshes->meshes[i]; \
        if(!strcmp(meshname, "*") || !strcmp(m.name, meshname)) \
        { \
            body; \
        } \
    }

#define loopmd3skins(meshname, s, body) loopmd3meshes(meshname, m, { md3::skin &s = mdl.skins[i]; body; })

void md3skin(char *meshname, char *tex, char *masks, float *envmapmax, float *envmapmin)
{    
    loopmd3skins(meshname, s,
        s.tex = textureload(makerelpath(md3dir, tex), 0, true, false);
        if(*masks)
        {
            s.masks = textureload(makerelpath(md3dir, masks, "<ffmask:25>"), 0, true, false);
            s.envmapmax = *envmapmax;
            s.envmapmin = *envmapmin;
        }
    );
}   

void md3spec(char *meshname, int *percent)
{
    float spec = 1.0f;
    if(*percent>0) spec = *percent/100.0f;
    else if(*percent<0) spec = 0.0f;
    loopmd3skins(meshname, s, s.spec = spec);
}

void md3ambient(char *meshname, int *percent)
{
    float ambient = 0.3f;
    if(*percent>0) ambient = *percent/100.0f;
    else if(*percent<0) ambient = 0.0f;
    loopmd3skins(meshname, s, s.ambient = ambient);
}

void md3glow(char *meshname, int *percent)
{
    float glow = 3.0f;
    if(*percent>0) glow = *percent/100.0f;
    else if(*percent<0) glow = 0.0f;
    loopmd3skins(meshname, s, s.glow = glow);
}

void md3glare(char *meshname, float *specglare, float *glowglare)
{
    loopmd3skins(meshname, s, { s.specglare = *specglare; s.glowglare = *glowglare; });
}

void md3alphatest(char *meshname, float *cutoff)
{
    loopmd3skins(meshname, s, s.alphatest = max(0.0f, min(1.0f, *cutoff)));
}

void md3alphablend(char *meshname, int *blend)
{
    loopmd3skins(meshname, s, s.alphablend = *blend!=0);
}

void md3envmap(char *meshname, char *envmap)
{
    Texture *tex = cubemapload(envmap);
    loopmd3skins(meshname, s, s.envmap = tex);
}

void md3bumpmap(char *meshname, char *normalmap, char *skin)
{
    Texture *normalmaptex = NULL, *skintex = NULL;
    normalmaptex = textureload(makerelpath(md3dir, normalmap, "<noff>"), 0, true, false);
    if(skin[0]) skintex = textureload(makerelpath(md3dir, skin, "<noff>"), 0, true, false);
    loopmd3skins(meshname, s, { s.unlittex = skintex; s.normalmap = normalmaptex; m.calctangents(); });
}

void md3translucent(char *meshname, float *translucency)
{
    loopmd3skins(meshname, s, s.translucency = *translucency);
}

void md3fullbright(char *meshname, float *fullbright)
{
    loopmd3skins(meshname, s, s.fullbright = *fullbright);
}

void md3shader(char *meshname, char *shader)
{
    loopmd3skins(meshname, s, s.shader = lookupshaderbyname(shader));
}

void md3scroll(char *meshname, float *scrollu, float *scrollv)
{
    loopmd3skins(meshname, s, { s.scrollu = *scrollu; s.scrollv = *scrollv; });
}

void md3anim(char *anim, int *frame, int *range, float *speed, int *priority)
{
    if(!loadingmd3 || loadingmd3->parts.empty()) { conoutf("not loading an md3"); return; }
    vector<int> anims;
    findanims(anim, anims);
    if(anims.empty()) conoutf("could not find animation %s", anim);
    else loopv(anims)
    {
        loadingmd3->parts.last()->setanim(0, anims[i], *frame, *range, *speed, *priority);
    }
}

void md3link(int *parent, int *child, char *tagname)
{
    if(!loadingmd3) { conoutf("not loading an md3"); return; }
    if(!loadingmd3->parts.inrange(*parent) || !loadingmd3->parts.inrange(*child)) { conoutf("no models loaded to link"); return; }
    if(!loadingmd3->parts[*parent]->link(loadingmd3->parts[*child], tagname)) conoutf("could not link model %s", loadingmd3->loadname);
}

void md3noclip(char *meshname, int *noclip)
{
    loopmd3meshes(meshname, m, m.noclip = *noclip!=0);
}

COMMAND(md3load, "s");
COMMAND(md3pitch, "ffff");
COMMAND(md3skin, "sssff");
COMMAND(md3spec, "si");
COMMAND(md3ambient, "si");
COMMAND(md3glow, "si");
COMMAND(md3glare, "sff");
COMMAND(md3alphatest, "sf");
COMMAND(md3alphablend, "si");
COMMAND(md3envmap, "ss");
COMMAND(md3bumpmap, "sss");
COMMAND(md3translucent, "sf");
COMMAND(md3fullbright, "sf");
COMMAND(md3shader, "ss");
COMMAND(md3scroll, "sff");
COMMAND(md3anim, "siifi");
COMMAND(md3link, "iis");
COMMAND(md3noclip, "si");
            
