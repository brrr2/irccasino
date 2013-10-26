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
import org.pircbotx.*;

/**
 * A player class with common methods and members for all types of players.
 * It serves as a template and should not be directly instantiated.
 * @author Yizhe Shen
 */
public abstract class Player extends Stats{
    /** Stores the player's simple status. */
    protected boolean simple;
    /** Stores the player's dealer status. */
    protected boolean dealer;
    /** Stores the player's quit status. */
    protected boolean quit;
    /** Stores the player's nick. */
    protected String nick;
    /** Stores the player's hostmask. */
    protected String hostmask;
    
    /**
     * Creates a new Player.
     * Not to be instantiated directly. Serves as the template for specific types
     * of players.
     * 
     * @param nick IRC user nick
     * @param hostmask IRC user hostmask
     * @param dealer Whether or not this player is dealer
     */
    public Player(String nick, String hostmask, boolean dealer){
    	this.nick = nick;
        this.dealer = dealer;
        this.hostmask = hostmask;
        statsMap = new HashMap<String,Integer>();
        statsMap.put("cash", 0);
        statsMap.put("bank", 0);
        statsMap.put("bankrupts", 0);
        statsMap.put("bjrounds", 0);
        statsMap.put("bjwinnings", 0);
        statsMap.put("tprounds", 0);
        statsMap.put("tpwinnings", 0);
        simple = true;
        quit = false;
    }
    
    /* Player info methods */
    /**
     * Returns the Player's nick or "Dealer" if the player is in the dealer role.
     * 
     * @return the Player's name
     */
    public String getNick(){
    	if (dealer){
    		return "Dealer";
        } else {
    		return nick;
        }
    }
    
    /**
     * Sets the Player's dealer status.
     * 
     * @param b the new status
     */
    public void setDealer(boolean b){
    	dealer = b;
    }
    
    /**
     * Whether or not the Player is the dealer.
     * 
     * @return true if the Player is the dealer
     */
    public boolean isDealer(){
        return dealer;
    }
    
    /**
     * Sets the Player's quit status.
     * 
     * @param b the new status
     */
    public void setQuit(boolean b){
    	quit = b;
    }
    
    /**
     * Whether or not the Player has quit.
     * 
     * @return true if the Player has quit
     */
    public boolean hasQuit(){
    	return quit;
    }
    
    /**
     * Returns the simple status of the Player.
     * If simple is true, then game information is sent via notices. If simple
     * is false, then game information is sent via private messages.
     * 
     * @return true if simple is turned on
     */
    public boolean isSimple(){
        return simple;
    }
    
    /**
     * Sets the Player's simple status.
     * 
     * @param s the new status
     */
    public void setSimple(boolean s){
        simple = s;
    }
    
    @Override
    public int get(String stat){
        if (stat.equals("exists")){
            return 1;
        } else if (stat.equals("netcash")){
            return statsMap.get("cash") + statsMap.get("bank");
        }
        return statsMap.get(stat);
    }
    
    /**
     * Transfers the specified amount from cash into bank.
     * 
     * @param amount the amount to transfer
     */
    public void bankTransfer(int amount){
        add("bank", amount);
        add("cash", -1 * amount);
    }
    
    /**
     * Returns the player's nick formatted in IRC bold.
     * 
     * @return the bold-formatted nick
     */
    public String getNickStr(){
    	return Colors.BOLD+getNick()+Colors.BOLD;
    }
    
    /**
     * String representation includes the Player's nick and hostmask.
     * 
     * @return a String containing the Players nick and hostmask
     */
    @Override
    public String toString(){
    	return getNick() + " " + hostmask;
    }
}