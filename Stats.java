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

public abstract class Stats {
    /** Stores the stats. */
    protected HashMap<String,Integer> statsMap;
    
    public void set(String stat, int value){
        statsMap.put(stat, value);
    }
    
    public int get(String stat){
        return statsMap.get(stat);
    }
    
    public void increment(String stat){
        int value = statsMap.get(stat);
        statsMap.put(stat, value + 1);
    }
    
    public void add(String stat, int amount){
        int value = statsMap.get(stat);
        statsMap.put(stat, value + amount);
    }
    
    public boolean has(String stat){
        return statsMap.get(stat) > 0;
    }
    
    public void clear(String stat){
        set(stat, 0);
    }
    
    public boolean exists(String stat){
        return statsMap.containsKey(stat);
    }
}