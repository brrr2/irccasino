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
package irccasino.cardgame;

import irccasino.Stats;
import java.util.Comparator;

/**
 * Stores a player's stats. 
 * @author Yizhe Shen
 */
public class PlayerRecord extends Stats{
    private String nick;
    
    /**
     * Initializes an empty PlayerRecord.
     */
    public PlayerRecord(){
        this("",0,0,0,0,0,0,0,0,0,0);
    }
    
    /**
     * Initializes a PlayerRecord with the given values.
     * @param nick
     * @param cash
     * @param bank
     * @param bankrupts
     * @param bjwinnings
     * @param bjrounds
     * @param tpwinnings
     * @param tprounds
     * @param ttwins
     * @param ttplayed
     * @param simple 
     */
    public PlayerRecord(String nick, int cash, int bank, int bankrupts, 
            int bjwinnings, int bjrounds, int tpwinnings, int tprounds,
            int ttwins, int ttplayed, int simple){
        super();
        this.nick = nick;
        set("cash", cash);
        set("bank", bank);
        set("bankrupts", bankrupts);
        set("bjwinnings", bjwinnings);
        set("bjrounds", bjrounds);
        set("tpwinnings", tpwinnings);
        set("tprounds", tprounds);
        set("ttwins", ttwins);
        set("ttplayed", ttplayed);
        set("simple", simple);
    }
    
    /**
     * Returns the stored nick.
     * @return the stored nick
     */
    public String getNick(){
        return nick;
    }
    
    /**
     * Sets the stored nick.
     * @param value the new nick
     */
    public void setNick(String value){
        nick = value;
    }
    
    @Override
    public int get(String stat){
        if (stat.equals("exists")){
            return 1;
        } else if (stat.equals("netcash")){
            return get("cash") + get("bank");
        }
        return super.get(stat);
    }
    
    /**
     * Copies the values from another PlayerRecord to this one.
     * @param a the other PlayerRecord
     */
    public void copy(PlayerRecord a) {
        nick = a.getNick();
        set("cash", a.get("cash"));
        set("bank", a.get("bank"));
        set("bankrupts", a.get("bankrupts"));
        set("bjwinnings", a.get("bjwinnings"));
        set("bjrounds", a.get("bjrounds"));
        set("tpwinnings", a.get("tpwinnings"));
        set("tprounds", a.get("tprounds"));
        set("ttwins", a.get("ttwins"));
        set("ttplayed", a.get("ttplayed"));
        set("simple", a.get("simple"));
    }
    
    @Override
    public String toString(){
        return getNick() + " " + get("cash") + " " + get("bank") + 
                " " + get("bankrupts") + " " + get("bjwinnings") + 
                " " + get("bjrounds") + " " + get("tpwinnings") + 
                " " + get("tprounds") + " " + get("ttwins") +
                " " + get("ttplayed") + " " + get("simple");
    }
    
    /**
     * Returns a comparator for PlayerRecord based on the specified stat.
     * @param stat the name of the stat
     * @return a comparator that ranks in descending order
     */
    public static Comparator<PlayerRecord> getComparator(final String stat) {
        return new Comparator<PlayerRecord>() {
            @Override
            public int compare(PlayerRecord a, PlayerRecord b) {
                if (a.get(stat) < b.get(stat)) {
                    return 1;
                } else if (a.get(stat) > b.get(stat)) {
                    return -1;
                }
                return 0;
            }
        };
    }
}