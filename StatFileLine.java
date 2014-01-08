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
package irccasino;

/**
 * Stores a single line of data read from the players' stats file.
 * @author Yizhe Shen
 */
public class StatFileLine extends Stats{
    private String nick;
    
    /**
     * Initializes an empty stat file line.
     */
    public StatFileLine(){
        this("",0,0,0,0,0,0,0,0);
    }
    
    /**
     * Initializes a stat file line with the given values.
     * @param nick
     * @param cash
     * @param bank
     * @param bankrupts
     * @param bjwinnings
     * @param bjrounds
     * @param tpwinnings
     * @param tprounds
     * @param simple 
     */
    public StatFileLine(String nick, int cash, int bank, int bankrupts, 
            int bjwinnings, int bjrounds, int tpwinnings, int tprounds,
            int simple){
        super();
        this.nick = nick;
        set("cash", cash);
        set("bank", bank);
        set("bankrupts", bankrupts);
        set("bjwinnings", bjwinnings);
        set("bjrounds", bjrounds);
        set("tpwinnings", tpwinnings);
        set("tprounds", tprounds);
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
    
    @Override
    public String toString(){
        return getNick() + " " + get("cash") + " " + get("bank") + 
                " " + get("bankrupts") + " " + get("bjwinnings") + 
                " " + get("bjrounds") + " " + get("tpwinnings") + 
                " " + get("tprounds") + " " + get("simple");
    }
}