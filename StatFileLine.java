/*
	Copyright (C) 2013 Yizhe Shen <brrr@live.ca>
	
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

import java.util.HashMap;

public class StatFileLine{
    private HashMap<String,Integer> statsMap;
    private String nick;
    private boolean simple;
    
    public StatFileLine(){
        this("",0,0,0,0,0,0,0,true);
    }
    
    public StatFileLine(String nick, int cash, int bank, int bankrupts, 
            int bjwinnings, int bjrounds, int tpwinnings, int tprounds,
            boolean simple){
        statsMap = new HashMap<String,Integer>();
        this.nick = nick;
        statsMap.put("cash", cash);
        statsMap.put("bank", bank);
        statsMap.put("bankrupts", bankrupts);
        statsMap.put("bjwinnings", bjwinnings);
        statsMap.put("bjrounds", bjrounds);
        statsMap.put("tpwinnings", tpwinnings);
        statsMap.put("tprounds", tprounds);
        this.simple = simple;
    }
    
    public String getNick(){
        return nick;
    }
    
    public void setNick(String value){
        nick = value;
    }
    
    public int getStat(String stat){
        return statsMap.get(stat);
    }
    
    public void setStat(String stat, int value){
        statsMap.put(stat, value);
    }
    
    public boolean getSimple(){
        return simple;
    }
    
    public void setSimple(boolean value){
        simple = value;
    }
    
    @Override
    public String toString(){
        return getNick() + " " + getStat("cash") + " " + getStat("bank") + 
                " " + getStat("bankrupts") + " " + getStat("bjwinnings") + 
                " " + getStat("bjrounds") + " " + getStat("tpwinnings") + 
                " " + getStat("tprounds") + " " + getSimple();
    }
}