// menus.cpp: ingame menu system (also used for scores and serverlist)

#include "pch.h"
#include "engine.h"

#define GUI_TITLE_COLOR  0xFFDD88
#define GUI_BUTTON_COLOR 0xFFFFFF
#define GUI_TEXT_COLOR   0xDDFFDD

static vec menupos;
static int menustart = 0;
static int menutab = 1;
static g3d_gui *cgui = NULL;

struct menu : g3d_callback
{
    char *name, *header, *contents;

    menu() : name(NULL), header(NULL), contents(NULL) {}

    void gui(g3d_gui &g, bool firstpass)
    {
        cgui = &g;
        cgui->start(menustart, 0.03f, &menutab);
        cgui->tab(header ? header : name, GUI_TITLE_COLOR);
        execute(contents);
        cgui->end();
        cgui = NULL;
    }

    virtual void clear() {}
};

static hashtable<const char *, menu> guis;
static vector<menu *> guistack;
static vector<char *> executelater;
static bool shouldclearmenu = true, clearlater = false;

VARP(menudistance,  16, 40,  256);
VARP(menuautoclose, 32, 120, 4096);

vec menuinfrontofplayer()
{ 
    vec dir;
    vecfromyawpitch(camera1->yaw, 0, 1, 0, dir);
    dir.mul(menudistance).add(camera1->o);
    dir.z -= player->eyeheight-1;
    return dir;
}

void popgui()
{
    menu *m = guistack.pop();
    m->clear();
}

void removegui(menu *m)
{
    loopv(guistack) if(guistack[i]==m)
    {
        guistack.remove(i);
        m->clear();
        return;
    }
}    

#ifdef TC
bool tc_aremenuspresent()
{
	return !guistack.empty(); //  || !guis3d.length();
}
#endif

void pushgui(menu *m, int pos = -1)
{
    if(guistack.empty())
    {
        menupos = menuinfrontofplayer();
#ifdef TC
		//extern int tcmode;
		//if (!tcmode) g3d_resetcursor();
#else
        g3d_resetcursor();
#endif
    }
    if(pos < 0) guistack.add(m);
    else guistack.insert(pos, m);
    if(pos < 0 || pos==guistack.length()-1)
    {
        menutab = 1;
        menustart = totalmillis;
    }
}

void restoregui(int pos)
{
    int clear = guistack.length()-pos-1;
    loopi(clear) popgui();
    menutab = 1;
    menustart = totalmillis;
}

void showgui(const char *name)
{
    menu *m = guis.access(name);
    if(!m) return;
    int pos = guistack.find(m);
    if(pos<0) pushgui(m);
    else restoregui(pos);
}

int cleargui(int n = 0)
{
    int clear = guistack.length();
    if(n>0) clear = min(clear, n);
    loopi(clear) popgui(); 
    if(!guistack.empty()) restoregui(guistack.length()-1);
    return clear;
}

void cleargui_(int *n)
{
    intret(cleargui(*n));
}

void guistayopen(char *contents)
{
    bool oldclearmenu = shouldclearmenu;
    shouldclearmenu = false;
    execute(contents);
    shouldclearmenu = oldclearmenu;
}

void guinoautotab(char *contents)
{
    if(!cgui) return;
    cgui->allowautotab(false);
    execute(contents);
    cgui->allowautotab(true);
}

//@DOC name and icon are optional
void guibutton(char *name, char *action, char *icon)
{
    if(!cgui) return;
    int ret = cgui->button(name, GUI_BUTTON_COLOR, *icon ? icon : (strstr(action, "showgui") ? "menu" : "action"));
    if(ret&G3D_UP) 
    {
        executelater.add(newstring(*action ? action : name));
        if(shouldclearmenu) clearlater = true;
    }
    else if(ret&G3D_ROLLOVER)
    {
        alias("guirollovername", name);
        alias("guirolloveraction", action);
    }
}

void guiimage(char *path, char *action, float *scale, int *overlaid, char *alt)
{
    if(!cgui) return;
    Texture *t = textureload(path, 0, true, false);
    if(t==notexture)
    {
        if(alt[0]) t = textureload(alt, 0, true, false);
        if(t==notexture) return;
    }
    int ret = cgui->image(t, *scale, *overlaid!=0);
    if(ret&G3D_UP)
    {
        if(*action)
        {
            executelater.add(newstring(action));
            if(shouldclearmenu) clearlater = true;
        }
    }
    else if(ret&G3D_ROLLOVER)
    {
        alias("guirolloverimgpath", path);
        alias("guirolloverimgaction", action);
    }
}

