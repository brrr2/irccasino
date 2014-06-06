/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

    This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
 */

package irccasino.texastourney;

import irccasino.cardgame.CardGame;
import irccasino.cardgame.Player;
import irccasino.cardgame.Record;
import java.util.ArrayList;

/**
 * Stores tournament stats.
 * @author Yizhe Shen
 */
public class TourneyStat extends Record{
    private ArrayList<Player> players;
    private Player winner;
    
    public TourneyStat() {
        this(0);
    }
    
    public TourneyStat(int num) {
        super();
        put("numtourneys", num);
        players = new ArrayList<>();
        winner = null;
    }
    
    public Player getWinner() {
        return winner;
    }
    
    public void setWinner(Player p) {
        winner = p;
    }
    
    public void addPlayer(Player p) {
        players.add(p);
    }
    
    public ArrayList<Player> getPlayers() {
        return players;
    }
    
    public String getPlayersString() {
        String outStr = "";
        for (int ctr = 0; ctr < players.size(); ctr++){
            outStr += players.get(ctr).getNick() + " ";
        }
        return outStr.substring(0, outStr.length() - 1);
    }
    
    public String getToStringList(){
        String outStr;
        int size = players.size();
        outStr = CardGame.formatBold(size) + " players: ";
        for (Player p : players) {
            if (p.equals(winner)) {
                outStr += p.getNickStr(false) + ", ";
            } else {
                outStr += p.getNick(false) + ", ";
            }
        }
        return outStr.substring(0, outStr.length() - 2);
    }
    
    public int getBiggestTourney() {
        return players.size();
    }
    
    @Override
    public String toString() {
        return "Total Tournaments: " + CardGame.formatNumber(getInteger("numtourneys")) + ". Biggest Tournament: " + getToStringList() + ".";
    }
}
