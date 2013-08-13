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

public class Player {
    protected boolean simple, dealer;
    protected int cash, bank, bankrupts, rounds;
    protected boolean quit;
    protected String nick, hostmask;
    
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
     * Returns the player's nick or "Dealer" if the player is in the dealer role.
     * @return the player's name
     */
    public String getNick(){
    	if (dealer){
    		return "Dealer";
        } else {
    		return nick;
        }
    }
    /**
     * Returns whether or not the player has any cash.
     * @return true if cash is 0
     */
    public boolean isBankrupt(){
        return cash == 0;
    }
    public void setDealer(boolean b){
    	dealer = b;
    }
    public boolean isDealer(){
        return dealer;
    }
    public void setQuit(boolean b){
    	quit = b;
    }
    public boolean hasQuit(){
    	return quit;
    }
    
    /* Stats keeping */
    public void setBankrupts(int c){
    	bankrupts = c;
    }
    public int getBankrupts(){
    	return bankrupts;
    }
    public void incrementBankrupts(){
    	bankrupts++;
    }
    public int getRounds(){
    	return rounds;
    }
    public void setRounds(int value){
    	rounds = value;
    }
    public void incrementRounds(){
    	rounds++;
    }
    
    /* Send user information via notice or message */
    public boolean isSimple(){
        return simple;
    }
    public void setSimple(boolean s){
        simple = s;
    }
    
    /* Methods for cash manipulation */
    /**
     * Sets the player's stack to the specified amount.
     * @param amount the amount to set
     */
    public void setCash(int amount){
        cash = amount;
    }
    /**
     * Adds the specified amount to a player's stack.
     * @param amount the amount to add
     */
    public void addCash(int amount){
        cash += amount;
    }
    /**
     * Returns the player's stack.
     * @return the player's stack
     */
    public int getCash(){
        return cash;
    }
    /**
     * Sets the player's bank to the specified amount.
     * @param amount the amount to set
     */
    public void setBank(int amount){
    	bank = amount;
    }
    /**
     * Subtracts the specified amount from the player's bank.
     * @param amount the amount to subtract.
     */
    public void addDebt(int amount){
    	bank -= amount;
    }
    /**
     * Returns the amount the player has stored in bank.
     * @return the bank amount
     */
    public int getBank(){
    	return bank;
    }
    /**
     * Transfers the specified amount from cash into bank.
     * @param amount the amount to transfer
     */
    public void bankTransfer(int amount){
    	bank += amount;
    	cash -= amount;
    }
    /**
     * Returns the total amount of stack and bank for the player.
     * @return cash plus bank
     */
    public int getNetCash(){
        return cash + bank;
    }
    
    /* Formatted string representations */
    /**
     * Returns the player's nick formatted in IRC bold.
     * @return the bold-formatted nick
     */
    public String getNickStr(){
    	return Colors.BOLD+getNick()+Colors.BOLD;
    }
    @Override
    public String toString(){
    	return getNick();
    }
}