void guitext(char *name, char *icon)
{
    if(cgui) cgui->text(name, icon[0] ? GUI_BUTTON_COLOR : GUI_TEXT_COLOR, icon[0] ? icon : "info");
}

void guititle(char *name)
{
    if(cgui) cgui->title(name, GUI_TITLE_COLOR);
}

void guitab(char *name)
{
    if(cgui) cgui->tab(name, GUI_TITLE_COLOR);
}

void guibar()
{
    if(cgui) cgui->separator();
}

static void updateval(char *var, int val, char *onchange)
{
    ident *id = getident(var);
    string assign;
    if(!id) return;
    switch(id->type)
    {
        case ID_VAR:
        case ID_FVAR:
        case ID_SVAR:
            s_sprintf(assign)("%s %d", var, val);
            break;
        case ID_ALIAS: 
            s_sprintf(assign)("%s = %d", var, val);
            break;
        default:
            return;
    }
    executelater.add(newstring(assign));
    if(onchange[0]) executelater.add(newstring(onchange)); 
}

static int getval(char *var)
{
    ident *id = getident(var);
    if(!id) return 0;
    switch(id->type)
    {
        case ID_VAR: return *id->storage.i;
        case ID_FVAR: return int(*id->storage.f);
        case ID_SVAR: return atoi(*id->storage.s);
        case ID_ALIAS: return atoi(id->action);
        default: return 0;
    }
}

void guislider(char *var, int *min, int *max, char *onchange)
{
	if(!cgui) return;
    int oldval = getval(var), val = oldval, vmin = *max ? *min : getvarmin(var), vmax = *max ? *max : getvarmax(var);
    cgui->slider(val, vmin, vmax, GUI_TITLE_COLOR);
    if(val != oldval) updateval(var, val, onchange);
}

void guilistslider(char *var, char *list, char *onchange)
{
    if(!cgui) return;
    vector<int> vals;
    list += strspn(list, "\n\t ");
    while(*list)
    {
        vals.add(atoi(list));
        list += strcspn(list, "\n\t \0");
        list += strspn(list, "\n\t ");
    }
    if(vals.empty()) return;
    int val = getval(var), oldoffset = vals.length()-1, offset = oldoffset;
    loopv(vals) if(val <= vals[i]) { oldoffset = offset = i; break; }
    s_sprintfd(label)("%d", val);
    cgui->slider(offset, 0, vals.length()-1, GUI_TITLE_COLOR, label);
    if(offset != oldoffset) updateval(var, vals[offset], onchange);
}

void guicheckbox(char *name, char *var, int *on, int *off, char *onchange)
{
    bool enabled = getval(var)!=*off;
    if(cgui && cgui->button(name, GUI_BUTTON_COLOR, enabled ? "checkbox_on" : "checkbox_off")&G3D_UP)
    {
        updateval(var, enabled ? *off : (*on || *off ? *on : 1), onchange);
    }
}

void guiradio(char *name, char *var, int *n, char *onchange)
{
    bool enabled = getval(var)==*n;
    if(cgui && cgui->button(name, GUI_BUTTON_COLOR, enabled ? "radio_on" : "radio_off")&G3D_UP)
    {
        if(!enabled) updateval(var, *n, onchange);
    }
}

void guibitfield(char *name, char *var, int *mask, char *onchange)
{
    int val = getval(var);
    bool enabled = (val & *mask) != 0;
    if(cgui && cgui->button(name, GUI_BUTTON_COLOR, enabled ? "checkbox_on" : "checkbox_off")&G3D_UP)
    {
        updateval(var, enabled ? val & ~*mask : val | *mask, onchange);
    }
}

//-ve length indicates a wrapped text field of any (approx 260 chars) length, |length| is the field width
void guifield(char *var, int *maxlength, char *onchange)
{   
    if(!cgui) return;
    const char *initval = "";
    ident *id = getident(var);
    if(id && id->type==ID_ALIAS) initval = id->action;
	char *result = cgui->field(var, GUI_BUTTON_COLOR, *maxlength ? *maxlength : 12, 0, initval);
    if(result) 
    {
        alias(var, result);
        if(onchange[0]) execute(onchange);
    }
}

