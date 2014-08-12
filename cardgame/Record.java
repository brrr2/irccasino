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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;

/**
 * A HashMap wrapper class to store data.
 * @author Yizhe Shen
 */
public class Record {
    private HashMap<String,Object> map;
    
    /**
     * Initializes an empty Record.
     */
    public Record(){
        map = new HashMap<>();
    }
    
    /**
     * Wrapper for HashMap.get() method with some additions.
     * @param key
     * @return 
     */
    public Object get(String key) {
        return map.get(key);
    }
    
    /**
     * String wrapper for get.
     * @param key
     * @return 
     */
    public String getString(String key) {
        return (String) get(key);
    }
    
    /**
     * Boolean wrapper for get.
     * @param key
     * @return 
     */
    public Boolean getBoolean(String key) {
        return (Boolean) get(key);
    }
    
    /**
     * Integer wrapper for get.
     * @param key
     * @return 
     */
    public Integer getInteger(String key) {
        return (Integer) get(key);
    }
    
    /**
     * Long wrapper for get.
     * @param key
     * @return 
     */
    public Long getLong(String key) {
        return (Long) get(key);
    }
    
    /**
     * Double wrapper for get.
     * @param key
     * @return 
     */
    public Double getDouble(String key) {
        return (Double) get(key);
    }
    
    /**
     * Determines if the value at a key is true or greater than 0.
     * @param key 
     * @return  
     */
    public boolean has(String key) {
        Object o = get(key);
        if (o instanceof Boolean) {
            return (Boolean) o;
        } else if (o instanceof Integer) {
            return (Integer) o > 0;
        } else if (o instanceof Double) {
            return (Double) o > 0;
        } else if (o instanceof String) {
            return !((String) o).equals("");
        }
        return false;
    }
    
    /**
     * Wrapper for HashMap.put() method.
     * @param key
     * @param value 
     */
    public void put(String key, Object value) {
        map.put(key, value);
    }
    
    /**
     * Attempts to convert a new string value to the type stored at the
     * specified key. Inserts the new converted value if conversion is
     * successful.
     * @param key
     * @param value 
     */
    public void putStrVal(String key, String value) throws IllegalArgumentException {
        Object oldValue = get(key);
        if (oldValue == null || value == null) {
            throw new IllegalArgumentException();
        }
        
        Object newValue;
        if (oldValue instanceof Boolean) {
            switch (value) {
                case "1": case "0":
                    newValue = value.equals("1") ? Boolean.TRUE : Boolean.FALSE;
                    break;
                case "yes": case "no":
                    newValue = value.equals("yes") ? Boolean.TRUE : Boolean.FALSE;
                    break;
                default:
                    newValue = Boolean.parseBoolean(value);
                    break;
            }
        } else if (oldValue instanceof Integer) {
            newValue = Integer.parseInt(value);
        } else if (oldValue instanceof Double) {
            newValue = Double.parseDouble(value);
        } else {
            newValue = value;
        }
        map.put(key, newValue);
    }
    
    /**
     * Adds the specified value to the value stored at the specified key.
     * @param key
     * @param value 
     */
    public void add(String key, Object value) {
        Object o = get(key);
        if (o instanceof Integer) {
            put(key, (Integer) o + (Integer) value);
        } else if (o instanceof Double) {
            put(key, (Double) o + (Double) value);
        }
    }
    
    /**
     * Resets the value at a key to its default value.
     * @param key
     */
    public void clear(String key){
        Object o = get(key);
        if (o instanceof Integer) {
            put(key, 0);
        } else if (o instanceof Double) {
            put(key, 0.);
        } else if (o instanceof Boolean) {
            put(key, Boolean.FALSE);
        } else if (o instanceof String) {
            put(key, "");
        }
    }
    
    /**
     * Determines whether or not a key exists in the map.
     * @param key
     * @return true if statsMap contains the key
     */
    public boolean exists(String key){
        return map.containsKey(key);
    }
    
    /**
     * Wrapper for HashMap.keySet() method.
     * @return the key set
     */
    public Set<String> keySet() {
        return map.keySet();
    }
    
    /**
     * Wrapper for HashMap.clear() method.
     */
    public void empty() {
        map.clear();
    }
    
    @Override
    public String toString(){
        String output = "";
        for (String key : map.keySet()) {
            output += get(key) + " ";
        }
        return output.substring(0, output.length() - 1);
    }
    
    /**
     * Returns a comparator for Record based on the specified key.
     * @param key the name of the key
     * @return a comparator that ranks values in ascending order for a given key
     */
    public static Comparator<Record> getComparator(final String key) {
        return new Comparator<Record>() {
            @Override
            public int compare(Record a, Record b) {
                Object o = a.get(key);
                if (o instanceof Integer) {
                    return Integer.compare(a.getInteger(key), b.getInteger(key));
                } else if (o instanceof Double) {
                    return Double.compare(a.getDouble(key), b.getDouble(key));
                } else if (o instanceof String) {
                    return String.CASE_INSENSITIVE_ORDER.compare(a.getString(key), b.getString(key));
                }
                return 0;
            }
        };
    }
}