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

import org.pircbotx.Colors;

/**
 * A player class with common methods and members for all types of players.
 * @author Yizhe Shen
 */
public class Player extends Record{

    /**
     * Creates a new Player.
     * 
     * @param nick IRC user nick
     */
    public Player(String nick){
        super();
        put("nick", nick);
        put("change", 0);
        put("transaction", 0);
        put("last", false);
        put("quit", false);
        put("idled", false);
    }
    
    @Override
    public Object get(String key) {
        if (key.equalsIgnoreCase("exists")) {
            return true;
        }        
        return super.get(key);
    }
    
    /* Player info methods */
    /**
     * Returns the Player's nick.
     * 
     * @return the Player's nick
     */
    public String getNick(){
        return getNick(true);
    }

    /**
     * Allows a nick to be returned with a zero-space character.
     * @param ping whether or not to add a zero-width character in nick
     * @return the Player's nick
     */
    public String getNick(boolean ping) {
        String nick = getString("nick");
        if (ping) {
            return nick;
        } else {
            return nick.substring(0, 1) + "\u200b" + nick.substring(1);
        }
    }
    
    /**
     * Returns the player's nick formatted in IRC bold.
     * 
     * @return the bold-formatted nick
     */
    public String getNickStr(){
        return getNickStr(true);
    }

    /**
     * Allows a bolded nick to be returned with a zero-space character.
     * 
     * @param ping whether or not to add a zero-width character in nick
     * @return the bold-formatted nick
     */
    public String getNickStr(boolean ping){
        return Colors.BOLD + getNick(ping) + Colors.BOLD;
    }

    /**
     * Transfers the specified amount from cash into bank.
     * 
     * @param amount the amount to transfer
     */
    public void bankTransfer(int amount){
        add("bank", amount);
        add("cash", -1 * amount);
        put("transaction", -1 * amount);
    }
    
    /**
     * Comparison of Player objects based on nick and host.
     * @param o the Object to compare
     * @return true if the properties are the same
     */
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Player) {
            Player p = (Player) o;
            if (get("nick").equals(p.get("nick")) && 
                    hashCode() == p.hashCode()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-generated hashCode method.
     * @return the Player's hashCode
     */
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + get("nick").hashCode();
        return hash;
    }
}