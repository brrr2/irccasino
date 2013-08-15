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

import org.pircbotx.*;

/**
 * A player class with common methods and members for all types of players.
 * It serves as a template and should not be directly instantiated.
 * @author Yizhe Shen
 */
public abstract class Player {
    /** Stores the player's simple status. */
    protected boolean simple;
    /** Stores the player's dealer status. */
    protected boolean dealer;
    /** Stores the player's stack. */
    protected int cash;
    /** Stores the player's bankroll. */
    protected int bank;
    /** Stores the number of times the player has gone bankrupt. */
    protected int bankrupts;
    /** 
     * Stores the number of rounds the player has played in the player
     * he is joined in.
     */
    protected int rounds;
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
        cash = 0;
        bank = 0;
        bankrupts = 0;
        rounds = 0;
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
     * Returns whether or not the Player has any cash in his stack.
     * 
     * @return true if cash is 0
     */
    public boolean isBankrupt(){
        return cash == 0;
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
    
    /* Stats keeping */
    /**
     * Sets the number of bankrupts for the Player.
     * 
     * @param c the number of bankrupts
     */
    public void setBankrupts(int c){
    	bankrupts = c;
    }
    
    /**
     * Returns the number of bankrupts for the Player.
     * 
     * @return the number of bankrupts
     */
    public int getBankrupts(){
    	return bankrupts;
    }
    
    /**
     * Increments the number of bankrupts for the Player.
     */
    public void incrementBankrupts(){
    	bankrupts++;
    }
    
    /**
     * Returns the number of rounds played for the Player.
     * 
     * @return the number of rounds played
     */
    public int getRounds(){
    	return rounds;
    }
    
    /**
     * Sets the number of rounds played for the Player.
     * 
     * @param value the number of rounds
     */
    public void setRounds(int value){
    	rounds = value;
    }
    
    /**
     * Increments the number of rounds played for the Player.
     */
    public void incrementRounds(){
    	rounds++;
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
    
    /* Methods for cash manipulation */
    /**
     * Sets the player's stack to the specified amount.
     * 
     * @param amount the amount to set
     */
    public void setCash(int amount){
        cash = amount;
    }
    
    /**
     * Adds the specified amount to a player's stack.
     * 
     * @param amount the amount to add
     */
    public void addCash(int amount){
        cash += amount;
    }
    
    /**
     * Returns the player's stack.
     * 
     * @return the player's stack
     */
    public int getCash(){
        return cash;
    }
    
    /**
     * Sets the player's bank to the specified amount.
     * 
     * @param amount the amount to set
     */
    public void setBank(int amount){
    	bank = amount;
    }
    
    /**
     * Subtracts the specified amount from the player's bank.
     * 
     * @param amount the amount to subtract.
     */
    public void addDebt(int amount){
    	bank -= amount;
    }
    
    /**
     * Returns the amount the player has stored in bank.
     * 
     * @return the bank amount
     */
    public int getBank(){
    	return bank;
    }
    
    /**
     * Transfers the specified amount from cash into bank.
     * 
     * @param amount the amount to transfer
     */
    public void bankTransfer(int amount){
    	bank += amount;
    	cash -= amount;
    }
    
    /**
     * Returns the total amount of stack and bank for the player.
     * 
     * @return cash plus bank
     */
    public int getNetCash(){
        return cash + bank;
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