//-ve maxlength indicates a wrapped text field of any (approx 260 chars) length, |maxlength| is the field width
void guieditor(char *name, int *maxlength, int *height, int *mode)
{
    if(!cgui) return;
    cgui->field(name, GUI_BUTTON_COLOR, *maxlength ? *maxlength : 12, *height, NULL, *mode<=0 ? EDITORFOREVER : *mode);
    //returns a non-NULL pointer (the currentline) when the user commits, could then manipulate via text* commands
}

COMMAND(guieditor, "siii");
//use text<action> to do more...


void guilist(char *contents)
{
    if(!cgui) return;
    cgui->pushlist();
    execute(contents);
    cgui->poplist();
}

void newgui(char *name, char *contents, char *header)
{
    menu *m = guis.access(name);
    if(!m)
    {
        name = newstring(name);
        m = &guis[name];
        m->name = name;
    }
    else
    {
        DELETEA(m->header);
        DELETEA(m->contents);
    }
    m->header = header && header[0] ? newstring(header) : NULL;
    m->contents = newstring(contents);
}

void guiservers()
{
    extern const char *showservers(g3d_gui *cgui);
    if(cgui) 
    {
        const char *name = showservers(cgui);
        if(name)
        {
            s_sprintfd(connect)("connect %s", name);
            executelater.add(newstring(connect));
            if(shouldclearmenu) clearlater = true;
        }
    }
}

COMMAND(newgui, "sss");
COMMAND(guibutton, "sss");
COMMAND(guitext, "ss");
COMMAND(guiservers, "s");
COMMANDN(cleargui, cleargui_, "i");
COMMAND(showgui, "s");
COMMAND(guistayopen, "s");
COMMAND(guinoautotab, "s");

COMMAND(guilist, "s");
COMMAND(guititle, "s");
COMMAND(guibar,"");
COMMAND(guiimage,"ssfis");
COMMAND(guislider,"siis");
COMMAND(guilistslider, "sss");
COMMAND(guiradio,"ssis");
COMMAND(guibitfield, "ssis");
COMMAND(guicheckbox, "ssiis");
COMMAND(guitab, "s");
COMMAND(guifield, "sis");

struct change
{
    int type;
    const char *desc;

    change() {}
    change(int type, const char *desc) : type(type), desc(desc) {}
};
static vector<change> needsapply;

static struct applymenu : menu
{
    void gui(g3d_gui &g, bool firstpass)
    {
        if(guistack.empty()) return;
        g.start(menustart, 0.03f);
        g.text("the following settings have changed:", GUI_TEXT_COLOR, "info");
        loopv(needsapply) g.text(needsapply[i].desc, GUI_TEXT_COLOR, "info");
        g.separator();
        g.text("apply changes now?", GUI_TEXT_COLOR, "info");
        if(g.button("yes", GUI_BUTTON_COLOR, "action")&G3D_UP)
        {
            int changetypes = 0;
            loopv(needsapply) changetypes |= needsapply[i].type;
            if(changetypes&CHANGE_GFX) executelater.add(newstring("resetgl"));
            if(changetypes&CHANGE_SOUND) executelater.add(newstring("resetsound"));
            clearlater = true;
        }
        if(g.button("no", GUI_BUTTON_COLOR, "action")&G3D_UP)
            clearlater = true;
        g.end();
    }

    void clear()
    {
        needsapply.setsize(0);
    }
} applymenu;

VARP(applydialog, 0, 1, 1);

void addchange(const char *desc, int type)
{
    if(!applydialog) return;
    loopv(needsapply) if(!strcmp(needsapply[i].desc, desc)) return;
    needsapply.add(change(type, desc));
    if(needsapply.length() && guistack.find(&applymenu) < 0)
        pushgui(&applymenu, 0);
}

void clearchanges(int type)
{
    loopv(needsapply)
    {
        if(needsapply[i].type&type)
        {
            needsapply[i].type &= ~type;
            if(!needsapply[i].type) needsapply.remove(i--);
        }
    }
    if(needsapply.empty()) removegui(&applymenu);
}

void menuprocess()
{
    int level = guistack.length();
    loopv(executelater) execute(executelater[i]);
    executelater.deletecontentsa();
    if(clearlater)
    {
        if(level==guistack.length()) loopi(level) popgui();
        clearlater = false;
    }
}

void g3d_mainmenu()
{
    if(!guistack.empty()) 
    {   
        extern int usegui2d;
        if(!usegui2d && camera1->o.dist(menupos) > menuautoclose) cleargui();
        else g3d_addgui(guistack.last(), menupos, GUI_2D | GUI_FOLLOW);
    }
}

