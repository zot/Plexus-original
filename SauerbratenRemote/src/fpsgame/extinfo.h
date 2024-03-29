
#define EXT_ACK                         -1
#define EXT_VERSION                     103
#define EXT_NO_ERROR                    0
#define EXT_ERROR                       1
#define EXT_PLAYERSTATS_RESP_IDS        -10
#define EXT_PLAYERSTATS_RESP_STATS      -11
#define EXT_UPTIME                      0
#define EXT_PLAYERSTATS                 1
#define EXT_TEAMSCORE                   2

/*
    Client:
    -----
    A: 0 EXT_UPTIME
    B: 0 EXT_PLAYERSTATS cn #a client number or -1 for all players#
    C: 0 EXT_TEAMSCORE

    Server:  
    --------
    A: 0 EXT_UPTIME EXT_ACK EXT_VERSION uptime #in seconds#
    B: 0 EXT_PLAYERSTATS cn #send by client# EXT_ACK EXT_VERSION 0 or 1 #error, if cn was > -1 and client does not exist# ...
         EXT_PLAYERSTATS_RESP_IDS pid(s) #1 packet#
         EXT_PLAYERSTATS_RESP_STATS pid playerdata #1 packet for each player#
    C: 0 EXT_TEAMSCORE EXT_ACK EXT_VERSION 0 or 1 #error, no teammode# remaining_time gamemode loop(teamdata [numbases bases] or -1)

    Errors:
    --------------
    B:C:default: 0 command EXT_ACK EXT_VERSION EXT_ERROR
*/

    void extinfoplayer(ucharbuf &p, clientinfo *ci)
    {
        ucharbuf q = p;
        putint(q, EXT_PLAYERSTATS_RESP_STATS); // send player stats following
        putint(q, ci->clientnum); //add player id
        sendstring(ci->name, q);
        sendstring(ci->team, q);
        putint(q, ci->state.frags);
        putint(q, ci->state.deaths);
        putint(q, ci->state.teamkills);
        putint(q, ci->state.damage*100/max(ci->state.shotdamage,1));
        putint(q, ci->state.health);
        putint(q, ci->state.armour);
        putint(q, ci->state.gunselect);
        putint(q, ci->privilege);
        putint(q, ci->state.state);
        uint ip = getclientip(ci->clientnum);
        q.put((uchar*)&ip, 3);
        sendserverinforeply(q);
    }

    struct teamscore
    {
        const char *name;
        int score;

        teamscore(const char *name, int score) : name(name), score(score) {}
    };

    void extinfoteams(ucharbuf &p)
    {
        putint(p, m_teammode ? 0 : 1);
        putint(p, gamemode);
        putint(p, minremain);
        if(!m_teammode) return;

        vector<teamscore> scores;

        //most taken from scoreboard.h
        if(m_capture)
        {
            loopv(capturemode.scores) scores.add(teamscore(capturemode.scores[i].team, capturemode.scores[i].total));
            loopv(clients) if(clients[i]->team[0]) //check all teams available, since capturemode.scores contains only teams with scores
            {
                teamscore *ts = NULL;
                loopvj(scores) if(!strcmp(scores[j].name, clients[i]->team)) { ts = &scores[i]; break; }
                if(!ts) scores.add(teamscore(clients[i]->team, 0));
            }
        }
        else if(m_ctf)
        {
            loopv(ctfmode.flags)
            {
                const char *team = ctfflagteam(ctfmode.flags[i].team);
                if(!team) continue;
                teamscore *ts = NULL;
                loopvj(scores) if(!strcmp(scores[j].name, team)) { ts = &scores[i]; break; }
                if(!ts) scores.add(teamscore(team, ctfmode.flags[i].score));
                else ts->score += ctfmode.flags[i].score;
            }
        }
        else
        {
            loopv(clients) if(clients[i]->team[0])
            {
                clientinfo *ci = clients[i];
                teamscore *ts = NULL;
                loopvj(scores) if(!strcmp(scores[j].name, ci->team)) { ts = &scores[i]; break; }
                if(!ts) scores.add(teamscore(ci->team, ci->state.frags));
                else ts->score += ci->state.frags;
            }
        }

        loopv(scores)
        {
            sendstring(scores[i].name, p);
            putint(p, scores[i].score);

            if(m_capture)
            {
                int bases = 0;
                loopvj(capturemode.bases) if(!strcmp(capturemode.bases[j].owner, scores[i].name)) bases++;
                putint(p, bases);
                loopvj(capturemode.bases) if(!strcmp(capturemode.bases[j].owner, scores[i].name)) putint(p, j);
            }
            else putint(p,-1); //no bases follow
        }
    }

    void extserverinforeply(ucharbuf &req, ucharbuf &p)
    {
        int extcmd = getint(req); // extended commands  

        //Build a new packet
        putint(p, EXT_ACK); //send ack
        putint(p, EXT_VERSION); //send version of extended info

        switch(extcmd)
        {
            case EXT_UPTIME:
            {
                putint(p, uint(totalmillis)/1000); //in seconds
                break;
            }

            case EXT_PLAYERSTATS:
            {
                int cn = getint(req); //a special player, -1 for all
                
                clientinfo *ci = NULL;
                if(cn >= 0)
                {
                    loopv(clients) if(clients[i]->clientnum == cn) { ci = clients[i]; break; }
                    if(!ci)
                    {
                        putint(p, EXT_ERROR); //client requested by id was not found
                        sendserverinforeply(p);
                        return;
                    }
                }

                putint(p, EXT_NO_ERROR); //so far no error can happen anymore
                
                ucharbuf q = p; //remember buffer position
                putint(q, EXT_PLAYERSTATS_RESP_IDS); //send player ids following
                if(ci) putint(q, ci->clientnum);
                else loopv(clients) putint(q, clients[i]->clientnum);
                sendserverinforeply(q);
            
                if(ci) extinfoplayer(p, ci);
                else loopv(clients) extinfoplayer(p, clients[i]);
                return;
            }

            case EXT_TEAMSCORE:
            {
                extinfoteams(p);
                break;
            }

            default:
            {
                putint(p, EXT_ERROR);
                break;
            }
        }
        sendserverinforeply(p);
    }

