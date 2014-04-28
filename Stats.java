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

import java.util.HashMap;

/**
 * Wrapper class for a HashMap to store stats.
 * @author Yizhe Shen
 */
public abstract class Stats {
    /** Stores the stats. */
    protected HashMap<String,Integer> statsMap;
    
    public Stats() {
        statsMap = new HashMap<>();
    }
    
    /**
     * Sets a new value for a stat.
     * @param stat the stat
     * @param value the new value
     */
    public void set(String stat, int value){
        statsMap.put(stat, value);
    }
    
    /**
     * Gets the value of a stat.
     * @param stat the stat
     * @return the value
     */
    public int get(String stat){
        return statsMap.get(stat);
    }
    
    /**
     * Increments the value of a stat.
     * @param stat the stat
     */
    public void increment(String stat){
        statsMap.put(stat, get(stat) + 1);
    }
    
    /**
     * Decrements the value of a stat.
     * @param stat the stat
     */
    public void decrement(String stat){
        statsMap.put(stat, get(stat) - 1);
    }
    
    /**
     * Adds the specified amount to the value of a stat.
     * @param stat the stat
     * @param amount the amount to add
     */
    public void add(String stat, int amount){
        statsMap.put(stat, get(stat) + amount);
    }
    
    /**
     * Determines whether or not a stat has a positive value.
     * @param stat the stat
     * @return true if the value of the stat is greater than 0
     */
    public boolean has(String stat){
        return statsMap.get(stat) > 0;
    }
    
    /**
     * Sets the value of a stat to 0.
     * @param stat the stat
     */
    public void clear(String stat){
        set(stat, 0);
    }
    
    /**
     * Determines whether or not a stat exists in the statsMap.
     * @param stat the stat
     * @return true if statsMap contains the stat key
     */
    public boolean exists(String stat){
        return statsMap.containsKey(stat);
    }
}