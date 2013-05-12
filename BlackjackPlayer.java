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

import java.util.*;

public class BlackjackPlayer extends Player{
	private int currentIndex;
	private ArrayList<BlackjackHand> hands;
	private int initialBet, insureBet;
	private boolean surrender;
	
	/**
	 * Class constructor for subclass of Player
	 * 
	 * @param u		IRC user object
	 * @param d		Whether or not player is dealer
	 */
    public BlackjackPlayer(String nick, String hostmask, boolean dealer){
        super(nick, hostmask, dealer);
        hands = new ArrayList<BlackjackHand>();
        currentIndex = 0;
        initialBet = 0;
        surrender = false;
		insureBet = 0;
    }
    
    /* Blackjack-specific card/hand manipulation methods */
    public void addHand(){
    	hands.add(new BlackjackHand());
    }
    public BlackjackHand getHand(int num){
    	return hands.get(num);
    }
    public int getCurrentIndex(){
    	return currentIndex;
    }
    public void resetCurrentIndex(){
    	currentIndex = 0;
    }
    public BlackjackHand getCurrentHand(){
    	return hands.get(currentIndex);
    }
    public BlackjackHand getNextHand(){
    	return getHand(++currentIndex);
    }
    public int getNumberHands(){
    	return hands.size();
    }
    public boolean hasHands(){
        return hands.size() > 0;
    }
    public void resetHands(){
    	hands.clear();
    }
    
    /* Betting and gameplay methods */
    public void setInitialBet(int b){
    	initialBet = b;
    }
    public int getInitialBet(){
    	return initialBet;
    }
    public boolean hasInitialBet(){
    	return initialBet > 0;
    }
    public void clearInitialBet(){
    	initialBet = 0;
    }
    public boolean hasInsured(){
    	return (insureBet > 0);
    }
    public void setInsureBet(int amount){
    	insureBet = amount;
    }
    public int getInsureBet(){
    	return insureBet;
    }
	public void setSurrender(boolean b){
		surrender = b;
	}
	public boolean hasSurrendered(){
		return surrender;
	}
    
    /* Methods related to splitting hands */
    public boolean hasSplit(){
    	return hands.size() > 1;
    }
    public void splitHand(){
    	BlackjackHand tHand = new BlackjackHand();
    	BlackjackHand cHand = getCurrentHand();
    	tHand.add(cHand.get(1));
    	cHand.remove(1);
    	
    	hands.add(currentIndex+1, tHand);
    }
    
}
