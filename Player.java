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
    protected User user;
    protected boolean simple, dealer;
    protected int cash, bet, debt, bankrupts, rounds;
    protected boolean idledOut;
    
    /**
     * Class constructor
     * 
     * @param u		IRC user object
     * @param d		Whether or not this player is dealer
     */
    public Player(User u, boolean d){
        user = u;
        dealer = d;
        cash = 0;
        bet = 0;
        debt = 0;
        bankrupts = 0;
        rounds = 0;
        simple = true;
        idledOut = false;
    }
    
    /* Player info methods */
    public String getNick(){
        if (isDealer()){
            return "Dealer";
        } else {
            return user.getNick();
        }
    }
    public String getHostMask(){
        return user.getHostmask();
    }
    public User getUser(){
        return user;
    }
    public void setDealer(boolean b){
    	dealer = b;
    }
    public boolean isDealer(){
        return dealer;
    }
    public void setIdledOut(boolean b){
    	idledOut = b;
    }
    public boolean getIdledOut(){
    	return idledOut;
    }
    public void setBankrupts(int c){
    	bankrupts = c;
    }
    public int getBankrupts(){
    	return bankrupts;
    }
    public void incrementBankrupts(){
    	bankrupts++;
    }
    
    /* Stats keeping */
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
    public void setCash(int amount){
        cash = amount;
    }
    public void addCash(int amount){
        cash += amount;
    }
    public int getCash(){
        return cash;
    }
    public void setDebt(int amount){
    	debt = amount;
    }
    public void addDebt(int amount){
    	debt += amount;
    }
    public int getDebt(){
    	return debt;
    }
    public void payDebt(int amount){
    	debt -= amount;
    	cash -= amount;
    }
    
    /* Formatted string representations */
    public String getNickStr(){
    	return Colors.BOLD+getNick()+Colors.NORMAL;
    }
}