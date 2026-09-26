# Creates t.db (next to this script): 2 players ("me" on level 3, "he" on level 2) with per-level attempt history.
# Used to test webapp/api/leaderboard.php via POT_TEST_DSN (see HANDOFF_2026-09-20.md, section 7).
import sqlite3, random
random.seed(1)
import os
c=sqlite3.connect(os.path.join(os.path.dirname(os.path.abspath(__file__)),"t.db"))
c.executescript("""
DROP TABLE IF EXISTS players; DROP TABLE IF EXISTS attempts;
CREATE TABLE players(id TEXT, seq INT, sync_token TEXT, display_name TEXT, level_id INT, task_id INT, sc_total REAL, attempts_total INT, wins_total INT, current_epoch INT DEFAULT 0, created_at TEXT, updated_at TEXT);
CREATE TABLE attempts(id INTEGER PRIMARY KEY, player_id TEXT, local_id INT, session_id INT, level_id INT, task_id INT, accuracy INT, duration_ms INT, error_ms INT, is_win INT, tolerance_ms INT, sc REAL, epoch INT, recorded_at TEXT);
""")
def player(pid,lvl,levels):
    n=0; w=0
    for acc,cnt,day in levels:
        for i in range(cnt):
            e=random.choice([0,0,5,-8,12,-20]) if acc>=2 else random.choice([0,100,-100])
            win=1 if e==0 else 0
            n+=1; w+=win
            c.execute("insert into attempts(player_id,local_id,session_id,level_id,task_id,accuracy,duration_ms,error_ms,is_win,tolerance_ms,sc,epoch,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",(pid,n,1,acc,1,acc,3000+e,e,win,0,1.5,0,"2026-09-%02d 10:00:00"%(day+i%3)))
    c.execute("insert into players values(?,?,?,?,?,?,?,?,?,0,'x','x')",(pid,1,'t',pid,lvl,1,n*1.5,n,w))
player("me",3,[(1,20,1),(2,30,5),(3,40,12)])
player("he",2,[(1,10,8),(2,25,10)])
c.commit()
